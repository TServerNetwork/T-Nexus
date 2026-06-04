package network.tserver.tnexus.gui;

import java.util.List;
import network.tserver.tnexus.TNexus;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Temporary main menu GUI stub used by the /tnexus command.
 */
public final class MainMenuGui extends BaseGui {

    private static final int ROWS = 3;
    private static final int CENTER_SLOT = 13;

    /**
     * Creates the main menu GUI for a player.
     *
     * @param plugin plugin instance
     * @param player target player
     */
    public MainMenuGui(TNexus plugin, Player player) {
        super(plugin, player, plugin.getConfigManager().getGuiSettings().mainMenuTitle(), ROWS);
    }

    @Override
    protected void buildContent() {
        setItem(
                CENTER_SLOT,
                createItem(
                        Material.NETHER_STAR,
                        getPlugin().getMessageConfig().getMessage("gui.main-menu.stub.name"),
                        List.of(getPlugin().getMessageConfig().getMessage("gui.main-menu.stub.lore"))),
                null);
    }
}
