package network.tserver.tnexus.command.subcommand;

import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.command.BaseCommand;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

/**
 * Reloads configuration and message resources.
 */
public final class ReloadCommand extends BaseCommand {

    private static final String PERMISSION = "tnexus.admin";

    /**
     * Creates a new reload command.
     *
     * @param plugin plugin instance
     */
    public ReloadCommand(TNexus plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "reload";
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
        getPlugin().reloadPlugin().whenComplete((reloaded, throwable) -> Bukkit.getScheduler().runTask(getPlugin(), () -> {
            if (throwable != null || !Boolean.TRUE.equals(reloaded)) {
                getPlugin().getMessageConfig().sendMessage(sender, "general.reload-failed");
                return;
            }
            getPlugin().getMessageConfig().sendMessage(sender, "general.reload-success");
        }));
        return true;
    }
}
