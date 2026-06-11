package network.tserver.tnexus.manager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.database.repository.BlockStatsDelta;
import network.tserver.tnexus.database.repository.EntityDamageDelta;
import network.tserver.tnexus.database.repository.ItemStatsDelta;
import network.tserver.tnexus.database.repository.PlayerStatsRepository;
import network.tserver.tnexus.util.BlockPosition;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * Tracks in-memory player sessions and persists aggregate play time.
 */
public class PlayerStatsManager {

    static final long DEFAULT_DISTANCE_FLUSH_INTERVAL_TICKS = 100L;
    static final double MAX_TRACKED_DISTANCE_PER_EVENT = 32.0D;
    static final Duration WORLD_EDIT_SUPPRESSION_WINDOW = Duration.ofSeconds(1L);
    static final Duration PROCESSING_ATTRIBUTION_WINDOW = Duration.ofMinutes(10L);

    private final TNexus plugin;
    private final PlayerStatsRepository playerStatsRepository;
    private final Clock clock;
    private final Map<UUID, Instant> sessionStartTimes;
    private final Object distanceLock;
    private final Map<UUID, Double> pendingTotalDistances;
    private final Map<UUID, EnumMap<TravelType, Double>> pendingTravelDistances;
    private final Object blockLock;
    private final Map<UUID, Integer> pendingBlocksPlaced;
    private final Map<UUID, Integer> pendingBlocksBroken;
    private final Map<UUID, Map<String, BlockStatsDelta>> pendingBlockStats;
    private final Object entityDamageLock;
    private final Map<UUID, Map<String, EntityDamageDelta>> pendingEntityDamageStats;
    private final Object killLock;
    private final Map<UUID, Map<String, Integer>> pendingKillStats;
    private final Object craftLock;
    private final Map<UUID, Map<String, Integer>> pendingCraftStats;
    private final Object processingLock;
    private final Map<UUID, Integer> pendingBrewCounts;
    private final Map<UUID, Map<String, Integer>> pendingSmeltStats;
    private final Map<UUID, Map<String, Integer>> pendingEnchantStats;
    private final Map<UUID, Map<String, Integer>> pendingEnchantItemStats;
    private final Object farmingLock;
    private final Map<UUID, Map<String, Integer>> pendingHarvestStats;
    private final Map<UUID, Map<String, Integer>> pendingBreedStats;
    private final Map<UUID, Map<String, Integer>> pendingFishStats;
    private final Object itemLock;
    private final Map<UUID, Map<String, ItemStatsDelta>> pendingItemStats;
    private final Map<BlockPosition, ProcessingAttribution> processingStationAttributions;
    private final Map<UUID, Instant> worldEditSuppressionWindows;
    private final BukkitTask statsFlushTask;

    /**
     * Creates a new player stats manager.
     *
     * @param plugin plugin instance
     * @param playerStatsRepository player stats repository
     */
    public PlayerStatsManager(TNexus plugin, PlayerStatsRepository playerStatsRepository) {
        this(
                plugin,
                playerStatsRepository,
                Clock.systemUTC(),
                new ConcurrentHashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new ConcurrentHashMap<>(),
                new ConcurrentHashMap<>(),
                DEFAULT_DISTANCE_FLUSH_INTERVAL_TICKS,
                true);
    }

