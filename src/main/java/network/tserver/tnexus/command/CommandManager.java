package network.tserver.tnexus.command;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.command.subcommand.HelpCommand;
import network.tserver.tnexus.command.subcommand.MenuCommand;
import network.tserver.tnexus.command.subcommand.ReloadCommand;
import network.tserver.tnexus.command.subcommand.VersionCommand;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Registers and dispatches the main /tnexus command tree.
 */
public final class CommandManager implements TabExecutor {

    private final TNexus plugin;
    private final BaseCommand rootCommand;
    private final Map<String, BaseCommand> subcommands;

    /**
     * Creates a new command manager.
     *
     * @param plugin plugin instance
     */
    public CommandManager(TNexus plugin) {
        this.plugin = plugin;
        this.rootCommand = new MenuCommand(plugin);
        this.subcommands = new LinkedHashMap<>();
        registerSubcommand(new HelpCommand(plugin));
        registerSubcommand(new ReloadCommand(plugin));
        registerSubcommand(new VersionCommand(plugin));
    }

    /**
     * Registers the plugin command executor and tab completer.
     *
     * @throws IllegalStateException when the tnexus command is missing
     */
    public void register() {
        org.bukkit.command.PluginCommand command = this.plugin.getCommand("tnexus");
        if (command == null) {
            throw new IllegalStateException("Command 'tnexus' is not defined in paper-plugin.yml");
        }
        command.setExecutor(this);
        command.setTabCompleter(this);
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args) {
        if (args.length == 0) {
            return executeCommand(sender, this.rootCommand, args);
        }

        BaseCommand subcommand = this.subcommands.get(normalize(args[0]));
        if (subcommand == null) {
            this.plugin.getMessageConfig().sendMessage(sender, "general.unknown-command");
            return true;
        }
        return executeCommand(sender, subcommand, sliceArgs(args));
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args) {
        if (args.length <= 1) {
            String input = args.length == 0 ? "" : normalize(args[0]);
            List<String> completions = new ArrayList<>();
            for (BaseCommand subcommand : this.subcommands.values()) {
                if (!canUse(sender, subcommand)) {
                    continue;
                }
                if (subcommand.getName().startsWith(input)) {
                    completions.add(subcommand.getName());
                }
            }
            return completions;
        }

        BaseCommand subcommand = this.subcommands.get(normalize(args[0]));
        if (subcommand == null || !canUse(sender, subcommand)) {
            return List.of();
        }
        return subcommand.getTabCompletions(sender, sliceArgs(args));
    }

    private void registerSubcommand(BaseCommand subcommand) {
        this.subcommands.put(subcommand.getName(), subcommand);
    }

    private boolean executeCommand(CommandSender sender, BaseCommand command, String[] args) {
        if (!canUse(sender, command)) {
            return true;
        }
        return command.execute(sender, args);
    }

    private boolean canUse(CommandSender sender, BaseCommand command) {
        String permission = command.getPermission();
        if (permission != null && !permission.isBlank() && !sender.hasPermission(permission)) {
            this.plugin.getMessageConfig().sendMessage(sender, "general.no-permission");
            return false;
        }

        if (command.isPlayerOnly() && !(sender instanceof Player)) {
            this.plugin.getMessageConfig().sendMessage(sender, "general.player-only");
            return false;
        }
        return true;
    }

    private String[] sliceArgs(String[] args) {
        if (args.length <= 1) {
            return new String[0];
        }

        String[] sliced = new String[args.length - 1];
        System.arraycopy(args, 1, sliced, 0, sliced.length);
        return sliced;
    }

    private String normalize(String input) {
        return input.toLowerCase(Locale.ROOT);
    }
}
