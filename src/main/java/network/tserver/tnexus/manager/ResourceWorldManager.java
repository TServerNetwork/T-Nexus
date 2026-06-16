package network.tserver.tnexus.manager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;
import java.util.logging.Level;
import net.kyori.adventure.util.TriState;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.config.ConfigManager;
import network.tserver.tnexus.database.repository.ResourceWorldResetRepository;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * Manages resource world configuration, reset state, and Multiverse operations.
 */
public final class ResourceWorldManager {

    private static final int FALLBACK_CYLINDER_RADIUS = 8;
    private static final int WATER_SURFACE_FALLBACK_RADIUS = 24;
    private static final int WORLD_LOAD_WAIT_TICKS = 200;
    private static final String ADMIN_PERMISSION = "tnexus.admin";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");

    private final TNexus plugin;
    private final ConfigManager.ResourceWorldSettings settings;
    private final ResourceWorldResetRepository repository;
    private final MultiverseWorldService worldManager;
    private final Clock clock;
    private final ResourceWorldFileManager fileManager;
    private final ResourceWorldEditService worldEditService;
    private final LongSupplier randomSeedSupplier;
    private final Map<String, ConfigManager.ResourceWorldDefinition> worldDefinitions;
    private final Set<String> resettingWorlds;
    private final Map<String, Long> activeResetEntryIds;

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
            MultiverseWorldService worldManager) {
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
            MultiverseWorldService worldManager,
            Clock clock) {
        this(
                plugin,
                repository,
                worldManager,
                clock,
                new ResourceWorldFileManager(plugin),
                new FaweResourceWorldEditService(plugin),
                new SecureRandom()::nextLong);
    }

    ResourceWorldManager(
            TNexus plugin,
            ResourceWorldResetRepository repository,
            MultiverseWorldService worldManager,
            Clock clock,
            ResourceWorldFileManager fileManager,
            ResourceWorldEditService worldEditService,
            LongSupplier randomSeedSupplier) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.settings = this.plugin.getConfigManager().getResourceWorldSettings();
        this.repository = Objects.requireNonNull(repository, "repository");
        this.worldManager = Objects.requireNonNull(worldManager, "worldManager");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.fileManager = Objects.requireNonNull(fileManager, "fileManager");
        this.worldEditService = Objects.requireNonNull(worldEditService, "worldEditService");
        this.randomSeedSupplier = Objects.requireNonNull(randomSeedSupplier, "randomSeedSupplier");
        this.worldDefinitions = new ConcurrentHashMap<>();
        this.resettingWorlds = ConcurrentHashMap.newKeySet();
        this.activeResetEntryIds = new ConcurrentHashMap<>();
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
                            null);
                })
                .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(futures);
    }

    /**
     * Persists in-progress resets synchronously during plugin shutdown.
     */
    public void onDisable() {
        for (Map.Entry<String, Long> entry : this.activeResetEntryIds.entrySet()) {
            try {
                this.repository
                        .updateStatus(
                                entry.getValue(),
                                ResourceWorldResetRepository.ResetStatus.FAILED,
                                "Plugin disabled during reset")
                        .join();
            } catch (RuntimeException exception) {
                this.plugin.getLogger().log(
                        Level.SEVERE,
                        "Failed to persist shutdown status for resource world " + entry.getKey(),
                        exception);
            }
        }
        this.activeResetEntryIds.clear();
        this.resettingWorlds.clear();
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
        this.activeResetEntryIds.remove(worldName);
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
     * Returns the latest persisted reset entry for the given world.
     *
     * @param worldName world name
     * @return future containing the latest entry when present
     */
    public CompletableFuture<Optional<ResourceWorldResetRepository.ResourceWorldResetEntry>> getLatestResetEntry(
            String worldName) {
        return this.repository.findLatestEntry(worldName);
    }

    /**
     * Returns the most recent completed reset time for the given world.
     *
     * @param worldName world name
     * @return future containing the latest completed reset timestamp when present
     */
    public CompletableFuture<Optional<LocalDateTime>> getLastCompletedResetTime(String worldName) {
        return this.repository.findLatestCompletedEntry(worldName)
                .thenApply(entry -> entry.map(ResourceWorldResetRepository.ResourceWorldResetEntry::resetAt));
    }

    /**
     * Executes a resource-world reset and persists the next schedule.
     *
     * @param worldName world name
     * @return completion future with the next scheduled reset time
     */
    public CompletableFuture<LocalDateTime> executeReset(String worldName) {
        return executeReset(worldName, ResetTrigger.MANUAL);
    }

    /**
     * Executes a scheduled resource-world reset and persists the next schedule.
     *
     * @param worldName world name
     * @return completion future with the next scheduled reset time
     */
    public CompletableFuture<LocalDateTime> executeScheduledReset(String worldName) {
        return executeReset(worldName, ResetTrigger.SCHEDULED);
    }

    private CompletableFuture<LocalDateTime> executeReset(String worldName, ResetTrigger resetTrigger) {
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
                    LocalDateTime nextScheduledReset = scheduledResetTime.plusDays(worldDefinition.resetIntervalDays());
                    return this.repository.findByWorldNameAndNextResetAt(worldName, scheduledResetTime)
                            .thenCompose(currentEntry -> markResetInProgress(
                                    worldName,
                                    scheduledResetTime,
                                    nextScheduledReset,
                                    resetTrigger,
                                    currentEntry))
                            .thenCompose(context -> executeResetFlow(context, worldDefinition))
                            .handle((result, throwable) -> handleResetResult(worldName, result, throwable))
                            .thenCompose(future -> future);
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
     * Removes a Multiverse-managed world registration.
     *
     * @param worldName world name
     * @return {@code true} when removal succeeded
     */
    public boolean removeWorld(String worldName) {
        return this.worldManager.removeWorld(worldName);
    }

    /**
     * Imports a Bukkit-loaded world into Multiverse management.
     *
     * @param worldName world name
     * @param environment world environment
     * @return {@code true} when import succeeded
     */
    public boolean importWorld(String worldName, World.Environment environment) {
        return this.worldManager.importWorld(worldName, environment);
    }

    /**
     * Regenerates a Multiverse-managed world.
     *
     * @param worldName world name
     * @param seed world seed string
     * @return {@code true} when regeneration succeeded
     */
    public boolean regenerateWorld(String worldName, String seed) {
        return this.worldManager.regenerateWorld(worldName, seed);
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

    /**
     * Returns whether the given world is managed as a resource world.
     *
     * @param worldName world name
     * @return {@code true} when configured as a resource world
     */
    public boolean isResourceWorld(String worldName) {
        return this.worldDefinitions.containsKey(worldName);
    }

    /**
     * Returns the configured display name for a resource world.
     *
     * @param worldName world name
     * @return localized display name
     */
    public String getDisplayName(String worldName) {
        ConfigManager.ResourceWorldDefinition worldDefinition = this.worldDefinitions.get(worldName);
        if (worldDefinition == null) {
            return worldName;
        }
        return getDimensionDisplayName(worldDefinition.dimension());
    }

    /**
     * Returns the localized display name for a dimension.
     *
     * @param environment dimension environment
     * @return localized display name
     */
    public String getDimensionDisplayName(World.Environment environment) {
        return switch (environment) {
            case NORMAL -> "通常世界";
            case NETHER -> "ネザー";
            case THE_END -> "エンド";
            default -> environment.name();
        };
    }

    /**
     * Returns whether admins should see the real world seed.
     *
     * @return {@code true} when admin bypass is enabled
     */
    public boolean shouldShowRealSeedToAdmin() {
        return this.settings.showRealSeedToAdmin();
    }

    /**
     * Obfuscates a world seed for player-facing display.
     *
     * @param actualSeed real world seed
     * @return obfuscated seed value
     */
    public long obfuscateSeed(long actualSeed) {
        return actualSeed ^ this.settings.seedObfuscationKey();
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

    public String formatCountdown(long offsetSeconds) {
        Duration duration = Duration.ofSeconds(offsetSeconds);
        if (duration.toHours() >= 1 && duration.toHoursPart() == 0 && duration.toMinutesPart() == 0 && duration.toSecondsPart() == 0) {
            return duration.toHours() + "h";
        }
        if (duration.toMinutes() >= 1 && duration.toSecondsPart() == 0) {
            return duration.toMinutes() + "m";
        }
        return offsetSeconds + "s";
    }

    private CompletableFuture<ResetContext> markResetInProgress(
            String worldName,
            LocalDateTime scheduledResetTime,
            LocalDateTime nextScheduledReset,
            ResetTrigger resetTrigger,
            Optional<ResourceWorldResetRepository.ResourceWorldResetEntry> currentEntry) {
        return runOnMainThread(() -> broadcast("resource.notification.start.broadcast", worldName))
                .thenCompose(ignored -> currentEntry
                        .map(entry -> CompletableFuture.completedFuture(entry.id()))
                        .orElseGet(() -> this.repository.insert(
                                ResourceWorldResetRepository.ResourceWorldResetRecord.scheduled(
                                        worldName,
                                        scheduledResetTime,
                                        scheduledResetTime,
                                        null))))
                .thenCompose(entryId -> this.repository
                        .updateStatus(entryId, ResourceWorldResetRepository.ResetStatus.IN_PROGRESS, null)
                        .thenApply(updated -> {
                            if (!updated) {
                                throw new IllegalStateException("Failed to mark reset as in progress for " + worldName);
                            }
                            this.activeResetEntryIds.put(worldName, entryId);
                            return new ResetContext(worldName, entryId, scheduledResetTime, nextScheduledReset, resetTrigger);
                        }));
    }

    private CompletableFuture<LocalDateTime> executeResetFlow(
            ResetContext context,
            ConfigManager.ResourceWorldDefinition worldDefinition) {
        return runOnMainThread(() -> teleportPlayersToFallback(context.worldName()))
                .thenCompose(ignored -> runOnMainThread(() -> this.fileManager.getLoadedWorldFolder(context.worldName())))
                .thenCompose(worldFolder -> runAsync(() -> this.fileManager.backupWorld(worldFolder))
                        .thenCompose(ignored -> runOnMainThread(() -> unloadWorldForReset(context.worldName())))
                        .thenCompose(ignored -> runAsync(() -> {
                            this.fileManager.deleteWorldFolder(worldFolder);
                            return this.fileManager.randomizeStructureSeeds(context.worldName());
                        }))
                        .thenCompose(seed -> runOnMainThread(() -> recreateWorld(
                                        context.worldName(),
                                        worldDefinition,
                                        seed))
                                .thenCompose(world -> runAsync(() -> this.fileManager.getSpawnSchematicPath(context.worldName()))
                                        .thenCompose(schematicPath -> prepareSpawnArea(world, schematicPath)
                                                .thenCompose(spawnAnchor -> maybePasteSpawnSchematic(
                                                                context.worldName(),
                                                                world,
                                                                schematicPath,
                                                                spawnAnchor)
                                                        .thenCompose(pasteIgnored -> configureSpawnPoint(world, spawnAnchor)))
                                                .thenCompose(configuredAnchor -> persistResetSuccess(
                                                        context,
                                                        worldDefinition,
                                                        seed))
                                                .thenCompose(nextReset -> runOnMainThread(() -> {
                                                    clearResetting(context.worldName());
                                                    broadcast(
                                                            "resource.notification.complete.broadcast",
                                                            context.worldName(),
                                                            Map.of("next_reset", DATE_TIME_FORMATTER.format(nextReset)));
                                                }).thenApply(ignoredAgain -> nextReset))))));
    }

    private CompletableFuture<SpawnAnchor> prepareSpawnArea(World world, Path schematicPath) {
        return runOnMainThread(() -> resolveSpawnAnchor(world))
                .thenCompose(spawnAnchor -> {
                    this.plugin.getLogger().info("Preparing resource-world spawn area with anchor: world="
                            + world.getName()
                            + ", x="
                            + spawnAnchor.x()
                            + ", y="
                            + spawnAnchor.surfaceY()
                            + ", z="
                            + spawnAnchor.z());
                    return runOnMainThread(() -> this.worldEditService.prepareSpawnArea(
                            world,
                            schematicPath,
                            spawnAnchor.x(),
                            spawnAnchor.z(),
                            FALLBACK_CYLINDER_RADIUS,
                            spawnAnchor.surfaceY())).thenApply(ignored -> spawnAnchor);
                });
    }

    private CompletableFuture<LocalDateTime> handleResetResult(
            String worldName,
            LocalDateTime result,
            Throwable throwable) {
        if (throwable == null) {
            return CompletableFuture.completedFuture(result);
        }

        Long entryId = this.activeResetEntryIds.get(worldName);
        ResetContext context = new ResetContext(
                worldName,
                entryId == null ? -1L : entryId,
                LocalDateTime.now(this.clock),
                LocalDateTime.now(this.clock),
                ResetTrigger.SCHEDULED);
        return handleResetFailure(context, unwrap(throwable));
    }

    private CompletableFuture<LocalDateTime> handleResetFailure(ResetContext context, Throwable rootCause) {
        CompletableFuture<String> recoveryFuture = attemptRestore(context.worldName())
                .handle((ignored, recoveryThrowable) -> buildFailureMessage(rootCause, recoveryThrowable));

        return recoveryFuture
                .thenCompose(errorMessage -> persistResetFailure(context, errorMessage)
                        .handle((ignored, persistenceThrowable) -> {
                            if (persistenceThrowable != null) {
                                this.plugin.getLogger().log(
                                        Level.SEVERE,
                                        "Failed to persist resource world reset failure for " + context.worldName(),
                                        persistenceThrowable);
                            }
                            return errorMessage;
                        }))
                .thenCompose(errorMessage -> runOnMainThread(() -> {
                    clearResetting(context.worldName());
                    this.plugin.getLogger().log(
                            Level.SEVERE,
                            "Failed to reset resource world " + context.worldName(),
                            rootCause);
                    notifyAdmins(errorMessage, context.worldName());
                    broadcast("resource.notification.failed.broadcast", context.worldName());
                }).thenCompose(ignored -> CompletableFuture.failedFuture(
                        new IllegalStateException("Failed to reset resource world " + context.worldName(), rootCause))));
    }

    private CompletableFuture<Void> attemptRestore(String worldName) {
        ConfigManager.ResourceWorldDefinition worldDefinition = this.worldDefinitions.get(worldName);
        return runOnMainThread(() -> {
            World loadedWorld = Bukkit.getWorld(worldName);
            if (loadedWorld != null && !unloadWorld(worldName)) {
                throw new IllegalStateException("Failed to unload world before restore: " + worldName);
            }
            if (!removeWorld(worldName)) {
                throw new IllegalStateException("Failed to remove world before restore: " + worldName);
            }
        }).thenCompose(ignored -> runAsync(() -> {
            if (!this.fileManager.hasLatestBackup(worldName)) {
                throw new IllegalStateException("No backup is available for restore");
            }
            this.fileManager.restoreLatestBackup(worldName);
        })).thenCompose(ignored -> runOnMainThread(() -> {
            if (worldDefinition == null) {
                throw new IllegalStateException("Unknown resource world: " + worldName);
            }
            createAndRegisterWorld(worldName, worldDefinition, null);
        })).thenAccept(ignored -> {
        });
    }

    private CompletableFuture<LocalDateTime> persistResetSuccess(
            ResetContext context,
            ConfigManager.ResourceWorldDefinition worldDefinition,
            long seed) {
        LocalDateTime nextScheduledReset = resolveNextScheduledReset(context, worldDefinition);
        return this.repository.updateStatusAndSeed(
                        context.currentEntryId(),
                        ResourceWorldResetRepository.ResetStatus.COMPLETED,
                        seed)
                .thenAccept(updated -> {
                    if (!updated) {
                        throw new IllegalStateException("Failed to mark reset as completed for " + context.worldName());
                    }
                })
                .thenCompose(ignored -> this.repository.insert(
                        ResourceWorldResetRepository.ResourceWorldResetRecord.scheduled(
                                context.worldName(),
                                nextScheduledReset,
                                nextScheduledReset,
                                null)))
                .thenApply(ignored -> nextScheduledReset);
    }

    private LocalDateTime resolveNextScheduledReset(
            ResetContext context,
            ConfigManager.ResourceWorldDefinition worldDefinition) {
        if (context.resetTrigger() == ResetTrigger.MANUAL) {
            return LocalDateTime.now(this.clock).plusDays(worldDefinition.resetIntervalDays());
        }
        return context.nextScheduledReset();
    }

    private CompletableFuture<Void> persistResetFailure(ResetContext context, String errorMessage) {
        if (context.currentEntryId() < 0L) {
            return CompletableFuture.completedFuture(null);
        }

        return this.repository.updateStatus(
                        context.currentEntryId(),
                        ResourceWorldResetRepository.ResetStatus.FAILED,
                        errorMessage)
                .thenAccept(updated -> {
                    if (!updated) {
                        throw new IllegalStateException("Failed to mark reset as failed for " + context.worldName());
                    }
                });
    }

    private CompletableFuture<Void> maybePasteSpawnSchematic(
            String worldName,
            World world,
            Path schematicPath,
            SpawnAnchor spawnAnchor) {
        return runOnMainThread(() -> {
            if (!Files.isRegularFile(schematicPath)) {
                return;
            }
            this.plugin.getLogger().info("Pasting resource-world schematic at anchor: world="
                    + world.getName()
                    + ", x="
                    + spawnAnchor.x()
                    + ", y="
                    + spawnAnchor.surfaceY()
                    + ", z="
                    + spawnAnchor.z()
                    + ", path="
                    + schematicPath);
            this.worldEditService.pasteSchematic(
                    world,
                    schematicPath,
                    spawnAnchor.x(),
                    spawnAnchor.surfaceY(),
                    spawnAnchor.z());
        });
    }

    private CompletableFuture<Void> configureSpawnPoint(World world, SpawnAnchor spawnAnchor) {
        return runOnMainThread(() -> {
            Location spawnLocation = resolveSpawnPoint(world, spawnAnchor);
            try {
                if (!world.setSpawnLocation(
                        spawnLocation.getBlockX(),
                        spawnLocation.getBlockY(),
                        spawnLocation.getBlockZ())) {
                    throw new IllegalStateException("Failed to set resource-world spawn location for " + world.getName());
                }
            } catch (RuntimeException exception) {
                if (!"org.mockbukkit.mockbukkit.exception.UnimplementedOperationException"
                        .equals(exception.getClass().getName())) {
                    throw exception;
                }
                this.plugin.getLogger().fine("Spawn location updates are not implemented by the active test world: "
                        + world.getName());
                return;
            }
            configureSpawnRadius(world);
            this.plugin.getLogger().info("Configured resource-world spawn point: world="
                    + world.getName()
                    + ", x="
                    + spawnLocation.getX()
                    + ", y="
                    + spawnLocation.getY()
                    + ", z="
                    + spawnLocation.getZ());
        });
    }

    private void configureSpawnRadius(World world) {
        try {
            if (!world.setGameRule(GameRule.SPAWN_RADIUS, 0)) {
                this.plugin.getLogger().warning("Failed to set resource-world spawn radius to 0 for "
                        + world.getName());
            }
        } catch (RuntimeException exception) {
            if (!"org.mockbukkit.mockbukkit.exception.UnimplementedOperationException"
                    .equals(exception.getClass().getName())) {
                throw exception;
            }
            this.plugin.getLogger().fine("Spawn radius updates are not implemented by the active test world: "
                    + world.getName());
        }
    }

    private void teleportPlayersToFallback(String worldName) {
        World targetWorld = Bukkit.getWorld(worldName);
        if (targetWorld == null) {
            return;
        }

        World fallbackWorld = Bukkit.getWorld(this.settings.fallbackWorld());
        if (fallbackWorld == null) {
            throw new IllegalStateException("Fallback world is not loaded: " + this.settings.fallbackWorld());
        }

        for (Player player : List.copyOf(targetWorld.getPlayers())) {
            sendResourceMessage(
                    player,
                    "resource.notification.start.teleport",
                    Map.of());
            player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(
                    this.plugin.getMessageConfig().getMessage("resource.notification.start.teleport_actionbar")));
            player.teleport(fallbackWorld.getSpawnLocation());
        }
    }

    private void regenerateWorld(String worldName, long seed) {
        if (!regenerateWorld(worldName, String.valueOf(seed))) {
            throw new IllegalStateException("Failed to regenerate world " + worldName);
        }
    }

    private void unloadWorldForReset(String worldName) {
        World loadedWorld = Bukkit.getWorld(worldName);
        if (loadedWorld != null && !unloadWorld(worldName)) {
            throw new IllegalStateException("Failed to unload world before reset: " + worldName);
        }
    }

    private World recreateWorld(
            String worldName,
            ConfigManager.ResourceWorldDefinition worldDefinition,
            long seed) {
        return createAndRegisterWorld(worldName, worldDefinition, seed);
    }

    private World createAndRegisterWorld(
            String worldName,
            ConfigManager.ResourceWorldDefinition worldDefinition,
            Long seed) {
        if (!removeWorld(worldName)) {
            throw new IllegalStateException("Failed to remove world from Multiverse: " + worldName);
        }

        World world = createBukkitWorld(worldName, worldDefinition, seed);
        if (!importWorld(worldName, worldDefinition.dimension())) {
            throw new IllegalStateException("Failed to import world into Multiverse: " + worldName);
        }
        return world;
    }

    private World createBukkitWorld(
            String worldName,
            ConfigManager.ResourceWorldDefinition worldDefinition,
            Long seed) {
        WorldCreator creator = new WorldCreator(worldName)
                .environment(worldDefinition.dimension())
                .generateStructures(true)
                .keepSpawnLoaded(TriState.FALSE);
        if (seed != null) {
            creator.seed(seed);
        }
        World world = creator.createWorld();
        if (world == null) {
            throw new IllegalStateException("Failed to create Bukkit world " + worldName);
        }
        return world;
    }

    int determineFlattenSurface(World world) {
        int min = world.getMinHeight() + 1;
        int max = world.getMaxHeight() - 2;
        return FaweResourceWorldEditService.sampleTerrainSurfaceY(world, 0, 0, min, max, true);
    }

    SpawnAnchor resolveSpawnAnchor(World world) {
        int min = world.getMinHeight() + 1;
        int max = world.getMaxHeight() - 2;
        Location vanillaSpawn = world.getSpawnLocation();
        int anchorX = vanillaSpawn.getBlockX();
        int anchorZ = vanillaSpawn.getBlockZ();
        int anchorSurfaceY = FaweResourceWorldEditService.sampleTerrainSurfaceY(world, anchorX, anchorZ, min, max, true);
        Material anchorMaterial = world.getBlockAt(anchorX, anchorSurfaceY, anchorZ).getType();
        if (!FaweResourceWorldEditService.isLiquidSurfaceMaterial(anchorMaterial)) {
            this.plugin.getLogger().info("Resolved resource-world spawn anchor from vanilla spawn: "
                    + "spawnX=" + anchorX
                    + ", spawnZ=" + anchorZ
                    + ", surfaceY=" + anchorSurfaceY
                    + ", world=" + world.getName());
            return new SpawnAnchor(anchorX, anchorZ, anchorSurfaceY);
        }

        for (int radius = 1; radius <= WATER_SURFACE_FALLBACK_RADIUS; radius++) {
            for (FaweResourceWorldEditService.ColumnKey column : FaweResourceWorldEditService.perimeterColumns(radius)) {
                int candidateX = anchorX + column.x();
                int candidateZ = anchorZ + column.z();
                int topY = Math.max(min, Math.min(world.getHighestBlockYAt(candidateX, candidateZ), max));
                Material topMaterial = world.getBlockAt(candidateX, topY, candidateZ).getType();
                if (FaweResourceWorldEditService.isLiquidSurfaceMaterial(topMaterial)) {
                    continue;
                }
                int surfaceY = FaweResourceWorldEditService.sampleTerrainSurfaceY(
                        world,
                        candidateX,
                        candidateZ,
                        min,
                        max,
                        false);
                Material surfaceMaterial = world.getBlockAt(candidateX, surfaceY, candidateZ).getType();
                if (!FaweResourceWorldEditService.isTerrainSurfaceMaterial(surfaceMaterial)) {
                    continue;
                }
                if (surfaceY < anchorSurfaceY - 2) {
                    continue;
                }
                this.plugin.getLogger().info("Resolved resource-world spawn anchor from nearby land because vanilla spawn was liquid: "
                        + "spawnX=" + anchorX
                        + ", spawnZ=" + anchorZ
                        + ", spawnY=" + anchorSurfaceY
                        + ", anchorX=" + candidateX
                        + ", anchorZ=" + candidateZ
                        + ", landY=" + surfaceY
                        + ", world=" + world.getName());
                return new SpawnAnchor(candidateX, candidateZ, surfaceY);
            }
        }

        this.plugin.getLogger().info("Using liquid vanilla spawn as resource-world spawn anchor because no nearby land was found: "
                + "spawnX=" + anchorX
                + ", spawnZ=" + anchorZ
                + ", surfaceY=" + anchorSurfaceY
                + ", world=" + world.getName());
        return new SpawnAnchor(anchorX, anchorZ, anchorSurfaceY);
    }

    Location resolveSpawnPoint(World world, SpawnAnchor spawnAnchor) {
        int minY = world.getMinHeight() + 1;
        int maxY = world.getMaxHeight() - 2;
        int startY = clamp(spawnAnchor.surfaceY() + 1, minY + 1, maxY);
        for (int y = startY; y <= maxY; y++) {
            Block feetBlock = world.getBlockAt(spawnAnchor.x(), y, spawnAnchor.z());
            Block headBlock = world.getBlockAt(spawnAnchor.x(), y + 1, spawnAnchor.z());
            Block floorBlock = world.getBlockAt(spawnAnchor.x(), y - 1, spawnAnchor.z());
            if (!isSpawnPassable(feetBlock) || !isSpawnPassable(headBlock)) {
                continue;
            }
            if (isSpawnPassable(floorBlock) || FaweResourceWorldEditService.isLiquidSurfaceMaterial(floorBlock.getType())) {
                continue;
            }
            return new Location(world, spawnAnchor.x() + 0.5D, y, spawnAnchor.z() + 0.5D);
        }
        int fallbackY = clamp(world.getHighestBlockYAt(spawnAnchor.x(), spawnAnchor.z()) + 1, minY + 1, maxY);
        return new Location(world, spawnAnchor.x() + 0.5D, fallbackY, spawnAnchor.z() + 0.5D);
    }

    private boolean isSpawnPassable(Block block) {
        Material material = block.getType();
        return material.isAir()
                || (!material.isSolid() && !FaweResourceWorldEditService.isLiquidSurfaceMaterial(material));
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    record SpawnAnchor(int x, int z, int surfaceY) {
    }

    private CompletableFuture<World> waitForWorldLoad(String worldName) {
        CompletableFuture<World> future = new CompletableFuture<>();
        AtomicInteger attempts = new AtomicInteger();
        waitForWorldLoad(worldName, attempts, future);
        return future;
    }

    private void waitForWorldLoad(String worldName, AtomicInteger attempts, CompletableFuture<World> future) {
        if (future.isDone()) {
            return;
        }

        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            future.complete(world);
            return;
        }

        if (attempts.incrementAndGet() >= WORLD_LOAD_WAIT_TICKS) {
            future.completeExceptionally(new IllegalStateException("Timed out waiting for world to load: " + worldName));
            return;
        }

        Bukkit.getScheduler().runTaskLater(this.plugin, () -> waitForWorldLoad(worldName, attempts, future), 1L);
    }

    private void notifyAdmins(String errorMessage, String worldName) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission(ADMIN_PERMISSION)) {
                sendResourceMessage(
                        player,
                        "resource.notification.failed.admin",
                        Map.of(
                                "display_name", getDisplayName(worldName),
                                "world", worldName,
                                "error_reason", errorMessage));
            }
        }
    }

    private String buildFailureMessage(Throwable rootCause, Throwable recoveryThrowable) {
        StringBuilder builder = new StringBuilder(sanitizeMessage(rootCause));
        if (recoveryThrowable != null) {
            builder.append(" | restore=").append(sanitizeMessage(unwrap(recoveryThrowable)));
        }
        return builder.toString();
    }

    private String sanitizeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable.getClass().getSimpleName();
        }
        return message;
    }

    private void broadcast(String key, String worldName) {
        broadcast(key, worldName, Map.of());
    }

    private void broadcast(String key, String worldName, Map<String, ?> extraPlaceholders) {
        String message = this.plugin.getMessageConfig().getMessage("resource.prefix")
                + this.plugin.getMessageConfig().getMessage(key, createWorldPlaceholders(worldName, extraPlaceholders));
        Bukkit.broadcastMessage(message);
    }

    private void sendResourceMessage(Player player, String key, Map<String, ?> placeholders) {
        player.sendMessage(this.plugin.getMessageConfig().getMessage("resource.prefix")
                + this.plugin.getMessageConfig().getMessage(key, placeholders));
    }

    private Map<String, Object> createWorldPlaceholders(String worldName, Map<String, ?> extraPlaceholders) {
        Map<String, Object> placeholders = new HashMap<>();
        placeholders.put("display_name", getDisplayName(worldName));
        placeholders.put("world", worldName);
        placeholders.putAll(extraPlaceholders);
        return placeholders;
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

    private <T> CompletableFuture<T> runOnMainThread(java.util.function.Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTask(this.plugin, () -> {
            try {
                future.complete(supplier.get());
            } catch (RuntimeException exception) {
                future.completeExceptionally(exception);
            }
        });
        return future;
    }

    private CompletableFuture<Void> runAsync(Runnable runnable) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
            try {
                runnable.run();
                future.complete(null);
            } catch (RuntimeException exception) {
                future.completeExceptionally(exception);
            }
        });
        return future;
    }

    private <T> CompletableFuture<T> runAsync(java.util.function.Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
            try {
                future.complete(supplier.get());
            } catch (RuntimeException exception) {
                future.completeExceptionally(exception);
            }
        });
        return future;
    }

    private Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null
                && (current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException)) {
            current = current.getCause();
        }
        return current;
    }

    private record ResetContext(
            String worldName,
            long currentEntryId,
            LocalDateTime scheduledResetTime,
            LocalDateTime nextScheduledReset,
            ResetTrigger resetTrigger) {
    }

    private enum ResetTrigger {
        MANUAL,
        SCHEDULED
    }
}
