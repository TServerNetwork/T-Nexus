package network.tserver.tnexus.gui;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.config.ConfigManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Tracks open GUIs and routes shared inventory events.
 */
public final class GuiManager implements Listener {

    private final TNexus plugin;
    private final Map<UUID, BaseGui> openGuis;
    private final Map<UUID, Long> clickCooldowns;
    private final int clickCooldownMillis;

    /**
     * Creates a new GUI manager and registers event listeners.
     *
     * @param plugin plugin instance
     */
    public GuiManager(TNexus plugin) {
        this.plugin = plugin;
        this.openGuis = new ConcurrentHashMap<>();
        this.clickCooldowns = new ConcurrentHashMap<>();
        ConfigManager.GuiSettings guiSettings = plugin.getConfigManager().getGuiSettings();
        this.clickCooldownMillis = Math.max(0, guiSettings.clickCooldownMillis());
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Opens and tracks the provided GUI for the player.
     *
     * @param player target player
     * @param gui GUI to open
     */
    public void openGui(Player player, BaseGui gui) {
        this.openGuis.put(player.getUniqueId(), gui);
        player.openInventory(gui.getInventory());
    }

    /**
     * Returns the tracked GUI for the player.
     *
     * @param player target player
     * @return tracked GUI or {@code null}
     */
    public BaseGui getOpenGui(Player player) {
        return this.openGuis.get(player.getUniqueId());
    }

    /**
     * Returns whether the player currently has a tracked GUI open.
     *
     * @param player target player
     * @return {@code true} when tracked
     */
    public boolean hasOpenGui(Player player) {
        return this.openGuis.containsKey(player.getUniqueId());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        BaseGui gui = this.openGuis.get(player.getUniqueId());
        if (gui == null || !gui.isView(event.getView())) {
            return;
        }

        event.setCancelled(true);

        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            return;
        }

        if (!gui.hasClickHandler(event.getRawSlot())) {
            return;
        }

        if (isCoolingDown(player.getUniqueId())) {
            return;
        }

        this.clickCooldowns.put(player.getUniqueId(), System.currentTimeMillis());
        gui.handleClick(event);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        BaseGui gui = this.openGuis.get(player.getUniqueId());
        if (gui == null || !gui.isView(event.getView())) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        BaseGui gui = this.openGuis.get(player.getUniqueId());
        if (gui == null || !gui.isInventory(event.getInventory())) {
            return;
        }

        this.openGuis.remove(player.getUniqueId());
        this.clickCooldowns.remove(player.getUniqueId());
        gui.onClose();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        this.openGuis.remove(playerId);
        this.clickCooldowns.remove(playerId);
    }

    private boolean isCoolingDown(UUID playerId) {
        Long lastClickAt = this.clickCooldowns.get(playerId);
        if (lastClickAt == null) {
            return false;
        }
        return System.currentTimeMillis() - lastClickAt < this.clickCooldownMillis;
    }
}
