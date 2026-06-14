package network.tserver.tnexus.command;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.config.ConfigManager;
import network.tserver.tnexus.database.repository.ResourceWorldResetRepository;
import network.tserver.tnexus.manager.ResourceWorldManager;
import network.tserver.tnexus.util.TabCompleterUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

/**
 * Handles the /resource command family.
 */
public final class ResourceWorldCommand {

    private static final String ADMIN_PERMISSION = "tnexus.admin";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");

    private final TNexus plugin;

    /**
     * Creates a new resource-world command handler.
     *
     * @param plugin plugin instance
     */
    public ResourceWorldCommand(TNexus plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(plugin.getResourceWorldManager(), "resourceWorldManager");
    }

    /**
     * Executes the /resource command.
     *
     * @param sender command sender
     * @param args command arguments
     */
    public void execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            showList(sender);
            return;
        }

        String action = normalize(args[0]);
        switch (action) {
            case "info" -> {
                if (args.length != 2) {
                    this.plugin.getMessageConfig().sendMessage(sender, "general.unknown-command");
                    return;
                }
                String worldName = resolveWorldName(args[1]);
                if (worldName == null) {
                    this.plugin.getMessageConfig().sendMessage(sender, "general.unknown-command");
                    return;
                }
                showInfo(sender, worldName);
            }
            case "status" -> {
                if (!sender.hasPermission(ADMIN_PERMISSION)) {
                    this.plugin.getMessageConfig().sendMessage(sender, "general.no-permission");
                    return;
                }
                if (args.length != 1) {
                    this.plugin.getMessageConfig().sendMessage(sender, "general.unknown-command");
                    return;
                }
                showAdminStatus(sender);
            }
            case "reset" -> {
                if (!sender.hasPermission(ADMIN_PERMISSION)) {
                    this.plugin.getMessageConfig().sendMessage(sender, "general.no-permission");
                    return;
                }
                if (args.length != 2) {
                    this.plugin.getMessageConfig().sendMessage(sender, "general.unknown-command");
                    return;
                }
                String worldName = resolveWorldName(args[1]);
                if (worldName == null) {
                    this.plugin.getMessageConfig().sendMessage(sender, "general.unknown-command");
                    return;
                }
                resetWorld(sender, worldName);
            }
            default -> this.plugin.getMessageConfig().sendMessage(sender, "general.unknown-command");
        }
    }

    /**
     * Returns tab completions for /resource.
     *
     * @param sender command sender
     * @param args command arguments
     * @return matching completions
     */
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length <= 1) {
            List<String> root = new ArrayList<>();
            root.addAll(TabCompleterUtil.filter(List.of("info"), args.length == 0 ? "" : args[0]));
            if (sender.hasPermission(ADMIN_PERMISSION)) {
                root.addAll(TabCompleterUtil.filter(List.of("status"), args.length == 0 ? "" : args[0]));
                root.addAll(TabCompleterUtil.filter(List.of("reset"), args.length == 0 ? "" : args[0]));
            }
            return root;
        }

        if ("info".equalsIgnoreCase(args[0]) && args.length == 2) {
            return TabCompleterUtil.filter(resourceWorldNames(), args[1]);
        }
        if ("reset".equalsIgnoreCase(args[0]) && args.length == 2 && sender.hasPermission(ADMIN_PERMISSION)) {
            return TabCompleterUtil.filter(resourceWorldNames(), args[1]);
        }
        return List.of();
    }

    private void showList(CommandSender sender) {
        List<ConfigManager.ResourceWorldDefinition> worlds = getManager().getSettings().worlds();
        CompletableFuture<?>[] futures = worlds.stream()
                .map(world -> loadSnapshot(world.name(), world))
                .toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(futures)
                .thenRun(() -> runSync(() -> {
                    sendRaw(sender, this.plugin.getMessageConfig().getMessage("resource.command.list.header"));
                    sendPrefixed(sender, "resource.command.list.title", Map.of());
                    for (CompletableFuture<?> future : futures) {
                        ResourceWorldSnapshot snapshot = (ResourceWorldSnapshot) future.join();
                        sendPrefixed(
                                sender,
                                "resource.command.list.entry",
                                Map.of(
                                        "display_name", snapshot.displayName(),
                                        "world", snapshot.worldName()));
                        if (snapshot.resetting()) {
                            sendPrefixed(sender, "resource.command.list.entry_resetting", Map.of());
                        } else {
                            sendPrefixed(
                                    sender,
                                    "resource.command.list.entry_time",
                                    Map.of(
                                            "next_reset", formatDateTime(snapshot.nextResetAt()),
                                            "time_display", formatTimeDisplay(snapshot.nextResetAt(), false)));
                        }
                    }
                    sendRaw(sender, this.plugin.getMessageConfig().getMessage("resource.command.list.footer"));
                }))
                .exceptionally(throwable -> {
                    logCommandFailure("Failed to render /resource", throwable);
                    return null;
                });
    }

    private void showInfo(CommandSender sender, String worldName) {
        ConfigManager.ResourceWorldDefinition definition = getManager().getWorldDefinition(worldName).orElse(null);
        if (definition == null) {
            this.plugin.getMessageConfig().sendMessage(sender, "general.unknown-command");
            return;
        }

        loadSnapshot(worldName, definition)
                .thenCompose(snapshot -> getManager().getLastCompletedResetTime(worldName)
                        .thenApply(lastCompleted -> new ResourceWorldInfoView(snapshot, lastCompleted.orElse(null), definition)))
                .thenAccept(view -> runSync(() -> {
                    sendRaw(sender, this.plugin.getMessageConfig().getMessage("resource.command.info.header"));
                    sendPrefixed(
                            sender,
                            "resource.command.info.title",
                            Map.of("display_name", view.snapshot().displayName()));
                    sendPrefixed(
                            sender,
                            "resource.command.info.dimension",
                            Map.of(
                                    "dimension", view.snapshot().dimensionName(),
                                    "world", view.snapshot().worldName()));
                    sendPrefixed(
                            sender,
                            view.snapshot().resetting()
                                    ? "resource.command.info.status_resetting"
                                    : "resource.command.info.status_active",
                            Map.of());
                    sendPrefixed(
                            sender,
                            "resource.command.info.period",
                            Map.of("period", view.definition().resetIntervalDays()));
                    if (view.lastCompletedResetAt() == null) {
                        sendPrefixed(sender, "resource.command.info.last_reset_never", Map.of());
                    } else {
                        sendPrefixed(
                                sender,
                                "resource.command.info.last_reset",
                                Map.of("date", formatDateTime(view.lastCompletedResetAt())));
                    }
                    sendPrefixed(
                            sender,
                            "resource.command.info.next_reset",
                            Map.of(
                                    "date", view.snapshot().resetting() ? "--/--/-- --:--" : formatDateTime(view.snapshot().nextResetAt()),
                                    "time_display", formatTimeDisplay(view.snapshot().nextResetAt(), view.snapshot().resetting())));
                    sendRaw(sender, this.plugin.getMessageConfig().getMessage("resource.command.info.footer"));
                }))
                .exceptionally(throwable -> {
                    logCommandFailure("Failed to render /resource info for " + worldName, throwable);
                    return null;
                });
    }

    private void showAdminStatus(CommandSender sender) {
        List<ConfigManager.ResourceWorldDefinition> worlds = getManager().getSettings().worlds();
        CompletableFuture<?>[] futures = worlds.stream()
                .map(world -> loadSnapshot(world.name(), world))
                .toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(futures)
                .thenRun(() -> runSync(() -> {
                    sendRaw(sender, this.plugin.getMessageConfig().getMessage("resource.command.admin_status.header"));
                    sendPrefixed(sender, "resource.command.admin_status.title", Map.of());
                    for (CompletableFuture<?> future : futures) {
                        ResourceWorldSnapshot snapshot = (ResourceWorldSnapshot) future.join();
                        sendPrefixed(
                                sender,
                                "resource.command.admin_status.entry",
                                Map.of(
                                        "display_name", snapshot.displayName(),
                                        "world", snapshot.worldName(),
                                        "status_color", snapshot.statusColor(),
                                        "status_name", snapshot.statusName(),
                                        "progress", snapshot.progress()));
                    }
                    sendPrefixed(sender, "resource.command.admin_status.hint", Map.of());
                    sendPrefixed(sender, "resource.command.admin_status.command", Map.of());
                    sendRaw(sender, this.plugin.getMessageConfig().getMessage("resource.command.admin_status.footer"));
                }))
                .exceptionally(throwable -> {
                    logCommandFailure("Failed to render /resource status", throwable);
                    return null;
                });
    }

    private void resetWorld(CommandSender sender, String worldName) {
        getManager().executeReset(worldName)
                .exceptionally(throwable -> {
                    Throwable rootCause = unwrap(throwable);
                    runSync(() -> this.plugin.getMessageConfig().sendMessage(
                            sender,
                            "resource-world.reset-failed-admin",
                            worldName,
                            rootCause.getMessage() == null ? rootCause.toString() : rootCause.getMessage()));
                    logCommandFailure("Failed to execute /resource reset for " + worldName, rootCause);
                    return null;
                });
    }

    private CompletableFuture<ResourceWorldSnapshot> loadSnapshot(
            String worldName,
            ConfigManager.ResourceWorldDefinition definition) {
        CompletableFuture<Optional<ResourceWorldResetRepository.ResourceWorldResetEntry>> latestEntryFuture =
                getManager().getLatestResetEntry(worldName);
        CompletableFuture<Optional<LocalDateTime>> nextResetFuture = getManager().getNextResetTime(worldName);
        return latestEntryFuture.thenCombine(nextResetFuture, (latestEntry, nextReset) -> {
            ResourceWorldResetRepository.ResourceWorldResetEntry entry = latestEntry.orElse(null);
            boolean resetting = getManager().isResetting(worldName)
                    || (entry != null && entry.status() == ResourceWorldResetRepository.ResetStatus.IN_PROGRESS);
            LocalDateTime fallbackNextReset = calculateNextReset(definition, LocalDateTime.now());
            LocalDateTime resolvedNextReset = nextReset.orElseGet(() -> {
                if (entry != null) {
                    return entry.nextResetAt();
                }
                return fallbackNextReset;
            });
            String progress = entry == null ? ResourceWorldResetRepository.ResetStatus.SCHEDULED.databaseValue()
                    : entry.status().databaseValue();
            String statusColor = resolveStatusColor(entry, resetting);
            String statusName = resolveStatusName(entry, resetting);
            return new ResourceWorldSnapshot(
                    worldName,
                    getManager().getDisplayName(worldName),
                    getManager().getDimensionDisplayName(definition.dimension()),
                    resetting,
                    resolvedNextReset,
                    progress,
                    statusColor,
                    statusName);
        });
    }

    private String resolveStatusColor(
            ResourceWorldResetRepository.ResourceWorldResetEntry entry,
            boolean resetting) {
        if (resetting) {
            return "&c";
        }
        if (entry != null && entry.status() == ResourceWorldResetRepository.ResetStatus.FAILED) {
            return "&4";
        }
        return "&a";
    }

    private String resolveStatusName(
            ResourceWorldResetRepository.ResourceWorldResetEntry entry,
            boolean resetting) {
        if (resetting) {
            return "リセット中";
        }
        if (entry != null && entry.status() == ResourceWorldResetRepository.ResetStatus.FAILED) {
            return "失敗";
        }
        return "通常";
    }

    private String formatDateTime(LocalDateTime value) {
        return DATE_TIME_FORMATTER.format(value);
    }

    private String formatTimeDisplay(LocalDateTime nextResetAt, boolean resetting) {
        if (resetting) {
            return "&cリセット中";
        }
        long secondsRemaining = Math.max(0L, Duration.between(LocalDateTime.now(), nextResetAt).getSeconds());
        String color = secondsRemaining < 86400L ? "&e" : "&a";
        return color + "残り" + getManager().formatCountdown(secondsRemaining);
    }

    private LocalDateTime calculateNextReset(
            ConfigManager.ResourceWorldDefinition definition,
            LocalDateTime currentTime) {
        LocalDateTime nextResetTime = definition.resetStartDate();
        while (!nextResetTime.isAfter(currentTime)) {
            nextResetTime = nextResetTime.plusDays(definition.resetIntervalDays());
        }
        return nextResetTime;
    }

    private List<String> resourceWorldNames() {
        return getManager().getSettings().worlds().stream()
                .map(ConfigManager.ResourceWorldDefinition::name)
                .toList();
    }

    private ResourceWorldManager getManager() {
        return Objects.requireNonNull(this.plugin.getResourceWorldManager(), "resourceWorldManager");
    }

    private String resolveWorldName(String candidate) {
        return resourceWorldNames().stream()
                .filter(worldName -> worldName.equalsIgnoreCase(candidate))
                .findFirst()
                .orElse(null);
    }

    private void sendRaw(CommandSender sender, String message) {
        sender.sendMessage(message);
    }

    private void sendPrefixed(CommandSender sender, String key, Map<String, ?> placeholders) {
        sender.sendMessage(this.plugin.getMessageConfig().getMessage("resource.prefix")
                + this.plugin.getMessageConfig().getMessage(key, placeholders));
    }

    private void logCommandFailure(String message, Throwable throwable) {
        this.plugin.getLogger().log(Level.SEVERE, message, throwable);
    }

    private void runSync(Runnable runnable) {
        Bukkit.getScheduler().runTask(this.plugin, runnable);
    }

    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT);
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

    private record ResourceWorldSnapshot(
            String worldName,
            String displayName,
            String dimensionName,
            boolean resetting,
            LocalDateTime nextResetAt,
            String progress,
            String statusColor,
            String statusName) {
    }

    private record ResourceWorldInfoView(
            ResourceWorldSnapshot snapshot,
            LocalDateTime lastCompletedResetAt,
            ConfigManager.ResourceWorldDefinition definition) {
    }
}
