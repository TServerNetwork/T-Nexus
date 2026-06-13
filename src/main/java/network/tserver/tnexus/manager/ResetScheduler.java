package network.tserver.tnexus.manager;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import network.tserver.tnexus.TNexus;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

/**
 * Schedules reset countdown broadcasts and reset execution tasks for resource worlds.
 */
public final class ResetScheduler {

    private static final List<Long> NOTIFICATION_OFFSETS = List.of(
            172800L, 86400L, 43200L, 21600L, 10800L, 7200L, 3600L,
            1800L, 1200L, 900L, 600L, 300L, 180L, 120L, 60L,
            50L, 40L, 30L, 20L, 15L, 10L, 9L, 8L, 7L, 6L, 5L, 4L, 3L, 2L, 1L);

    private final TNexus plugin;
    private final ResourceWorldManager manager;
    private final Clock clock;
    private final ZoneId zoneId;
    private final Map<String, List<BukkitTask>> scheduledTasks;

    /**
     * Creates a reset scheduler using the system clock.
     *
     * @param plugin owner plugin
     * @param manager resource world manager
     */
    public ResetScheduler(TNexus plugin, ResourceWorldManager manager) {
        this(plugin, manager, Clock.systemDefaultZone());
    }

    /**
     * Creates a reset scheduler with an explicit clock.
     *
     * @param plugin owner plugin
     * @param manager resource world manager
     * @param clock clock used for delay calculations
     */
    public ResetScheduler(TNexus plugin, ResourceWorldManager manager, Clock clock) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.manager = Objects.requireNonNull(manager, "manager");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.zoneId = this.clock.getZone();
        this.scheduledTasks = new ConcurrentHashMap<>();
    }

    /**
     * Schedules countdowns and reset execution for every configured resource world.
     *
     * @return completion future
     */
    public CompletableFuture<Void> scheduleAll() {
        CompletableFuture<?>[] futures = this.manager.getSettings().worlds().stream()
                .map(world -> this.manager.getNextResetTime(world.name())
                        .thenAccept(nextReset -> nextReset.ifPresent(resetTime -> Bukkit.getScheduler().runTask(
                                this.plugin,
                                () -> scheduleWorld(world.name(), resetTime)))))
                .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(futures);
    }

    /**
     * Cancels every scheduled countdown and reset task.
     */
    public void cancelAll() {
        this.scheduledTasks.values().forEach(tasks -> tasks.forEach(BukkitTask::cancel));
        this.scheduledTasks.clear();
    }

    void scheduleWorld(String worldName, LocalDateTime nextResetTime) {
        cancelWorld(worldName);

        long nowEpochMillis = this.clock.millis();
        long resetEpochMillis = nextResetTime.atZone(this.zoneId).toInstant().toEpochMilli();
        List<BukkitTask> worldTasks = new ArrayList<>();

        for (long offsetSeconds : NOTIFICATION_OFFSETS) {
            long fireAtMillis = resetEpochMillis - (offsetSeconds * 1000L);
            long delayMillis = fireAtMillis - nowEpochMillis;
            if (delayMillis <= 0L) {
                continue;
            }

            long delayTicks = Math.max(1L, (delayMillis + 49L) / 50L);
            worldTasks.add(Bukkit.getScheduler().runTaskLater(
                    this.plugin,
                    () -> broadcastCountdown(worldName, offsetSeconds),
                    delayTicks));
        }

        long resetDelayMillis = resetEpochMillis - nowEpochMillis;
        if (resetDelayMillis > 0L) {
            long resetDelayTicks = Math.max(1L, (resetDelayMillis + 49L) / 50L);
            worldTasks.add(Bukkit.getScheduler().runTaskLater(
                    this.plugin,
                    () -> this.manager.executeReset(worldName)
                            .thenAccept(nextScheduledReset -> Bukkit.getScheduler().runTask(
                                    this.plugin,
                                    () -> scheduleWorld(worldName, nextScheduledReset)))
                            .exceptionally(throwable -> {
                                this.plugin.getLogger().log(
                                        Level.SEVERE,
                                        "Failed to execute scheduled reset for " + worldName,
                                        throwable);
                                return null;
                            }),
                    resetDelayTicks));
        }

        if (!worldTasks.isEmpty()) {
            this.scheduledTasks.put(worldName, worldTasks);
        }
    }

    int getScheduledTaskCount(String worldName) {
        return this.scheduledTasks.getOrDefault(worldName, List.of()).size();
    }

    private void cancelWorld(String worldName) {
        List<BukkitTask> tasks = this.scheduledTasks.remove(worldName);
        if (tasks == null) {
            return;
        }
        tasks.forEach(BukkitTask::cancel);
    }

    private void broadcastCountdown(String worldName, long offsetSeconds) {
        String message = this.plugin.getMessageConfig().getMessage("prefix")
                + this.plugin.getMessageConfig().getMessage(
                "resource-world.countdown",
                worldName,
                this.manager.formatCountdown(offsetSeconds));
        Bukkit.broadcastMessage(message);
    }
}
