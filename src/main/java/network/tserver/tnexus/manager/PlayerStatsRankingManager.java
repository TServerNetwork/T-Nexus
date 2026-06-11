package network.tserver.tnexus.manager;

import java.text.NumberFormat;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.database.repository.PlayerStatsRankingRepository;
import network.tserver.tnexus.database.repository.PlayerStatsRankingRepository.PlaySessionRecord;
import network.tserver.tnexus.gui.player.PlayerStatsRankingGui;
import network.tserver.tnexus.manager.PlayerStatsViewerManager.StatsPeriodFilter;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/**
 * Loads and formats play-time leaderboard data.
 */
public final class PlayerStatsRankingManager {

    private static final Locale NUMBER_LOCALE = Locale.JAPAN;

    private final TNexus plugin;
    private final PlayerStatsRankingRepository repository;
    private final PlayerStatsManager playerStatsManager;
    private final Clock clock;

    /**
     * Creates a new ranking manager.
     *
     * @param plugin plugin instance
     * @param repository ranking repository
     * @param playerStatsManager live player stats manager
     */
    public PlayerStatsRankingManager(
            TNexus plugin,
            PlayerStatsRankingRepository repository,
            PlayerStatsManager playerStatsManager) {
        this(plugin, repository, playerStatsManager, Clock.systemDefaultZone());
    }

