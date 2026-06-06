package network.tserver.tnexus.manager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.database.repository.PlayerStatsRepository;
import org.bukkit.entity.Player;

/**
 * Tracks in-memory player sessions and persists aggregate play time.
 */
public class PlayerStatsManager {

    private final TNexus plugin;
    private final PlayerStatsRepository playerStatsRepository;
    private final Clock clock;
    private final Map<UUID, Instant> sessionStartTimes;

    /**
     * Creates a new player stats manager.
     *
     * @param plugin plugin instance
     * @param playerStatsRepository player stats repository
     */
    public PlayerStatsManager(TNexus plugin, PlayerStatsRepository playerStatsRepository) {
        this(plugin, playerStatsRepository, Clock.systemUTC(), new ConcurrentHashMap<>());
    }

    PlayerStatsManager(
            TNexus plugin,
            PlayerStatsRepository playerStatsRepository,
            Clock clock,
            Map<UUID, Instant> sessionStartTimes) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.playerStatsRepository = Objects.requireNonNull(playerStatsRepository, "playerStatsRepository");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.sessionStartTimes = Objects.requireNonNull(sessionStartTimes, "sessionStartTimes");
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
}
