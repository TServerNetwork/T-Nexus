package network.tserver.tnexus.manager;

import java.text.NumberFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.database.repository.ServerStatsRepository;
import network.tserver.tnexus.database.repository.ServerStatsRepository.DatabaseServerStats;
import network.tserver.tnexus.gui.ServerStatsGui;
import network.tserver.tnexus.util.CurrencyFormatter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Coordinates server statistics loading and GUI updates.
 */
public final class ServerStatsManager {

    private static final Locale NUMBER_LOCALE = Locale.JAPAN;

    private final TNexus plugin;
    private final ServerStatsRepository repository;
    private final Instant startupTime;

    /**
     * Creates a new server stats manager.
     *
     * @param plugin plugin instance
     * @param repository server stats repository
     */
    public ServerStatsManager(TNexus plugin, ServerStatsRepository repository) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.startupTime = Instant.now();
    }

    /**
     * Opens the server stats GUI for the player and refreshes it asynchronously.
     *
     * @param player target player
     */
    public void openServerStats(Player player) {
        Objects.requireNonNull(player, "player");
        ServerStatsGui gui = new ServerStatsGui(this.plugin, player);
        gui.open();
        loadStats().whenComplete((stats, throwable) -> Bukkit.getScheduler().runTask(this.plugin, () -> {
            if (throwable != null) {
                this.plugin.getMessageConfig().sendMessage(player, "server-stats.command.load-failed");
                return;
            }
            if (this.plugin.getGuiManager().getOpenGui(player) != gui) {
                return;
            }
            gui.showStats(Objects.requireNonNull(stats, "stats"));
        }));
    }

    /**
     * Loads a formatted snapshot for display.
     *
     * @return completion future with formatted stats
     */
    public CompletableFuture<ServerStatsSnapshot> loadStats() {
        CompletableFuture<ServerStatsSnapshot> future = new CompletableFuture<>();
        this.repository.loadStats().whenComplete((stats, throwable) -> Bukkit.getScheduler().runTask(this.plugin, () -> {
            if (throwable != null) {
                future.completeExceptionally(throwable);
                return;
            }
            future.complete(toSnapshot(Objects.requireNonNull(stats, "stats")));
        }));
        return future;
    }

    private ServerStatsSnapshot toSnapshot(DatabaseServerStats databaseStats) {
        return new ServerStatsSnapshot(
                Bukkit.getOnlinePlayers().size(),
                formatWholeNumber(databaseStats.totalTransactions()),
                CurrencyFormatter.format(this.plugin, databaseStats.totalTransactionAmount()),
                formatDuration(Duration.between(this.startupTime, Instant.now())),
                CurrencyFormatter.format(this.plugin, databaseStats.circulationAmount()),
                formatWholeNumber(databaseStats.activeShopCount()));
    }

    private String formatWholeNumber(long value) {
        return NumberFormat.getIntegerInstance(NUMBER_LOCALE).format(value);
    }

    private String formatDuration(Duration duration) {
        long totalSeconds = Math.max(0L, duration.getSeconds());
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        return "%d:%02d:%02d".formatted(hours, minutes, seconds);
    }

    /**
     * Formatted server stats values ready for GUI rendering.
     *
     * @param onlinePlayers current online player count
     * @param totalTransactions formatted total transaction count
     * @param totalTransactionAmount formatted total transaction amount
     * @param uptime formatted uptime
     * @param circulationAmount formatted circulation amount
     * @param activeShopCount formatted active shop count
     */
    public record ServerStatsSnapshot(
            int onlinePlayers,
            String totalTransactions,
            String totalTransactionAmount,
            String uptime,
            String circulationAmount,
            String activeShopCount) {
    }
}