    PlayerStatsManager(
            TNexus plugin,
            PlayerStatsRepository playerStatsRepository,
            Clock clock,
            Map<UUID, Instant> sessionStartTimes,
            Map<UUID, Double> pendingTotalDistances,
            Map<UUID, EnumMap<TravelType, Double>> pendingTravelDistances,
            Map<UUID, Integer> pendingBlocksPlaced,
            Map<UUID, Integer> pendingBlocksBroken,
            Map<UUID, Map<String, BlockStatsDelta>> pendingBlockStats,
            Map<UUID, Map<String, EntityDamageDelta>> pendingEntityDamageStats,
            Map<UUID, Map<String, Integer>> pendingKillStats,
            Map<UUID, Map<String, Integer>> pendingCraftStats,
            Map<UUID, Integer> pendingBrewCounts,
            Map<UUID, Map<String, Integer>> pendingSmeltStats,
            Map<UUID, Map<String, Integer>> pendingEnchantStats,
            Map<UUID, Map<String, Integer>> pendingEnchantItemStats,
            Map<UUID, Map<String, Integer>> pendingHarvestStats,
            Map<UUID, Map<String, Integer>> pendingBreedStats,
            Map<UUID, Map<String, Integer>> pendingFishStats,
            Map<UUID, Map<String, ItemStatsDelta>> pendingItemStats,
            Map<BlockPosition, ProcessingAttribution> processingStationAttributions,
            Map<UUID, Instant> worldEditSuppressionWindows,
            long distanceFlushIntervalTicks,
            boolean scheduleDistanceFlushTask) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.playerStatsRepository = Objects.requireNonNull(playerStatsRepository, "playerStatsRepository");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.sessionStartTimes = Objects.requireNonNull(sessionStartTimes, "sessionStartTimes");
        this.distanceLock = new Object();
        this.pendingTotalDistances = Objects.requireNonNull(pendingTotalDistances, "pendingTotalDistances");
        this.pendingTravelDistances = Objects.requireNonNull(pendingTravelDistances, "pendingTravelDistances");
        this.blockLock = new Object();
        this.pendingBlocksPlaced = Objects.requireNonNull(pendingBlocksPlaced, "pendingBlocksPlaced");
        this.pendingBlocksBroken = Objects.requireNonNull(pendingBlocksBroken, "pendingBlocksBroken");
        this.pendingBlockStats = Objects.requireNonNull(pendingBlockStats, "pendingBlockStats");
        this.entityDamageLock = new Object();
        this.pendingEntityDamageStats = Objects.requireNonNull(pendingEntityDamageStats, "pendingEntityDamageStats");
        this.killLock = new Object();
        this.pendingKillStats = Objects.requireNonNull(pendingKillStats, "pendingKillStats");
        this.craftLock = new Object();
        this.pendingCraftStats = Objects.requireNonNull(pendingCraftStats, "pendingCraftStats");
        this.processingLock = new Object();
        this.pendingBrewCounts = Objects.requireNonNull(pendingBrewCounts, "pendingBrewCounts");
        this.pendingSmeltStats = Objects.requireNonNull(pendingSmeltStats, "pendingSmeltStats");
        this.pendingEnchantStats = Objects.requireNonNull(pendingEnchantStats, "pendingEnchantStats");
        this.pendingEnchantItemStats = Objects.requireNonNull(pendingEnchantItemStats, "pendingEnchantItemStats");
        this.farmingLock = new Object();
        this.pendingHarvestStats = Objects.requireNonNull(pendingHarvestStats, "pendingHarvestStats");
        this.pendingBreedStats = Objects.requireNonNull(pendingBreedStats, "pendingBreedStats");
        this.pendingFishStats = Objects.requireNonNull(pendingFishStats, "pendingFishStats");
        this.itemLock = new Object();
        this.pendingItemStats = Objects.requireNonNull(pendingItemStats, "pendingItemStats");
        this.processingStationAttributions = Objects.requireNonNull(
                processingStationAttributions,
                "processingStationAttributions");
        this.worldEditSuppressionWindows = Objects.requireNonNull(
                worldEditSuppressionWindows,
                "worldEditSuppressionWindows");
        this.statsFlushTask = scheduleDistanceFlushTask
                ? this.plugin.getServer().getScheduler().runTaskTimer(
                        this.plugin,
                        this::flushPendingStatsSafely,
                        distanceFlushIntervalTicks,
                        distanceFlushIntervalTicks)
                : null;
    }

    /**
     * Records the start of a player's session and initializes their stats row if needed.
     *
     * @param player joining player
     * @return completion future
     */
    public CompletableFuture<Void> recordSessionStart(Player player) {
        Objects.requireNonNull(player, "player");
        Instant sessionStart = this.clock.instant();
        this.sessionStartTimes.put(player.getUniqueId(), sessionStart);
        return this.playerStatsRepository.ensurePlayerExists(player.getUniqueId(), sessionStart);
    }

    /**
     * Persists the elapsed time for a player's active session.
     *
     * @param player quitting player
     * @return completion future
     */
    public CompletableFuture<Void> recordSessionEnd(Player player) {
        Objects.requireNonNull(player, "player");
        return persistSession(player.getUniqueId(), this.clock.instant());
    }

    /**
     * Persists all currently active sessions, typically during plugin shutdown.
     *
     * @param onlinePlayers online players to flush
     * @return completion future
     */
    public CompletableFuture<Void> flushOnlineSessions(Collection<? extends Player> onlinePlayers) {
        Objects.requireNonNull(onlinePlayers, "onlinePlayers");
        Instant flushTime = this.clock.instant();
        Collection<CompletableFuture<Void>> futures = new ArrayList<>();
        for (Player player : onlinePlayers) {
            futures.add(persistSession(player.getUniqueId(), flushTime));
        }
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    /**
     * Records a player death and its cause.
     *
     * @param player dead player
     * @param cause death cause label
     * @return completion future
     */
    public CompletableFuture<Void> recordDeath(Player player, String cause) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(cause, "cause");
        UUID playerId = player.getUniqueId();
        return CompletableFuture.allOf(
                this.playerStatsRepository.incrementDeaths(playerId),
                this.playerStatsRepository.incrementDeathCause(playerId, cause));
    }

    /**
     * Records a player respawn.
     *
     * @param player respawning player
     * @return completion future
     */
    public CompletableFuture<Void> recordRespawn(Player player) {
        Objects.requireNonNull(player, "player");
        return this.playerStatsRepository.incrementRespawns(player.getUniqueId());
    }

    /**
     * Records distance travelled for the given movement event when it is within the tracked threshold.
     *
     * @param player moving player
     * @param from previous location
     * @param to next location
     */
    public void recordMovement(Player player, Location from, Location to) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (from.getWorld() == null || to.getWorld() == null || !Objects.equals(from.getWorld(), to.getWorld())) {
            return;
        }

        double distance = from.distance(to);
        if (distance <= 0.0D || distance > MAX_TRACKED_DISTANCE_PER_EVENT) {
            return;
        }

        TravelType travelType = resolveTravelType(player);
        UUID playerId = player.getUniqueId();
        synchronized (this.distanceLock) {
            this.pendingTotalDistances.merge(playerId, distance, Double::sum);
            this.pendingTravelDistances
                    .computeIfAbsent(playerId, ignored -> new EnumMap<>(TravelType.class))
                    .merge(travelType, distance, Double::sum);
        }
    }

    /**
     * Records a block placement for the given material.
     *
     * @param player placing player
     * @param material placed material
     */
    public void recordBlockPlacement(Player player, Material material) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(material, "material");
        recordBlockChange(player.getUniqueId(), material, true);
    }

    /**
     * Records a block break for the given material.
     *
     * @param player breaking player
     * @param material broken material
     */
    public void recordBlockBreak(Player player, Material material) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(material, "material");
        recordBlockChange(player.getUniqueId(), material, false);
    }

    /**
     * Records damage dealt by the player to an entity identifier.
     *
     * @param player dealing player
     * @param entityIdentifier entity identifier
     * @param damage final damage amount
     */
    public void recordDamageDealt(Player player, String entityIdentifier, double damage) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(entityIdentifier, "entityIdentifier");
        recordEntityDamage(player.getUniqueId(), entityIdentifier, damage, true);
    }

    /**
     * Records damage taken by the player from an entity identifier.
     *
     * @param player damaged player
     * @param entityIdentifier entity identifier
     * @param damage final damage amount
     */
    public void recordDamageTaken(Player player, String entityIdentifier, double damage) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(entityIdentifier, "entityIdentifier");
        recordEntityDamage(player.getUniqueId(), entityIdentifier, damage, false);
    }

    /**
     * Records a kill by the given player against the target identifier.
     *
     * @param player killing player
     * @param targetIdentifier entity type name or player UUID
     */
    public void recordKill(Player player, String targetIdentifier) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(targetIdentifier, "targetIdentifier");
        synchronized (this.killLock) {
            this.pendingKillStats
                    .computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>())
                    .merge(targetIdentifier, 1, Integer::sum);
        }
    }

    /**
     * Records crafted item counts for the given material.
     *
     * @param player crafting player
     * @param material crafted material
     * @param amount crafted item amount
     */
    public void recordCraft(Player player, Material material, int amount) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(material, "material");
        if (amount <= 0) {
            return;
        }

        synchronized (this.craftLock) {
            this.pendingCraftStats
                    .computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>())
                    .merge(material.name(), amount, Integer::sum);
        }
    }

    /**
     * Records the most recent player interaction with a smelting or brewing block.
     *
     * @param player interacting player
     * @param block interacted processing block
     */
    public void markProcessingStationInteraction(Player player, Block block) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(block, "block");
        this.processingStationAttributions.put(
                BlockPosition.from(block),
                new ProcessingAttribution(
                        player.getUniqueId(),
                        this.clock.instant().plus(PROCESSING_ATTRIBUTION_WINDOW)));
    }

    /**
     * Resolves the last known player attributed to the given processing block.
     *
     * @param block processing block
     * @return attributed player id or {@code null} when none is available
     */
    public UUID resolveProcessingStationPlayer(Block block) {
        Objects.requireNonNull(block, "block");
        BlockPosition position = BlockPosition.from(block);
        ProcessingAttribution attribution = this.processingStationAttributions.get(position);
        if (attribution == null) {
            return null;
        }
        if (attribution.expiresAt().isAfter(this.clock.instant())) {
            return attribution.playerId();
        }
        this.processingStationAttributions.remove(position, attribution);
        return null;
    }

    /**
     * Records a smelted result material for the given player.
     *
     * @param playerId player id
     * @param material smelted result material
     */
    public void recordSmelt(UUID playerId, Material material) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(material, "material");
        synchronized (this.processingLock) {
            this.pendingSmeltStats
                    .computeIfAbsent(playerId, ignored -> new HashMap<>())
                    .merge(material.name(), 1, Integer::sum);
        }
    }

    /**
     * Records a completed brew operation for the given player.
     *
     * @param playerId player id
     */
    public void recordBrew(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        synchronized (this.processingLock) {
            this.pendingBrewCounts.merge(playerId, 1, Integer::sum);
        }
    }

    /**
     * Records an applied enchantment for the given player.
     *
     * @param player player who enchanted an item
     * @param enchantment applied enchantment type
     */
    public void recordEnchantment(Player player, Enchantment enchantment) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(enchantment, "enchantment");
        synchronized (this.processingLock) {
            this.pendingEnchantStats
                    .computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>())
                    .merge(enchantment.getKey().getKey(), 1, Integer::sum);
        }
    }

    /**
     * Records the enchanted item material for the given player.
     *
     * @param player player who enchanted an item
     * @param material enchanted item material
     */
    public void recordEnchantedItem(Player player, Material material) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(material, "material");
        synchronized (this.processingLock) {
            this.pendingEnchantItemStats
                    .computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>())
                    .merge(material.name(), 1, Integer::sum);
        }
    }

    /**
     * Records harvested item counts for the given player.
     *
     * @param player harvesting player
     * @param material harvested material
     * @param amount harvested item amount
     */
    public void recordHarvest(Player player, Material material, int amount) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(material, "material");
        recordFarmingStat(this.pendingHarvestStats, player.getUniqueId(), material.name(), amount);
    }

    /**
     * Records a breeding event for the given player.
     *
     * @param player breeding player
     * @param entityType entity type name
     */
    public void recordBreed(Player player, String entityType) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(entityType, "entityType");
        recordFarmingStat(this.pendingBreedStats, player.getUniqueId(), entityType, 1);
    }

    /**
     * Records a caught fish item for the given player.
     *
     * @param player fishing player
     * @param material caught material
     */
    public void recordFish(Player player, Material material) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(material, "material");
        recordFarmingStat(this.pendingFishStats, player.getUniqueId(), material.name(), 1);
    }

    /**
     * Records picked up item counts for the given player and material.
     *
     * @param player picking up player
     * @param material picked up material
     * @param amount picked up item amount
     */
    public void recordItemPickup(Player player, Material material, int amount) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(material, "material");
        recordItemStat(player.getUniqueId(), material, amount, true);
    }

    /**
     * Records dropped item counts for the given player and material.
     *
     * @param player dropping player
     * @param material dropped material
     * @param amount dropped item amount
     */
    public void recordItemDrop(Player player, Material material, int amount) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(material, "material");
        recordItemStat(player.getUniqueId(), material, amount, false);
    }

    /**
     * Records a successful sleep event for the given player.
     *
     * @param player sleeping player
     * @return completion future
     */
    public CompletableFuture<Void> recordSleep(Player player) {
        Objects.requireNonNull(player, "player");
        return this.playerStatsRepository.incrementSleepCount(player.getUniqueId());
    }

    /**
     * Records a portal usage event for the given player.
     *
     * @param player portal-travelling player
     * @return completion future
     */
    public CompletableFuture<Void> recordPortal(Player player) {
        Objects.requireNonNull(player, "player");
        return this.playerStatsRepository.incrementPortalCount(player.getUniqueId());
    }

    /**
     * Records a chat message event for the given player.
     *
     * @param player chatting player
     * @return completion future
     */
    public CompletableFuture<Void> recordChat(Player player) {
        Objects.requireNonNull(player, "player");
        return this.playerStatsRepository.incrementChatCount(player.getUniqueId());
    }

    /**
     * Records a projectile launch event for the given player and projectile entity type.
     *
     * @param player launching player
     * @param entityType projectile entity type name
     * @return completion future
     */
    public CompletableFuture<Void> recordProjectileLaunch(Player player, String entityType) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(entityType, "entityType");
        return this.playerStatsRepository.incrementProjectileCount(player.getUniqueId(), entityType);
    }

    /**
     * Marks a player as currently performing a WorldEdit-driven bulk edit.
     *
     * @param playerId player id
     */
    public void markWorldEditOperation(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        this.worldEditSuppressionWindows.put(playerId, this.clock.instant().plus(WORLD_EDIT_SUPPRESSION_WINDOW));
    }

    /**
     * Returns whether stat tracking should currently ignore block events for the player.
     *
     * @param playerId player id
     * @return {@code true} when block events should be ignored
     */
    public boolean isWorldEditOperationSuppressed(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        Instant expiresAt = this.worldEditSuppressionWindows.get(playerId);
        if (expiresAt == null) {
            return false;
        }
        if (expiresAt.isAfter(this.clock.instant())) {
            return true;
        }
        this.worldEditSuppressionWindows.remove(playerId, expiresAt);
        return false;
    }

    /**
     * Flushes pending distance statistics to the database asynchronously.
     *
     * @return completion future
     */
    public CompletableFuture<Void> flushPendingDistanceStats() {
        DistanceStatsSnapshot snapshot;
        synchronized (this.distanceLock) {
            if (this.pendingTotalDistances.isEmpty() && this.pendingTravelDistances.isEmpty()) {
                return CompletableFuture.completedFuture(null);
            }
            snapshot = createDistanceStatsSnapshot();
            this.pendingTotalDistances.clear();
            this.pendingTravelDistances.clear();
        }
        return this.playerStatsRepository.addDistanceStats(snapshot.totalDistances(), snapshot.travelDistances())
                .whenComplete((ignored, throwable) -> {
                    if (throwable != null) {
                        restoreDistanceStatsSnapshot(snapshot);
                    }
                });
    }

    /**
     * Flushes pending block statistics to the database asynchronously.
     *
     * @return completion future
     */
    public CompletableFuture<Void> flushPendingBlockStats() {
        BlockStatsSnapshot snapshot;
        synchronized (this.blockLock) {
            if (this.pendingBlocksPlaced.isEmpty()
                    && this.pendingBlocksBroken.isEmpty()
                    && this.pendingBlockStats.isEmpty()) {
                return CompletableFuture.completedFuture(null);
            }
            snapshot = createBlockStatsSnapshot();
            this.pendingBlocksPlaced.clear();
            this.pendingBlocksBroken.clear();
            this.pendingBlockStats.clear();
        }
        return this.playerStatsRepository
                .addBlockStats(snapshot.totalPlacedCounts(), snapshot.totalBrokenCounts(), snapshot.materialStats())
                .whenComplete((ignored, throwable) -> {
                    if (throwable != null) {
                        restoreBlockStatsSnapshot(snapshot);
                    }
                });
    }

    /**
     * Flushes pending entity damage statistics to the database asynchronously.
     *
     * @return completion future
     */
    public CompletableFuture<Void> flushPendingEntityDamageStats() {
        EntityDamageStatsSnapshot snapshot;
        synchronized (this.entityDamageLock) {
            if (this.pendingEntityDamageStats.isEmpty()) {
                return CompletableFuture.completedFuture(null);
            }
            snapshot = createEntityDamageStatsSnapshot();
            this.pendingEntityDamageStats.clear();
        }
        return this.playerStatsRepository.addEntityDamageStats(snapshot.damageStats())
                .whenComplete((ignored, throwable) -> {
                    if (throwable != null) {
                        restoreEntityDamageStatsSnapshot(snapshot);
                    }
                });
    }

    /**
     * Flushes pending kill statistics to the database asynchronously.
     *
     * @return completion future
     */
    public CompletableFuture<Void> flushPendingKillStats() {
        KillStatsSnapshot snapshot;
        synchronized (this.killLock) {
            if (this.pendingKillStats.isEmpty()) {
                return CompletableFuture.completedFuture(null);
            }
            snapshot = createKillStatsSnapshot();
            this.pendingKillStats.clear();
        }
        return this.playerStatsRepository.addKillStats(snapshot.killStats())
                .whenComplete((ignored, throwable) -> {
                    if (throwable != null) {
                        restoreKillStatsSnapshot(snapshot);
                    }
                });
    }

    /**
     * Flushes pending craft statistics to the database asynchronously.
     *
     * @return completion future
     */
    public CompletableFuture<Void> flushPendingCraftStats() {
        CraftStatsSnapshot snapshot;
        synchronized (this.craftLock) {
            if (this.pendingCraftStats.isEmpty()) {
                return CompletableFuture.completedFuture(null);
            }
            snapshot = createCraftStatsSnapshot();
            this.pendingCraftStats.clear();
        }
        return this.playerStatsRepository.addCraftStats(snapshot.craftStats())
                .whenComplete((ignored, throwable) -> {
                    if (throwable != null) {
                        restoreCraftStatsSnapshot(snapshot);
                    }
                });
    }

    /**
     * Flushes pending smelt, brew, and enchant statistics to the database asynchronously.
     *
     * @return completion future
     */
    public CompletableFuture<Void> flushPendingProcessingStats() {
        ProcessingStatsSnapshot snapshot;
        synchronized (this.processingLock) {
            if (this.pendingBrewCounts.isEmpty()
                    && this.pendingSmeltStats.isEmpty()
                    && this.pendingEnchantStats.isEmpty()
                    && this.pendingEnchantItemStats.isEmpty()) {
                return CompletableFuture.completedFuture(null);
            }
            snapshot = createProcessingStatsSnapshot();
            this.pendingBrewCounts.clear();
            this.pendingSmeltStats.clear();
            this.pendingEnchantStats.clear();
            this.pendingEnchantItemStats.clear();
        }
        return this.playerStatsRepository
                .addProcessingStats(
                        snapshot.brewCounts(),
                        snapshot.smeltStats(),
                        snapshot.enchantStats(),
                        snapshot.enchantItemStats())
                .whenComplete((ignored, throwable) -> {
                    if (throwable != null) {
                        restoreProcessingStatsSnapshot(snapshot);
                    }
                });
    }

    /**
     * Flushes pending harvest, breed, and fish statistics to the database asynchronously.
     *
     * @return completion future
     */
    public CompletableFuture<Void> flushPendingFarmingStats() {
        FarmingStatsSnapshot snapshot;
        synchronized (this.farmingLock) {
            if (this.pendingHarvestStats.isEmpty()
                    && this.pendingBreedStats.isEmpty()
                    && this.pendingFishStats.isEmpty()) {
                return CompletableFuture.completedFuture(null);
            }
            snapshot = createFarmingStatsSnapshot();
            this.pendingHarvestStats.clear();
            this.pendingBreedStats.clear();
            this.pendingFishStats.clear();
        }
        return this.playerStatsRepository
                .addFarmingStats(snapshot.harvestStats(), snapshot.breedStats(), snapshot.fishStats())
                .whenComplete((ignored, throwable) -> {
                    if (throwable != null) {
                        restoreFarmingStatsSnapshot(snapshot);
                    }
                });
    }

    /**
     * Flushes pending pickup and drop statistics to the database asynchronously.
     *
     * @return completion future
     */
    public CompletableFuture<Void> flushPendingItemStats() {
        ItemStatsSnapshot snapshot;
        synchronized (this.itemLock) {
            if (this.pendingItemStats.isEmpty()) {
                return CompletableFuture.completedFuture(null);
            }
            snapshot = createItemStatsSnapshot();
            this.pendingItemStats.clear();
        }
        return this.playerStatsRepository.addItemStats(snapshot.itemStats())
                .whenComplete((ignored, throwable) -> {
                    if (throwable != null) {
                        restoreItemStatsSnapshot(snapshot);
                    }
                });
    }

    /**
     * Flushes all in-memory statistics and stops the scheduled distance flush task.
     *
     * @param onlinePlayers currently online players
     * @return completion future
     */
    public CompletableFuture<Void> shutdown(Collection<? extends Player> onlinePlayers) {
        Objects.requireNonNull(onlinePlayers, "onlinePlayers");
        if (this.statsFlushTask != null) {
            this.statsFlushTask.cancel();
        }
        return CompletableFuture.allOf(
                flushOnlineSessions(onlinePlayers),
                flushPendingDistanceStats(),
                flushPendingBlockStats(),
                flushPendingEntityDamageStats(),
                flushPendingKillStats(),
                flushPendingCraftStats(),
                flushPendingProcessingStats(),
                flushPendingFarmingStats(),
                flushPendingItemStats());
    }

    /**
     * Returns a snapshot of currently active sessions keyed by player UUID.
     *
     * @return active session start times
     */
    public Map<UUID, Instant> getActiveSessionStarts() {
        return Map.copyOf(this.sessionStartTimes);
    }

    private CompletableFuture<Void> persistSession(UUID playerId, Instant sessionEnd) {
        Instant sessionStart = this.sessionStartTimes.remove(playerId);
        if (sessionStart == null) {
            return CompletableFuture.completedFuture(null);
        }

        long playTimeSeconds = Math.max(0L, Duration.between(sessionStart, sessionEnd).getSeconds());
        if (playTimeSeconds <= 0L) {
            return CompletableFuture.completedFuture(null);
        }
        return this.playerStatsRepository.recordPlaySession(playerId, sessionStart, sessionEnd, playTimeSeconds);
    }

    private void flushPendingStatsSafely() {
        CompletableFuture.allOf(
                        flushPendingDistanceStats(),
                        flushPendingBlockStats(),
                        flushPendingEntityDamageStats(),
                        flushPendingKillStats(),
                        flushPendingCraftStats(),
                        flushPendingProcessingStats(),
                        flushPendingFarmingStats(),
                        flushPendingItemStats())
                .exceptionally(throwable -> {
            this.plugin.getLogger().log(
                    java.util.logging.Level.SEVERE,
                    "Failed to flush pending player stats.",
                    throwable);
            return null;
        });
    }

    private DistanceStatsSnapshot createDistanceStatsSnapshot() {
        Map<UUID, Double> totalDistancesSnapshot = new HashMap<>(this.pendingTotalDistances);
        Map<UUID, Map<String, Double>> travelDistancesSnapshot = new HashMap<>();
        for (Map.Entry<UUID, EnumMap<TravelType, Double>> entry : this.pendingTravelDistances.entrySet()) {
            Map<String, Double> typeDistances = new HashMap<>();
            for (Map.Entry<TravelType, Double> typeEntry : entry.getValue().entrySet()) {
                typeDistances.put(typeEntry.getKey().name(), typeEntry.getValue());
            }
            travelDistancesSnapshot.put(entry.getKey(), typeDistances);
        }
        return new DistanceStatsSnapshot(totalDistancesSnapshot, travelDistancesSnapshot);
    }

    private void restoreDistanceStatsSnapshot(DistanceStatsSnapshot snapshot) {
        synchronized (this.distanceLock) {
            for (Map.Entry<UUID, Double> entry : snapshot.totalDistances().entrySet()) {
                this.pendingTotalDistances.merge(entry.getKey(), entry.getValue(), Double::sum);
            }
            for (Map.Entry<UUID, Map<String, Double>> entry : snapshot.travelDistances().entrySet()) {
                EnumMap<TravelType, Double> pendingTypes = this.pendingTravelDistances
                        .computeIfAbsent(entry.getKey(), ignored -> new EnumMap<>(TravelType.class));
                for (Map.Entry<String, Double> typeEntry : entry.getValue().entrySet()) {
                    pendingTypes.merge(TravelType.valueOf(typeEntry.getKey()), typeEntry.getValue(), Double::sum);
                }
            }
        }
    }

    private void recordBlockChange(UUID playerId, Material material, boolean placed) {
        String materialName = material.name();
        synchronized (this.blockLock) {
            if (placed) {
                this.pendingBlocksPlaced.merge(playerId, 1, Integer::sum);
            } else {
                this.pendingBlocksBroken.merge(playerId, 1, Integer::sum);
            }

            Map<String, BlockStatsDelta> playerMaterialStats = this.pendingBlockStats
                    .computeIfAbsent(playerId, ignored -> new HashMap<>());
            playerMaterialStats.compute(materialName, (ignored, existing) -> {
                BlockStatsDelta current = existing == null ? new BlockStatsDelta(0, 0) : existing;
                return placed ? current.addPlaced(1) : current.addBroken(1);
            });
        }
    }

    private BlockStatsSnapshot createBlockStatsSnapshot() {
        Map<UUID, Integer> totalPlacedSnapshot = new HashMap<>(this.pendingBlocksPlaced);
        Map<UUID, Integer> totalBrokenSnapshot = new HashMap<>(this.pendingBlocksBroken);
        Map<UUID, Map<String, BlockStatsDelta>> materialStatsSnapshot = new HashMap<>();
        for (Map.Entry<UUID, Map<String, BlockStatsDelta>> entry : this.pendingBlockStats.entrySet()) {
            materialStatsSnapshot.put(entry.getKey(), new HashMap<>(entry.getValue()));
        }
        return new BlockStatsSnapshot(totalPlacedSnapshot, totalBrokenSnapshot, materialStatsSnapshot);
    }

    private void restoreBlockStatsSnapshot(BlockStatsSnapshot snapshot) {
        synchronized (this.blockLock) {
            for (Map.Entry<UUID, Integer> entry : snapshot.totalPlacedCounts().entrySet()) {
                this.pendingBlocksPlaced.merge(entry.getKey(), entry.getValue(), Integer::sum);
            }
            for (Map.Entry<UUID, Integer> entry : snapshot.totalBrokenCounts().entrySet()) {
                this.pendingBlocksBroken.merge(entry.getKey(), entry.getValue(), Integer::sum);
            }
            for (Map.Entry<UUID, Map<String, BlockStatsDelta>> entry : snapshot.materialStats().entrySet()) {
                Map<String, BlockStatsDelta> playerMaterialStats = this.pendingBlockStats
                        .computeIfAbsent(entry.getKey(), ignored -> new HashMap<>());
                for (Map.Entry<String, BlockStatsDelta> materialEntry : entry.getValue().entrySet()) {
                    playerMaterialStats.merge(materialEntry.getKey(), materialEntry.getValue(), (left, right) ->
                            new BlockStatsDelta(
                                    left.placedCount() + right.placedCount(),
                                    left.brokenCount() + right.brokenCount()));
                }
            }
        }
    }

    private EntityDamageStatsSnapshot createEntityDamageStatsSnapshot() {
        Map<UUID, Map<String, EntityDamageDelta>> damageStatsSnapshot = new HashMap<>();
        for (Map.Entry<UUID, Map<String, EntityDamageDelta>> entry : this.pendingEntityDamageStats.entrySet()) {
            damageStatsSnapshot.put(entry.getKey(), new HashMap<>(entry.getValue()));
        }
        return new EntityDamageStatsSnapshot(damageStatsSnapshot);
    }

    private void restoreEntityDamageStatsSnapshot(EntityDamageStatsSnapshot snapshot) {
        synchronized (this.entityDamageLock) {
            for (Map.Entry<UUID, Map<String, EntityDamageDelta>> playerEntry : snapshot.damageStats().entrySet()) {
                Map<String, EntityDamageDelta> playerStats = this.pendingEntityDamageStats
                        .computeIfAbsent(playerEntry.getKey(), ignored -> new HashMap<>());
                for (Map.Entry<String, EntityDamageDelta> entityEntry : playerEntry.getValue().entrySet()) {
                    playerStats.merge(entityEntry.getKey(), entityEntry.getValue(), (left, right) ->
                            new EntityDamageDelta(
                                    left.damageDealt() + right.damageDealt(),
                                    left.damageTaken() + right.damageTaken()));
                }
            }
        }
    }

    private void recordEntityDamage(UUID playerId, String entityIdentifier, double damage, boolean dealt) {
        if (damage <= 0.0D) {
            return;
        }
        synchronized (this.entityDamageLock) {
            Map<String, EntityDamageDelta> playerDamageStats = this.pendingEntityDamageStats
                    .computeIfAbsent(playerId, ignored -> new HashMap<>());
            playerDamageStats.compute(entityIdentifier, (ignored, existing) -> {
                EntityDamageDelta current = existing == null ? new EntityDamageDelta(0.0D, 0.0D) : existing;
                return dealt ? current.addDealt(damage) : current.addTaken(damage);
            });
        }
    }

    private KillStatsSnapshot createKillStatsSnapshot() {
        return new KillStatsSnapshot(copyPendingIntegerStats(this.pendingKillStats));
    }

    private void restoreKillStatsSnapshot(KillStatsSnapshot snapshot) {
        synchronized (this.killLock) {
            restoreProcessingStatMap(this.pendingKillStats, snapshot.killStats());
        }
    }

    private CraftStatsSnapshot createCraftStatsSnapshot() {
        Map<UUID, Map<String, Integer>> craftStatsSnapshot = new HashMap<>();
        for (Map.Entry<UUID, Map<String, Integer>> entry : this.pendingCraftStats.entrySet()) {
            craftStatsSnapshot.put(entry.getKey(), new HashMap<>(entry.getValue()));
        }
        return new CraftStatsSnapshot(craftStatsSnapshot);
    }

    private void restoreCraftStatsSnapshot(CraftStatsSnapshot snapshot) {
        synchronized (this.craftLock) {
            for (Map.Entry<UUID, Map<String, Integer>> playerEntry : snapshot.craftStats().entrySet()) {
                Map<String, Integer> playerCraftStats = this.pendingCraftStats
                        .computeIfAbsent(playerEntry.getKey(), ignored -> new HashMap<>());
                for (Map.Entry<String, Integer> materialEntry : playerEntry.getValue().entrySet()) {
                    playerCraftStats.merge(materialEntry.getKey(), materialEntry.getValue(), Integer::sum);
                }
            }
        }
    }

    private ProcessingStatsSnapshot createProcessingStatsSnapshot() {
        Map<UUID, Integer> brewCountsSnapshot = new HashMap<>(this.pendingBrewCounts);
        Map<UUID, Map<String, Integer>> smeltStatsSnapshot = new HashMap<>();
        for (Map.Entry<UUID, Map<String, Integer>> entry : this.pendingSmeltStats.entrySet()) {
            smeltStatsSnapshot.put(entry.getKey(), new HashMap<>(entry.getValue()));
        }
        Map<UUID, Map<String, Integer>> enchantStatsSnapshot = new HashMap<>();
        for (Map.Entry<UUID, Map<String, Integer>> entry : this.pendingEnchantStats.entrySet()) {
            enchantStatsSnapshot.put(entry.getKey(), new HashMap<>(entry.getValue()));
        }
        Map<UUID, Map<String, Integer>> enchantItemStatsSnapshot = new HashMap<>();
        for (Map.Entry<UUID, Map<String, Integer>> entry : this.pendingEnchantItemStats.entrySet()) {
            enchantItemStatsSnapshot.put(entry.getKey(), new HashMap<>(entry.getValue()));
        }
        return new ProcessingStatsSnapshot(
                brewCountsSnapshot,
                smeltStatsSnapshot,
                enchantStatsSnapshot,
                enchantItemStatsSnapshot);
    }

    private void restoreProcessingStatsSnapshot(ProcessingStatsSnapshot snapshot) {
        synchronized (this.processingLock) {
            for (Map.Entry<UUID, Integer> entry : snapshot.brewCounts().entrySet()) {
                this.pendingBrewCounts.merge(entry.getKey(), entry.getValue(), Integer::sum);
            }
            restoreProcessingStatMap(this.pendingSmeltStats, snapshot.smeltStats());
            restoreProcessingStatMap(this.pendingEnchantStats, snapshot.enchantStats());
            restoreProcessingStatMap(this.pendingEnchantItemStats, snapshot.enchantItemStats());
        }
    }

    private void restoreProcessingStatMap(
            Map<UUID, Map<String, Integer>> pendingStats,
            Map<UUID, Map<String, Integer>> snapshotStats) {
        for (Map.Entry<UUID, Map<String, Integer>> playerEntry : snapshotStats.entrySet()) {
            Map<String, Integer> playerStats = pendingStats.computeIfAbsent(playerEntry.getKey(), ignored -> new HashMap<>());
            for (Map.Entry<String, Integer> statEntry : playerEntry.getValue().entrySet()) {
                playerStats.merge(statEntry.getKey(), statEntry.getValue(), Integer::sum);
            }
        }
    }

    private void recordFarmingStat(
            Map<UUID, Map<String, Integer>> pendingStats,
            UUID playerId,
            String key,
            int amount) {
        if (amount <= 0) {
            return;
        }
        synchronized (this.farmingLock) {
            pendingStats.computeIfAbsent(playerId, ignored -> new HashMap<>()).merge(key, amount, Integer::sum);
        }
    }

    private FarmingStatsSnapshot createFarmingStatsSnapshot() {
        return new FarmingStatsSnapshot(
                copyPendingIntegerStats(this.pendingHarvestStats),
                copyPendingIntegerStats(this.pendingBreedStats),
                copyPendingIntegerStats(this.pendingFishStats));
    }

    private void restoreFarmingStatsSnapshot(FarmingStatsSnapshot snapshot) {
        synchronized (this.farmingLock) {
            restoreProcessingStatMap(this.pendingHarvestStats, snapshot.harvestStats());
            restoreProcessingStatMap(this.pendingBreedStats, snapshot.breedStats());
            restoreProcessingStatMap(this.pendingFishStats, snapshot.fishStats());
        }
    }

    private void recordItemStat(UUID playerId, Material material, int amount, boolean pickedUp) {
        if (amount <= 0) {
            return;
        }
        synchronized (this.itemLock) {
            Map<String, ItemStatsDelta> playerItemStats = this.pendingItemStats
                    .computeIfAbsent(playerId, ignored -> new HashMap<>());
            playerItemStats.compute(material.name(), (ignored, existing) -> {
                ItemStatsDelta current = existing == null ? new ItemStatsDelta(0, 0) : existing;
                return pickedUp ? current.addPickup(amount) : current.addDrop(amount);
            });
        }
    }

    private ItemStatsSnapshot createItemStatsSnapshot() {
        Map<UUID, Map<String, ItemStatsDelta>> snapshot = new HashMap<>();
        for (Map.Entry<UUID, Map<String, ItemStatsDelta>> entry : this.pendingItemStats.entrySet()) {
            snapshot.put(entry.getKey(), new HashMap<>(entry.getValue()));
        }
        return new ItemStatsSnapshot(snapshot);
    }

    private void restoreItemStatsSnapshot(ItemStatsSnapshot snapshot) {
        synchronized (this.itemLock) {
            for (Map.Entry<UUID, Map<String, ItemStatsDelta>> playerEntry : snapshot.itemStats().entrySet()) {
                Map<String, ItemStatsDelta> playerStats = this.pendingItemStats
                        .computeIfAbsent(playerEntry.getKey(), ignored -> new HashMap<>());
                for (Map.Entry<String, ItemStatsDelta> materialEntry : playerEntry.getValue().entrySet()) {
                    playerStats.merge(materialEntry.getKey(), materialEntry.getValue(), (left, right) ->
                            new ItemStatsDelta(
                                    left.pickupCount() + right.pickupCount(),
                                    left.dropCount() + right.dropCount()));
                }
            }
        }
    }

    private Map<UUID, Map<String, Integer>> copyPendingIntegerStats(Map<UUID, Map<String, Integer>> pendingStats) {
        Map<UUID, Map<String, Integer>> snapshot = new HashMap<>();
        for (Map.Entry<UUID, Map<String, Integer>> entry : pendingStats.entrySet()) {
            snapshot.put(entry.getKey(), new HashMap<>(entry.getValue()));
        }
        return snapshot;
    }

    private TravelType resolveTravelType(Player player) {
        if (player.isGliding()) {
            return TravelType.ELYTRA;
        }
        if (player.isFlying()) {
            return TravelType.FLY;
        }
        if (player.isSwimming()) {
            return TravelType.SWIM;
        }

        Entity vehicle = player.getVehicle();
        if (vehicle instanceof Boat) {
            return TravelType.BOAT;
        }
        if (vehicle instanceof Minecart) {
            return TravelType.MINECART;
        }
        if (vehicle instanceof AbstractHorse) {
            return TravelType.HORSE;
        }
        if (vehicle != null) {
            return TravelType.VEHICLE_OTHER;
        }

        if (player.isSprinting()) {
            return TravelType.SPRINT;
        }
        return TravelType.WALK;
    }

    private record DistanceStatsSnapshot(
            Map<UUID, Double> totalDistances,
            Map<UUID, Map<String, Double>> travelDistances) {
    }

    private record BlockStatsSnapshot(
            Map<UUID, Integer> totalPlacedCounts,
            Map<UUID, Integer> totalBrokenCounts,
            Map<UUID, Map<String, BlockStatsDelta>> materialStats) {
    }

    private record EntityDamageStatsSnapshot(Map<UUID, Map<String, EntityDamageDelta>> damageStats) {
    }

    private record KillStatsSnapshot(Map<UUID, Map<String, Integer>> killStats) {
    }

    private record CraftStatsSnapshot(Map<UUID, Map<String, Integer>> craftStats) {
    }

    private record ProcessingStatsSnapshot(
            Map<UUID, Integer> brewCounts,
            Map<UUID, Map<String, Integer>> smeltStats,
            Map<UUID, Map<String, Integer>> enchantStats,
            Map<UUID, Map<String, Integer>> enchantItemStats) {
    }

    private record FarmingStatsSnapshot(
            Map<UUID, Map<String, Integer>> harvestStats,
            Map<UUID, Map<String, Integer>> breedStats,
            Map<UUID, Map<String, Integer>> fishStats) {
    }

    private record ItemStatsSnapshot(Map<UUID, Map<String, ItemStatsDelta>> itemStats) {
    }

    private record ProcessingAttribution(UUID playerId, Instant expiresAt) {
    }

    enum TravelType {
        WALK,
        SPRINT,
        SWIM,
        ELYTRA,
        FLY,
        BOAT,
        MINECART,
        HORSE,
        VEHICLE_OTHER
    }
}
