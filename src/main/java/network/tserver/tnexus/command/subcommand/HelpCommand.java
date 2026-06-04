package network.tserver.tnexus.command.subcommand;

import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.command.BaseCommand;
import org.bukkit.command.CommandSender;

/**
 * Displays the main command help output.
 */
public final class HelpCommand extends BaseCommand {

    private static final String PERMISSION = "tnexus.use";

    /**
     * Creates a new help command.
     *
     * @param plugin plugin instance
     */
    public HelpCommand(TNexus plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "help";
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
        getPlugin().getMessageConfig().sendMessage(sender, "command.help.header");
        getPlugin().getMessageConfig().sendMessage(sender, "command.help.menu");
        getPlugin().getMessageConfig().sendMessage(sender, "command.help.help");
        getPlugin().getMessageConfig().sendMessage(sender, "command.help.reload");
        getPlugin().getMessageConfig().sendMessage(sender, "command.help.version");
        return true;
    }
}
