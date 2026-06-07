package network.tserver.tnexus.listener;

import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.TestPluginSupport;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.inventory.SimpleInventoryViewMock;
import org.mockbukkit.mockbukkit.inventory.WorkbenchInventoryMock;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerCraftStatsListenerTest {

    private ServerMock server;

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void shouldRecordCraftedResultAmount() {
        TNexus plugin = loadPlugin();
        PlayerMock player = this.server.addPlayer("Crafter");

        this.server.getPluginManager().callEvent(createCraftEvent(player, Material.STICK, 4));
        plugin.getPlayerStatsManager().flushPendingCraftStats().join();

        assertEquals(4, readCraftMaterialCount(plugin, player, Material.STICK));
    }

    @Test
    void shouldIgnoreCancelledCraftEvents() {
        TNexus plugin = loadPlugin();
        PlayerMock player = this.server.addPlayer("CancelledCrafter");
        CraftItemEvent event = createCraftEvent(player, Material.CHEST, 1);
        event.setCancelled(true);

        this.server.getPluginManager().callEvent(event);
        plugin.getPlayerStatsManager().flushPendingCraftStats().join();

        assertEquals(0, readCraftMaterialCount(plugin, player, Material.CHEST));
    }

    @Test
    void shouldIgnoreCreativeModeCraftEvents() {
        TNexus plugin = loadPlugin();
        PlayerMock player = this.server.addPlayer("CreativeCrafter");
        player.setGameMode(GameMode.CREATIVE);

        this.server.getPluginManager().callEvent(createCraftEvent(player, Material.TORCH, 4));
        plugin.getPlayerStatsManager().flushPendingCraftStats().join();

        assertEquals(0, readCraftMaterialCount(plugin, player, Material.TORCH));
    }

    private TNexus loadPlugin() {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        return TestPluginSupport.loadPlugin(this.server, TestPluginSupport.H2TestTNexus.class);
    }

    private CraftItemEvent createCraftEvent(PlayerMock player, Material resultMaterial, int amount) {
        ItemStack result = new ItemStack(resultMaterial, amount);
        ShapedRecipe recipe = new ShapedRecipe(
                NamespacedKey.minecraft(resultMaterial.name().toLowerCase(java.util.Locale.ROOT)),
                result);
        recipe.shape("A");
        recipe.setIngredient('A', Material.STICK);

        WorkbenchInventoryMock topInventory = new WorkbenchInventoryMock(null);
        topInventory.setResult(result);
        InventoryView view = new SimpleInventoryViewMock(
                player,
                topInventory,
                player.getInventory(),
                InventoryType.WORKBENCH);
        return new CraftItemEvent(
                recipe,
                view,
                InventoryType.SlotType.RESULT,
                0,
                ClickType.LEFT,
                InventoryAction.PICKUP_ALL);
    }

    private int readCraftMaterialCount(TNexus plugin, PlayerMock player, Material material) {
        return plugin.getDatabaseManager().queryAsync(() -> {
            try (var connection = plugin.getDatabaseManager().getConnection();
                 var statement = connection.prepareStatement(
                         "SELECT count FROM tnexus_craft_stats WHERE player_uuid = ? AND material = ?")) {
                statement.setString(1, player.getUniqueId().toString());
                statement.setString(2, material.name());
                try (var resultSet = statement.executeQuery()) {
                    return resultSet.next() ? resultSet.getInt("count") : 0;
                }
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }).join();
    }
}
