package network.tserver.tnexus.listener;

import com.onarandombox.MultiverseCore.api.MVWorldManager;
import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.TestPluginSupport;
import network.tserver.tnexus.database.repository.ResourceWorldResetRepository;
import network.tserver.tnexus.manager.ResourceWorldManager;
import org.bukkit.World;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceWorldSeedCommandListenerTest {

    private ServerMock server;

    @AfterEach
    void tearDown() {
        SeedListenerTestTNexus.showRealSeedToAdmin = false;
        MockBukkit.unmock();
    }

    @Test
    void shouldObfuscateSeedForPlayersInResourceWorlds() {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        this.server.addSimpleWorld("lobby");
        World resourceWorld = this.server.addSimpleWorld("resource");
        SeedListenerTestTNexus.showRealSeedToAdmin = false;
        TNexus plugin = TestPluginSupport.loadPlugin(this.server, SeedListenerTestTNexus.class);
        new ResourceWorldSeedCommandListener(plugin);
        PlayerMock player = this.server.addPlayer();
        player.teleport(resourceWorld.getSpawnLocation());

        PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(player, "/seed");
        this.server.getPluginManager().callEvent(event);

        assertTrue(event.isCancelled());
        String message = player.nextMessage();
        assertNotNull(message);
        assertTrue(message.contains("Seed: [" + plugin.getResourceWorldManager().obfuscateSeed(resourceWorld.getSeed()) + "]"));
    }

    @Test
    void shouldShowRealSeedToAdminsWhenEnabled() {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        this.server.addSimpleWorld("lobby");
        World resourceWorld = this.server.addSimpleWorld("resource");
        SeedListenerTestTNexus.showRealSeedToAdmin = true;
        TNexus plugin = TestPluginSupport.loadPlugin(this.server, SeedListenerTestTNexus.class);
        new ResourceWorldSeedCommandListener(plugin);
        PlayerMock admin = this.server.addPlayer();
        admin.teleport(resourceWorld.getSpawnLocation());
        admin.addAttachment(plugin, "tnexus.admin", true);

        PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(admin, "/seed");
        this.server.getPluginManager().callEvent(event);

        assertTrue(event.isCancelled());
        String message = admin.nextMessage();
        assertNotNull(message);
        assertTrue(message.contains("Seed: [" + resourceWorld.getSeed() + "]"));
    }

    @Test
    void shouldLeaveSeedCommandUntouchedOutsideResourceWorlds() {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        World lobby = this.server.addSimpleWorld("lobby");
        SeedListenerTestTNexus.showRealSeedToAdmin = false;
        TNexus plugin = TestPluginSupport.loadPlugin(this.server, SeedListenerTestTNexus.class);
        new ResourceWorldSeedCommandListener(plugin);
        PlayerMock player = this.server.addPlayer();
        player.teleport(lobby.getSpawnLocation());

        PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(player, "/seed");
        this.server.getPluginManager().callEvent(event);

        assertFalse(event.isCancelled());
    }

    public static class SeedListenerTestTNexus extends TestPluginSupport.H2TestTNexus {

        private static boolean showRealSeedToAdmin;

        private ResourceWorldManager testResourceWorldManager;

        @Override
        protected void initializeResourceWorldManager() {
            getConfigManager().getConfiguration().set("resource-world.show-real-seed-to-admin", showRealSeedToAdmin);
            getConfigManager().getConfiguration().set("resource-world.worlds", java.util.List.of(java.util.Map.of(
                    "name", "resource",
                    "dimension", "NORMAL",
                    "reset-interval-days", 1,
                    "reset-start-date", "2026-06-14T09:00:10")));
            MVWorldManager mvWorldManager = getPluginHookManager().getApi(MVWorldManager.class);
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
