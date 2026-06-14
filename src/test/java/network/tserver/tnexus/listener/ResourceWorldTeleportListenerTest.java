package network.tserver.tnexus.listener;

import java.util.Map;
import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.TestPluginSupport;
import network.tserver.tnexus.manager.MultiverseWorldService;
import network.tserver.tnexus.database.repository.ResourceWorldResetRepository;
import network.tserver.tnexus.manager.ResourceWorldManager;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceWorldTeleportListenerTest {

    private ServerMock server;

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void shouldBlockTeleportIntoResettingResourceWorldForPlayersAndAdmins() {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        World lobby = this.server.addSimpleWorld("lobby");
        World resourceWorld = this.server.addSimpleWorld("resource");
        TNexus plugin = TestPluginSupport.loadPlugin(this.server, TeleportListenerTestTNexus.class);
        new ResourceWorldTeleportListener(plugin);

        PlayerMock player = this.server.addPlayer("Player");
        player.teleport(lobby.getSpawnLocation());
        PlayerMock admin = this.server.addPlayer("Admin");
        admin.teleport(lobby.getSpawnLocation());
        admin.addAttachment(plugin, "tnexus.admin", true);

        plugin.getResourceWorldManager().markResetting(resourceWorld.getName());

        PlayerTeleportEvent playerEvent = new PlayerTeleportEvent(
                player,
                player.getLocation(),
                resourceWorld.getSpawnLocation(),
                PlayerTeleportEvent.TeleportCause.COMMAND);
        PlayerTeleportEvent adminEvent = new PlayerTeleportEvent(
                admin,
                admin.getLocation(),
                resourceWorld.getSpawnLocation(),
                PlayerTeleportEvent.TeleportCause.PLUGIN);

        this.server.getPluginManager().callEvent(playerEvent);
        this.server.getPluginManager().callEvent(adminEvent);

        assertTrue(playerEvent.isCancelled());
        assertTrue(adminEvent.isCancelled());

        String playerMessage = player.nextMessage();
        String adminMessage = admin.nextMessage();
        assertNotNull(playerMessage);
        assertNotNull(adminMessage);
        assertTrue(playerMessage.contains("resource"));
        assertTrue(playerMessage.contains("リセット中"));
        assertTrue(adminMessage.contains("resource"));
        assertTrue(adminMessage.contains("リセット中"));
    }

    @Test
    void shouldAllowTeleportAfterResetMarkerClears() {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        World lobby = this.server.addSimpleWorld("lobby");
        World resourceWorld = this.server.addSimpleWorld("resource");
        TNexus plugin = TestPluginSupport.loadPlugin(this.server, TeleportListenerTestTNexus.class);
        new ResourceWorldTeleportListener(plugin);

        PlayerMock player = this.server.addPlayer("Player");
        player.teleport(new Location(lobby, 0.0D, 64.0D, 0.0D));
        plugin.getResourceWorldManager().markResetting(resourceWorld.getName());
        plugin.getResourceWorldManager().clearResetting(resourceWorld.getName());

        PlayerTeleportEvent event = new PlayerTeleportEvent(
                player,
                player.getLocation(),
                resourceWorld.getSpawnLocation(),
                PlayerTeleportEvent.TeleportCause.NETHER_PORTAL);

        this.server.getPluginManager().callEvent(event);

        assertFalse(event.isCancelled());
        assertNotNull(event.getTo());
        assertTrue(player.nextMessage() == null);
    }

    public static class TeleportListenerTestTNexus extends TestPluginSupport.H2TestTNexus {

        private ResourceWorldManager testResourceWorldManager;

        @Override
        protected void initializeResourceWorldManager() {
            getConfigManager().getConfiguration().set("resource-world.worlds", java.util.List.of(Map.of(
                    "name", "resource",
                    "dimension", "NORMAL",
                    "reset-interval-days", 1,
                    "reset-start-date", "2026-06-14T09:00:10")));
            MultiverseWorldService mvWorldManager = getPluginHookManager().getApi(MultiverseWorldService.class);
            this.testResourceWorldManager = new ResourceWorldManager(
                    this,
                    new ResourceWorldResetRepository(getDatabaseManager()),
                    mvWorldManager);
            this.testResourceWorldManager.onEnable().join();
        }

        @Override
        public ResourceWorldManager getResourceWorldManager() {
            return this.testResourceWorldManager;
        }
    }
}
