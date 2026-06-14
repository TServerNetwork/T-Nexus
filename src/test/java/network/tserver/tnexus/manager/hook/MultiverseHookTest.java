package network.tserver.tnexus.manager.hook;

import network.tserver.tnexus.TestPluginSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiverseHookTest {

    private ServerMock server;

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void shouldHookMultiverseCoreFiveApi() {
        this.server = MockBukkit.mock();
        TestPluginSupport.registerMultiversePlugin(this.server);
        org.bukkit.plugin.Plugin plugin = this.server.getPluginManager().getPlugin("Multiverse-Core");
        MultiverseHook hook = new MultiverseHook();

        assertNotNull(plugin);
        assertTrue(hook.hook(plugin));
        assertNotNull(hook.getApi());
        assertTrue(hook.getApi().unloadWorld("resource"));
        assertTrue(hook.getApi().regenerateWorld("resource", "12345"));
        assertTrue(hook.getApi().loadWorld("resource"));
    }

    @Test
    void shouldRejectUnexpectedPluginInstances() {
        this.server = MockBukkit.mock();
        MultiverseHook hook = new MultiverseHook();
        PluginMock wrongPlugin = PluginMock.builder()
                .withPluginName("Wrong")
                .withPluginVersion("1.0.0")
                .build();

        assertFalse(hook.hook(null));
        assertFalse(hook.hook(wrongPlugin));
    }
}
