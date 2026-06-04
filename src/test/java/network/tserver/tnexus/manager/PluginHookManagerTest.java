package network.tserver.tnexus.manager;

import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.TestPluginSupport;
import network.tserver.tnexus.manager.hook.PluginHook;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginHookManagerTest {

    private ServerMock server;

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void shouldHookRegisteredPluginsAndExposeTypedApis() {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        TNexus plugin = TestPluginSupport.loadPlugin(this.server, TestPluginSupport.TestTNexus.class);
        TestPluginSupport.registerPlugin(this.server, "RequiredPlugin");
        TestPluginSupport.registerPlugin(this.server, "OptionalPlugin");

        PluginHookManager hookManager = new PluginHookManager(plugin, plugin.getMessageConfig());
        TestApi api = new TestApi("ready");
        hookManager.register(new TestHook("RequiredPlugin", true, api));
        hookManager.register(new TestHook("OptionalPlugin", false, new TestApi("optional")));

        assertTrue(hookManager.hookAll());
        assertTrue(hookManager.isFullyReady());
        assertSame(api, hookManager.getApi(TestApi.class));
        assertNull(hookManager.getApi(Plugin.class));
    }

    @Test
    void shouldReturnFalseWhenARequiredHookIsMissing() {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        TNexus plugin = TestPluginSupport.loadPlugin(this.server, TestPluginSupport.TestTNexus.class);
        TestPluginSupport.registerPlugin(this.server, "OptionalPlugin");

        PluginHookManager hookManager = new PluginHookManager(plugin, plugin.getMessageConfig());
        hookManager.register(new TestHook("RequiredPlugin", true, new TestApi("required")));
        TestApi optionalApi = new TestApi("optional");
        hookManager.register(new TestHook("OptionalPlugin", false, optionalApi));

        assertFalse(hookManager.hookAll());
        assertFalse(hookManager.isFullyReady());
        assertSame(optionalApi, hookManager.getApi(TestApi.class));
    }
    private record TestApi(String state) {
    }

    private static final class TestHook implements PluginHook<TestApi> {

        private final String pluginName;
        private final boolean required;
        private final TestApi api;
        private TestApi hookedApi;

        private TestHook(String pluginName, boolean required, TestApi api) {
            this.pluginName = pluginName;
            this.required = required;
            this.api = api;
        }

        @Override
        public String getPluginName() {
            return this.pluginName;
        }

        @Override
        public boolean isRequired() {
            return this.required;
        }

        @Override
        public TestApi getApi() {
            return this.hookedApi;
        }

        @Override
        public boolean hook(Plugin plugin) {
            if (plugin == null || !this.pluginName.equals(plugin.getName())) {
                return false;
            }
            this.hookedApi = this.api;
            return true;
        }
    }
}