    PlayerStatsRankingManager(
            TNexus plugin,
            PlayerStatsRankingRepository repository,
            PlayerStatsManager playerStatsManager,
            Clock clock) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.playerStatsManager = Objects.requireNonNull(playerStatsManager, "playerStatsManager");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Opens the ranking GUI for the given viewer.
     *
     * @param viewer viewing player
     * @param periodFilter active period filter
     */
    public void openRankingGui(Player viewer, StatsPeriodFilter periodFilter) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(periodFilter, "periodFilter");
        loadRanking(viewer.getUniqueId(), periodFilter)
                .whenComplete((ranking, throwable) -> Bukkit.getScheduler().runTask(this.plugin, () -> {
                    if (throwable != null) {
                        this.plugin.getMessageConfig().sendMessage(viewer, "stats.command.load-failed");
                        return;
                    }
                    new PlayerStatsRankingGui(
                            this.plugin,
                            this,
                            viewer,
                            Objects.requireNonNull(ranking, "ranking"),
                            periodFilter).open();
                }));
    }

    /**
     * Loads ranking data for the selected period.
     *
     * @param viewerId viewer UUID
     * @param periodFilter active period filter
     * @return completion future
     */
    public CompletableFuture<RankingSnapshot> loadRanking(UUID viewerId, StatsPeriodFilter periodFilter) {
        Objects.requireNonNull(viewerId, "viewerId");
        Objects.requireNonNull(periodFilter, "periodFilter");
        Instant periodStart = resolvePeriodStart(periodFilter);
        return (periodStart == null
                ? loadAllTimeEntries(viewerId, periodFilter)
                : loadFilteredEntries(viewerId, periodFilter, periodStart));
    }

    /**
     * Returns the localized period label.
     *
     * @param filter period filter
     * @return localized label
     */
    public String getPeriodLabel(StatsPeriodFilter filter) {
        return this.plugin.getMessageConfig().getMessage(filter.labelKey());
    }

    /**
     * Formats a duration value for GUI display.
     *
     * @param totalSeconds duration in seconds
     * @return formatted text
     */
    public String formatDuration(long totalSeconds) {
        long safeSeconds = Math.max(0L, totalSeconds);
        long hours = safeSeconds / 3600L;
        long minutes = (safeSeconds % 3600L) / 60L;
        long seconds = safeSeconds % 60L;
        return "%d:%02d:%02d".formatted(hours, minutes, seconds);
    }

    /**
     * Formats a 1-based rank number.
     *
     * @param rank rank value
     * @return formatted rank
     */
    public String formatRank(int rank) {
        return "#" + NumberFormat.getIntegerInstance(NUMBER_LOCALE).format(rank);
    }

    private CompletableFuture<RankingSnapshot> loadAllTimeEntries(UUID viewerId, StatsPeriodFilter periodFilter) {
        return this.repository.loadAllTimePlayTimes()
                .thenApply(totals -> {
                    Map<UUID, Long> mergedTotals = new LinkedHashMap<>(totals);
                    Instant now = this.clock.instant();
                    for (Map.Entry<UUID, Instant> entry : this.playerStatsManager.getActiveSessionStarts().entrySet()) {
                        long activeSeconds = Math.max(0L, Duration.between(entry.getValue(), now).getSeconds());
                        if (activeSeconds > 0L) {
                            mergedTotals.merge(entry.getKey(), activeSeconds, Long::sum);
                        }
                    }
                    return buildSnapshot(viewerId, periodFilter, mergedTotals);
                });
    }

    private CompletableFuture<RankingSnapshot> loadFilteredEntries(
            UUID viewerId,
            StatsPeriodFilter periodFilter,
            Instant periodStart) {
        return this.repository.loadSessionsSince(periodStart)
                .thenApply(sessions -> {
                    Map<UUID, Long> totals = new LinkedHashMap<>();
                    for (PlaySessionRecord session : sessions) {
                        long overlapSeconds = calculateOverlapSeconds(
                                session.sessionStart(),
                                session.sessionEnd(),
                                periodStart,
                                this.clock.instant());
                        if (overlapSeconds > 0L) {
                            totals.merge(session.playerId(), overlapSeconds, Long::sum);
                        }
                    }

                    Instant now = this.clock.instant();
                    for (Map.Entry<UUID, Instant> entry : this.playerStatsManager.getActiveSessionStarts().entrySet()) {
                        long overlapSeconds = calculateOverlapSeconds(entry.getValue(), now, periodStart, now);
                        if (overlapSeconds > 0L) {
                            totals.merge(entry.getKey(), overlapSeconds, Long::sum);
                        }
                    }
                    return buildSnapshot(viewerId, periodFilter, totals);
                });
    }

    private RankingSnapshot buildSnapshot(UUID viewerId, StatsPeriodFilter periodFilter, Map<UUID, Long> totals) {
        List<Map.Entry<UUID, Long>> sortedEntries = totals.entrySet().stream()
                .filter(entry -> entry.getValue() > 0L)
                .sorted(Comparator
                        .comparingLong((Map.Entry<UUID, Long> entry) -> entry.getValue())
                        .reversed()
                        .thenComparing(entry -> resolveTargetName(Bukkit.getOfflinePlayer(entry.getKey())).toLowerCase(Locale.ROOT))
                        .thenComparing(entry -> entry.getKey().toString()))
                .toList();

        List<RankingEntry> rankingEntries = new ArrayList<>();
        RankingEntry viewerEntry = null;
        int previousRank = 0;
        long previousValue = Long.MIN_VALUE;
        for (int index = 0; index < sortedEntries.size(); index++) {
            Map.Entry<UUID, Long> entry = sortedEntries.get(index);
            int rank = entry.getValue() == previousValue ? previousRank : index + 1;
            previousRank = rank;
            previousValue = entry.getValue();
            OfflinePlayer player = Bukkit.getOfflinePlayer(entry.getKey());
            RankingEntry rankingEntry = new RankingEntry(
                    entry.getKey(),
                    resolveTargetName(player),
                    rank,
                    entry.getValue(),
                    entry.getKey().equals(viewerId));
            rankingEntries.add(rankingEntry);
            if (rankingEntry.highlighted()) {
                viewerEntry = rankingEntry;
            }
        }
        return new RankingSnapshot(periodFilter, rankingEntries, viewerEntry);
    }

    private long calculateOverlapSeconds(
            Instant sessionStart,
            Instant sessionEnd,
            Instant periodStart,
            Instant periodEnd) {
        Instant effectiveStart = sessionStart.isAfter(periodStart) ? sessionStart : periodStart;
        Instant effectiveEnd = sessionEnd.isBefore(periodEnd) ? sessionEnd : periodEnd;
        if (!effectiveEnd.isAfter(effectiveStart)) {
            return 0L;
        }
        return Duration.between(effectiveStart, effectiveEnd).getSeconds();
    }

    private Instant resolvePeriodStart(StatsPeriodFilter filter) {
        LocalDate today = LocalDate.now(this.clock);
        ZoneId zoneId = this.clock.getZone();
        return switch (filter) {
            case ALL_TIME -> null;
            case TODAY -> LocalDateTime.of(today, LocalTime.MIN).atZone(zoneId).toInstant();
            case THIS_WEEK -> LocalDateTime.of(
                    today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                    LocalTime.MIN).atZone(zoneId).toInstant();
            case THIS_MONTH -> LocalDateTime.of(today.withDayOfMonth(1), LocalTime.MIN).atZone(zoneId).toInstant();
        };
    }

    private String resolveTargetName(OfflinePlayer target) {
        return target.getName() == null || target.getName().isBlank()
                ? target.getUniqueId().toString()
                : target.getName();
    }

    /**
     * Single ranking entry.
     *
     * @param playerId player UUID
     * @param playerName player name
     * @param rank 1-based rank
     * @param playTimeSeconds total play time for the selected period
     * @param highlighted whether the entry belongs to the viewer
     */
    public record RankingEntry(
            UUID playerId,
            String playerName,
            int rank,
            long playTimeSeconds,
            boolean highlighted) {
    }

    /**
     * Ranking GUI snapshot.
     *
     * @param periodFilter active period filter
     * @param entries sorted ranking entries
     * @param viewerEntry viewer ranking entry, or {@code null}
     */
    public record RankingSnapshot(
            StatsPeriodFilter periodFilter,
            List<RankingEntry> entries,
            RankingEntry viewerEntry) {
    }
}
