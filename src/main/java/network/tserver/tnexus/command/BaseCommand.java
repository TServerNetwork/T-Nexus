package network.tserver.tnexus.command;

import java.util.List;
import network.tserver.tnexus.TNexus;
import org.bukkit.command.CommandSender;

/**
 * Base type for T-Nexus subcommands.
 */
public abstract class BaseCommand {

    private final TNexus plugin;

    /**
     * Creates a new subcommand.
     *
     * @param plugin plugin instance
     */
    protected BaseCommand(TNexus plugin) {
        this.plugin = plugin;
    }

    /**
     * Returns the primary subcommand name.
     *
     * @return subcommand name
     */
    public abstract String getName();

    /**
     * Returns the required permission, or {@code null} when unrestricted.
     *
     * @return permission node or {@code null}
     */
    public abstract String getPermission();

    /**
     * Returns whether this subcommand can only be executed by players.
     *
     * @return {@code true} when player-only
     */
    public abstract boolean isPlayerOnly();

    /**
     * Executes the subcommand.
     *
     * @param sender command sender
     * @param args command arguments after the subcommand token
     * @return {@code true} when handled
     */
    public abstract boolean execute(CommandSender sender, String[] args);

    /**
     * Returns tab-completion candidates for the current argument state.
     *
     * @param sender command sender
     * @param args command arguments after the subcommand token
     * @return completion candidates
     */
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        return List.of();
    }

    /**
     * Returns the owning plugin.
     *
     * @return plugin instance
     */
    protected final TNexus getPlugin() {
        return this.plugin;
    }
}
