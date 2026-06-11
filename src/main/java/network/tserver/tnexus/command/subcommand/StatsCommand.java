package network.tserver.tnexus.command.subcommand;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.command.BaseCommand;
import network.tserver.tnexus.manager.PlayerStatsManager.ResetResult;
import network.tserver.tnexus.util.PlayerStatsResetTarget;
import network.tserver.tnexus.util.TabCompleterUtil;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Handles console-only stats reset administration.
 */
public final class StatsCommand extends BaseCommand {

    private static final String PERMISSION = "tnexus.stats.admin";
    private static final String RESET_ARGUMENT = "reset";
    private static final String ALL_ARGUMENT = "all";
    private static final String CONFIRM_ARGUMENT = "CONFIRM";

    /**
     * Creates a new stats reset subcommand.
     *
     * @param plugin plugin instance
     */
    public StatsCommand(TNexus plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "stats";
    }

    @Override
    public String getPermission() {
        return PERMISSION;
    }

    @Override
    public boolean isPlayerOnly() {
        return false;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (sender instanceof Player) {
            getPlugin().getMessageConfig().sendMessage(sender, "general.console-only");
            return true;
        }
        if (args.length < 2 || !RESET_ARGUMENT.equalsIgnoreCase(args[0])) {
            getPlugin().getMessageConfig().sendMessage(sender, "stats.reset.usage");
            return true;
        }
        if (ALL_ARGUMENT.equalsIgnoreCase(args[1])) {
            handleResetAll(sender, args);
            return true;
        }
        handleResetPlayer(sender, args);
        return true;
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        if (args.length <= 1) {
            return TabCompleterUtil.filter(List.of(RESET_ARGUMENT), args.length == 0 ? "" : args[0]);
        }
        if (!RESET_ARGUMENT.equalsIgnoreCase(args[0])) {
            return List.of();
        }
        if (args.length == 2) {
            String input = args[1];
            List<String> completions = new ArrayList<>();
            completions.addAll(TabCompleterUtil.filter(List.of(ALL_ARGUMENT), input));
            completions.addAll(TabCompleterUtil.filterPlayers(input));
            return completions;
        }
        if (args.length == 3 && ALL_ARGUMENT.equalsIgnoreCase(args[1])) {
            return TabCompleterUtil.filter(List.of(CONFIRM_ARGUMENT), args[2]);
        }
        return List.of();
    }

    private void handleResetAll(CommandSender sender, String[] args) {
        if (args.length == 2) {
            getPlugin().getMessageConfig().sendMessage(sender, "stats.reset.confirm-all");
            return;
        }
        if (args.length != 3 || !CONFIRM_ARGUMENT.equalsIgnoreCase(args[2])) {
            getPlugin().getMessageConfig().sendMessage(sender, "stats.reset.usage");
            return;
        }

        completeReset(
                sender,
                getPlugin().getPlayerStatsManager().resetAllPlayersStats(),
                result -> getPlugin().getMessageConfig().sendMessage(
                        sender,
                        "stats.reset.success-all",
                        result.affectedPlayers()));
    }

    private void handleResetPlayer(CommandSender sender, String[] args) {
        OfflinePlayer target = resolveTarget(args[1]);
        if (target == null) {
            getPlugin().getMessageConfig().sendMessage(sender, "stats.command.player-not-found", args[1]);
            return;
        }

        if (args.length == 2) {
            completeReset(
                    sender,
                    getPlugin().getPlayerStatsManager().resetAllStatsForPlayer(target.getUniqueId()),
                    result -> getPlugin().getMessageConfig().sendMessage(
                            sender,
                            "stats.reset.success-player",
                            resolveTargetName(target)));
            return;
        }
        if (args.length != 3) {
            getPlugin().getMessageConfig().sendMessage(sender, "stats.reset.usage");
            return;
        }

        PlayerStatsResetTarget resetTarget = PlayerStatsResetTarget.parse(args[2]);
        if (resetTarget == null) {
            getPlugin().getMessageConfig().sendMessage(sender, "stats.reset.invalid-key", args[2]);
            return;
        }

        completeReset(
                sender,
                getPlugin().getPlayerStatsManager().resetSpecificStat(target.getUniqueId(), resetTarget),
                result -> {
                    if (!result.success()) {
                        getPlugin().getMessageConfig().sendMessage(sender, "stats.reset.invalid-key", args[2]);
                        return;
                    }
                    getPlugin().getMessageConfig().sendMessage(
                            sender,
                            "stats.reset.success-key",
                            resolveTargetName(target),
                            result.canonicalKey());
                });
    }

    private void completeReset(
            CommandSender sender,
            CompletableFuture<ResetResult> future,
            java.util.function.Consumer<ResetResult> successHandler) {
        future.whenComplete((result, throwable) -> getPlugin().getServer().getScheduler().runTask(getPlugin(), () -> {
            if (throwable != null) {
                getPlugin().getLogger().log(Level.SEVERE, "Failed to reset player stats.", throwable);
                getPlugin().getMessageConfig().sendMessage(sender, "stats.reset.failed");
                return;
            }
            successHandler.accept(Objects.requireNonNull(result, "result"));
        }));
    }

    private @Nullable OfflinePlayer resolveTarget(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }

        org.bukkit.entity.Player onlineTarget = getPlugin().getServer().getPlayerExact(name);
        if (onlineTarget != null) {
            return onlineTarget;
        }

        OfflinePlayer offlineTarget = getPlugin().getServer().getOfflinePlayer(name);
        if (offlineTarget.getName() == null || offlineTarget.getName().isBlank()) {
            return null;
        }
        return offlineTarget;
    }

    private String resolveTargetName(OfflinePlayer target) {
        return target.getName() == null || target.getName().isBlank()
                ? target.getUniqueId().toString()
                : target.getName();
    }
}
