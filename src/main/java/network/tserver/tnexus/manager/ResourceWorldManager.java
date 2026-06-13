package network.tserver.tnexus.manager;

import com.onarandombox.MultiverseCore.api.MVWorldManager;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.config.ConfigManager;
import network.tserver.tnexus.database.repository.ResourceWorldResetRepository;
import org.bukkit.Bukkit;

/**
 * Manages resource world configuration, reset state, and Multiverse operations.
 */
public final class ResourceWorldManager {

    private final TNexus plugin;
    private final ConfigManager.ResourceWorldSettings settings;
    private final ResourceWorldResetRepository repository;
    private final MVWorldManager worldManager;
    private final Clock clock;
    private final Map<String, ConfigManager.ResourceWorldDefinition> worldDefinitions;
    private final Set<String> resettingWorlds;

    /**
     * Creates a new resource world manager.
     *
     * @param plugin owner plugin
     * @param repository reset schedule repository
     * @param worldManager Multiverse world manager
     */
    public ResourceWorldManager(
            TNexus plugin,
            ResourceWorldResetRepository repository,
            MVWorldManager worldManager) {
        this(plugin, repository, worldManager, Clock.systemDefaultZone());
    }

    /**
     * Creates a new resource world manager with an explicit clock.
     *
     * @param plugin owner plugin
     * @param repository reset schedule repository
     * @param worldManager Multiverse world manager
     * @param clock clock used for schedule calculations
     */
    public ResourceWorldManager(
            TNexus plugin,
            ResourceWorldResetRepository repository,
            MVWorldManager worldManager,
            Clock clock) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.settings = this.plugin.getConfigManager().getResourceWorldSettings();
        this.repository = Objects.requireNonNull(repository, "repository");
        this.worldManager = Objects.requireNonNull(worldManager, "worldManager");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.worldDefinitions = new ConcurrentHashMap<>();
        this.resettingWorlds = ConcurrentHashMap.newKeySet();
    }

    /**
     * Loads configured worlds and persists their next reset schedules.
     *
     * @return completion future
     */
    public CompletableFuture<Void> onEnable() {
        this.worldDefinitions.clear();
        CompletableFuture<?>[] futures = this.settings.worlds().stream()
                .map(worldDefinition -> {
                    this.worldDefinitions.put(worldDefinition.name(), worldDefinition);
                    LocalDateTime nextResetTime = calculateNextResetTime(worldDefinition, LocalDateTime.now(this.clock));
                    return this.repository.upsertScheduledReset(
                            worldDefinition.name(),
                            nextResetTime,
                            obfuscateSeed(worldDefinition));
                })
                .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(futures);
    }

    /**
     * Returns whether the given world is currently resetting.
     *
     * @param worldName world name
     * @return {@code true} when a reset is in progress
     */
    public boolean isResetting(String worldName) {
        return this.resettingWorlds.contains(worldName);
    }

    /**
     * Marks a world as actively resetting.
     *
     * @param worldName world name
     */
    public void markResetting(String worldName) {
        this.resettingWorlds.add(worldName);
    }

    /**
     * Clears the in-progress reset marker for a world.
     *
     * @param worldName world name
     */
    public void clearResetting(String worldName) {
        this.resettingWorlds.remove(worldName);
    }

    /**
     * Returns the next reset time for the given world from the database.
     *
     * @param worldName world name
     * @return future containing the next reset time when present
     */
    public CompletableFuture<Optional<LocalDateTime>> getNextResetTime(String worldName) {
        return this.repository.findNextResetTime(worldName);
    }

    /**
     * Executes a resource-world reset and persists the next schedule.
     *
     * @param worldName world name
     * @return completion future with the next scheduled reset time
     */
    public CompletableFuture<LocalDateTime> executeReset(String worldName) {
        Objects.requireNonNull(worldName, "worldName");
        ConfigManager.ResourceWorldDefinition worldDefinition = this.worldDefinitions.get(worldName);
        if (worldDefinition == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Unknown resource world: " + worldName));
        }
        if (!this.resettingWorlds.add(worldName)) {
            return CompletableFuture.failedFuture(new IllegalStateException("Reset already in progress for " + worldName));
        }

        return this.repository.findNextResetTime(worldName)
                .thenCompose(nextReset -> {
                    LocalDateTime scheduledResetTime = nextReset
                            .orElseGet(() -> calculateNextResetTime(worldDefinition, LocalDateTime.now(this.clock)));
                    return this.repository.findByWorldNameAndNextResetAt(worldName, scheduledResetTime)
                            .thenCompose(currentEntry -> markResetInProgress(worldName, currentEntry)
                                    .thenCompose(ignored -> executeResetOnMainThread(
                                            worldName,
                                            worldDefinition,
                                            currentEntry,
                                            scheduledResetTime)));
                });
    }

    /**
     * Unloads a Multiverse-managed world.
     *
     * @param worldName world name
     * @return {@code true} when unload succeeded
     */
    public boolean unloadWorld(String worldName) {
        return this.worldManager.unloadWorld(worldName);
    }

    /**
     * Regenerates a Multiverse-managed world.
     *
     * @param worldName world name
     * @param seed world seed string
     * @return {@code true} when regeneration succeeded
     */
    public boolean regenerateWorld(String worldName, String seed) {
        return this.worldManager.regenWorld(worldName, true, true, seed);
    }

    /**
     * Loads a Multiverse-managed world.
     *
     * @param worldName world name
     * @return {@code true} when load succeeded
     */
    public boolean loadWorld(String worldName) {
        return this.worldManager.loadWorld(worldName);
    }

    /**
     * Returns the loaded resource world settings.
     *
     * @return resource world settings
     */
    public ConfigManager.ResourceWorldSettings getSettings() {
        return this.settings;
    }

    /**
     * Returns the configured definition for a resource world.
     *
     * @param worldName world name
     * @return world definition when configured
     */
    public Optional<ConfigManager.ResourceWorldDefinition> getWorldDefinition(String worldName) {
        return Optional.ofNullable(this.worldDefinitions.get(worldName));
    }

    private void performReset(ConfigManager.ResourceWorldDefinition worldDefinition) {
        String worldName = worldDefinition.name();
        String seed = String.valueOf(obfuscateSeed(worldDefinition));
        if (!unloadWorld(worldName)) {
            throw new IllegalStateException("Failed to unload world " + worldName);
        }
        if (!regenerateWorld(worldName, seed)) {
            throw new IllegalStateException("Failed to regenerate world " + worldName);
        }
        if (!loadWorld(worldName)) {
            throw new IllegalStateException("Failed to load world " + worldName);
        }
    }

    LocalDateTime calculateNextResetTime(
            ConfigManager.ResourceWorldDefinition worldDefinition,
            LocalDateTime currentTime) {
        LocalDateTime nextResetTime = worldDefinition.resetStartDate();
        while (!nextResetTime.isAfter(currentTime)) {
            nextResetTime = nextResetTime.plusDays(worldDefinition.resetIntervalDays());
        }
        return nextResetTime;
    }

    String formatCountdown(long offsetSeconds) {
        Duration duration = Duration.ofSeconds(offsetSeconds);
        if (duration.toHours() >= 1 && duration.toHoursPart() == 0 && duration.toMinutesPart() == 0 && duration.toSecondsPart() == 0) {
            return duration.toHours() + "時間";
        }
        if (duration.toMinutes() >= 1 && duration.toSecondsPart() == 0) {
            return duration.toMinutes() + "分";
        }
        return offsetSeconds + "秒";
    }

    private CompletableFuture<Void> markResetInProgress(
            String worldName,
            Optional<ResourceWorldResetRepository.ResourceWorldResetEntry> currentEntry) {
        return runOnMainThread(() -> broadcast("resource-world.reset-start", worldName))
                .thenCompose(ignored -> currentEntry
                        .map(entry -> this.repository.updateStatus(
                                        entry.id(),
                                        ResourceWorldResetRepository.ResetStatus.IN_PROGRESS,
                                        null)
                                .thenAccept(updated -> {
                                    if (!updated) {
                                        throw new IllegalStateException("Failed to mark reset as in progress for " + worldName);
                                    }
                                }))
                        .orElseGet(() -> CompletableFuture.completedFuture(null)));
    }

    private CompletableFuture<LocalDateTime> executeResetOnMainThread(
            String worldName,
            ConfigManager.ResourceWorldDefinition worldDefinition,
            Optional<ResourceWorldResetRepository.ResourceWorldResetEntry> currentEntry,
            LocalDateTime scheduledResetTime) {
        CompletableFuture<LocalDateTime> result = new CompletableFuture<>();
        Bukkit.getScheduler().runTask(this.plugin, () -> {
            try {
                performReset(worldDefinition);
            } catch (RuntimeException exception) {
                handleResetFailure(worldName, currentEntry, exception).whenComplete((failedResult, throwable) -> {
                    if (throwable != null) {
                        result.completeExceptionally(throwable);
                        return;
                    }
                    result.complete(failedResult);
                });
                return;
            }

            LocalDateTime nextResetTime = scheduledResetTime.plusDays(worldDefinition.resetIntervalDays());
            Long seed = obfuscateSeed(worldDefinition);
            CompletableFuture<Void> persistenceFuture = currentEntry
                    .map(entry -> this.repository.updateStatus(
                                    entry.id(),
                                    ResourceWorldResetRepository.ResetStatus.COMPLETED,
                                    null)
                            .thenAccept(updated -> {
                                if (!updated) {
                                    throw new IllegalStateException("Failed to mark reset as completed for " + worldName);
                                }
                            }))
                    .orElseGet(() -> CompletableFuture.completedFuture(null));

            persistenceFuture
                    .thenCompose(ignored -> this.repository.insert(
                            ResourceWorldResetRepository.ResourceWorldResetRecord.scheduled(
                                    worldName,
                                    scheduledResetTime,
                                    nextResetTime,
                                    seed)))
                    .thenApply(ignored -> nextResetTime)
                    .whenComplete((nextScheduledReset, throwable) -> Bukkit.getScheduler().runTask(this.plugin, () -> {
                        this.resettingWorlds.remove(worldName);
                        if (throwable == null) {
                            broadcast("resource-world.reset-complete", worldName);
                            result.complete(nextScheduledReset);
                            return;
                        }
                        this.plugin.getLogger().log(
                                Level.SEVERE,
                                "Failed to persist resource world reset state for " + worldName,
                                throwable);
                        broadcast("resource-world.reset-failed", worldName);
                        result.completeExceptionally(throwable);
                    }));
        });
        return result;
    }

    private CompletableFuture<LocalDateTime> handleResetFailure(
            String worldName,
            Optional<ResourceWorldResetRepository.ResourceWorldResetEntry> currentEntry,
            RuntimeException exception) {
        CompletableFuture<Void> persistenceFuture = currentEntry
                .map(entry -> this.repository.updateStatus(
                                entry.id(),
                                ResourceWorldResetRepository.ResetStatus.FAILED,
                                exception.getMessage())
                        .thenAccept(ignored -> {
                        }))
                .orElseGet(() -> CompletableFuture.completedFuture(null));
        CompletableFuture<LocalDateTime> failure = new CompletableFuture<>();
        persistenceFuture.whenComplete((ignored, persistenceThrowable) -> Bukkit.getScheduler().runTask(this.plugin, () -> {
            this.resettingWorlds.remove(worldName);
            this.plugin.getLogger().log(Level.SEVERE, "Failed to reset resource world " + worldName, exception);
            if (persistenceThrowable != null) {
                this.plugin.getLogger().log(
                        Level.SEVERE,
                        "Failed to persist resource world reset failure for " + worldName,
                        persistenceThrowable);
            }
            broadcast("resource-world.reset-failed", worldName);
            failure.completeExceptionally(new IllegalStateException("Failed to reset resource world " + worldName, exception));
        }));
        return failure;
    }

    private void broadcast(String key, String worldName) {
        String message = this.plugin.getMessageConfig().getMessage("prefix")
                + this.plugin.getMessageConfig().getMessage(key, worldName);
        Bukkit.broadcastMessage(message);
    }

    private CompletableFuture<Void> runOnMainThread(Runnable runnable) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTask(this.plugin, () -> {
            try {
                runnable.run();
                future.complete(null);
            } catch (RuntimeException exception) {
                future.completeExceptionally(exception);
            }
        });
        return future;
    }

    private Long obfuscateSeed(ConfigManager.ResourceWorldDefinition worldDefinition) {
        long baseSeed = worldDefinition.name().getBytes(StandardCharsets.UTF_8).length;
        long mixedSeed = baseSeed;
        for (byte value : worldDefinition.name().getBytes(StandardCharsets.UTF_8)) {
            mixedSeed = (mixedSeed * 31) + value;
        }
        return mixedSeed ^ this.settings.seedObfuscationKey();
    }
}
