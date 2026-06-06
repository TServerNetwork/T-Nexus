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
import network.tserver.tnexus.database.repository.PlayerStatsRepository;
import org.bukkit.Location;
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

    private final TNexus plugin;
    private final PlayerStatsRepository playerStatsRepository;
    private final Clock clock;
    private final Map<UUID, Instant> sessionStartTimes;
    private final Object distanceLock;
    private final Map<UUID, Double> pendingTotalDistances;
    private final Map<UUID, EnumMap<TravelType, Double>> pendingTravelDistances;
    private final BukkitTask distanceFlushTask;

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
            long distanceFlushIntervalTicks,
            boolean scheduleDistanceFlushTask) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.playerStatsRepository = Objects.requireNonNull(playerStatsRepository, "playerStatsRepository");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.sessionStartTimes = Objects.requireNonNull(sessionStartTimes, "sessionStartTimes");
        this.distanceLock = new Object();
        this.pendingTotalDistances = Objects.requireNonNull(pendingTotalDistances, "pendingTotalDistances");
        this.pendingTravelDistances = Objects.requireNonNull(pendingTravelDistances, "pendingTravelDistances");
        this.distanceFlushTask = scheduleDistanceFlushTask
                ? this.plugin.getServer().getScheduler().runTaskTimer(
                        this.plugin,
                        this::flushPendingDistanceStatsSafely,
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
     * Flushes all in-memory statistics and stops the scheduled distance flush task.
     *
     * @param onlinePlayers currently online players
     * @return completion future
     */
    public CompletableFuture<Void> shutdown(Collection<? extends Player> onlinePlayers) {
        Objects.requireNonNull(onlinePlayers, "onlinePlayers");
        if (this.distanceFlushTask != null) {
            this.distanceFlushTask.cancel();
        }
        return CompletableFuture.allOf(
                flushOnlineSessions(onlinePlayers),
                flushPendingDistanceStats());
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
        return this.playerStatsRepository.addPlayTime(playerId, playTimeSeconds);
    }

    private void flushPendingDistanceStatsSafely() {
        flushPendingDistanceStats().exceptionally(throwable -> {
            this.plugin.getLogger().log(
                    java.util.logging.Level.SEVERE,
                    "Failed to flush pending player distance stats.",
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
