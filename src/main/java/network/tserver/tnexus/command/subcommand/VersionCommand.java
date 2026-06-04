package network.tserver.tnexus.command.subcommand;

import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.command.BaseCommand;
import org.bukkit.command.CommandSender;

/**
 * Displays the current plugin version.
 */
public final class VersionCommand extends BaseCommand {

    private static final String PERMISSION = "tnexus.use";

    /**
     * Creates a new version command.
     *
     * @param plugin plugin instance
     */
    public VersionCommand(TNexus plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "version";
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
        getPlugin().getMessageConfig().sendMessage(
                sender,
                "command.version.info",
                getPlugin().getPluginMeta().getVersion());
        return true;
    }
}
