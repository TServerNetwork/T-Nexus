package network.tserver.tnexus.config;

import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.TestPluginSupport;
import org.bukkit.configuration.ConfigurationSection;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigManagerTest {

    private ServerMock server;

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void shouldCreateDefaultConfigAndExposeTypedValues() {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        TNexus plugin = TestPluginSupport.loadPlugin(this.server, TestPluginSupport.TestTNexus.class);
        ConfigManager configManager = plugin.getConfigManager();

        assertTrue(plugin.getDataFolder().toPath().resolve("config.yml").toFile().exists());
        assertEquals("localhost", configManager.getString("tnexus.database.host"));
        assertEquals(3306, configManager.getInt("tnexus.database.port"));
        assertEquals(1000.0D, configManager.getDouble("tnexus.economy.starting-balance"));
        assertTrue(configManager.getBoolean("tnexus.missing-flag", true));
        assertEquals("tnexus_", configManager.getDatabaseSettings().tablePrefix());
        assertEquals("BLACK_STAINED_GLASS_PANE", configManager.getGuiSettings().headerItem());
        assertEquals("GRAY_STAINED_GLASS_PANE", configManager.getGuiSettings().borderItem());
        assertEquals("REPLACE_PREVIOUS_ENABLED_TEXTURE",
                configManager.getGuiSettings().pagerSettings().previous().enabledTexture());
        assertEquals(50, configManager.getGuiSettings().currentLocationSlot());
        assertTrue(plugin.getConfig().getStringList("tnexus.shop.banned-materials")
                .contains(configManager.getString("tnexus.shop.link-tool.material")));

        ConfigurationSection section = configManager.getSection("tnexus.database");
        assertNotNull(section);
        assertEquals("tnexus_", section.getString("table-prefix"));
    }
}
