package network.tserver.tnexus.manager;

import com.onarandombox.MultiverseCore.api.MVWorldManager;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.config.ConfigManager;
import network.tserver.tnexus.database.repository.ResourceWorldResetRepository;

/**
 * Manages resource world configuration, reset state, and Multiverse operations.
 */
public final class ResourceWorldManager {

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
        this.settings = Objects.requireNonNull(plugin, "plugin").getConfigManager().getResourceWorldSettings();
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

    LocalDateTime calculateNextResetTime(
            ConfigManager.ResourceWorldDefinition worldDefinition,
            LocalDateTime currentTime) {
        LocalDateTime nextResetTime = worldDefinition.resetStartDate();
        while (!nextResetTime.isAfter(currentTime)) {
            nextResetTime = nextResetTime.plusDays(worldDefinition.resetIntervalDays());
        }
        return nextResetTime;
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
