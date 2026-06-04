package network.tserver.tnexus.command.subcommand;

import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.command.BaseCommand;
import network.tserver.tnexus.gui.MainMenuGui;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Opens the main menu GUI.
 */
public final class MenuCommand extends BaseCommand {

    private static final String PERMISSION = "tnexus.use";

    /**
     * Creates a new menu command.
     *
     * @param plugin plugin instance
     */
    public MenuCommand(TNexus plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "menu";
    }

    @Override
    public String getPermission() {
        return PERMISSION;
    }

    @Override
    public boolean isPlayerOnly() {
        return true;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        Player player = (Player) sender;
        new MainMenuGui(getPlugin(), player).open();
        return true;
    }
}
