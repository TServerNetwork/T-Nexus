package network.tserver.tnexus.config;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.TestPluginSupport;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageConfigTest {

    private ServerMock server;

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void shouldTranslateMessagesAndReplacePlaceholders() throws IOException {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        TNexus plugin = TestPluginSupport.loadPlugin(this.server, TestPluginSupport.TestTNexus.class);
        MessageConfig messageConfig = plugin.getMessageConfig();

        File localeFile = plugin.getDataFolder().toPath().resolve("lang").resolve("ja_JP.yml").toFile();
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(localeFile);
        configuration.set("general.balance", "&e残高: {0}{1}");
        configuration.save(localeFile);

        messageConfig.reload();

        assertEquals("§e残高: $2500", messageConfig.getMessage("general.balance", "$", 2500));
        assertEquals("missing.key", messageConfig.getMessage("missing.key"));
        assertEquals("§e次のページ", messageConfig.getMessage("gui.navigation.next.enabled.name"));
    }

    @Test
    void shouldFallbackToBundledDefaultsWhenDiskLocaleMissesKeys() throws IOException {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        TNexus plugin = TestPluginSupport.loadPlugin(this.server, TestPluginSupport.TestTNexus.class);
        MessageConfig messageConfig = plugin.getMessageConfig();

        File localeFile = plugin.getDataFolder().toPath().resolve("lang").resolve("ja_JP.yml").toFile();
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(localeFile);
        configuration.set("economy.balance", null);
        configuration.set("economy.pay", null);
        configuration.save(localeFile);

        messageConfig.reload();

        assertEquals("§7所持金: §a1234", messageConfig.getMessage("economy.balance.self", 1234));
        assertEquals("§c使い方: /pay <player>", messageConfig.getMessage("economy.pay.usage"));
    }

    @Test
    void shouldSendPrefixedMessagesToPlayers() {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        TNexus plugin = TestPluginSupport.loadPlugin(this.server, TestPluginSupport.TestTNexus.class);
        PlayerMock player = this.server.addPlayer();

        plugin.getMessageConfig().sendMessage(player, "general.reload-success");

        String message = player.nextMessage();
        assertEquals("§8[§6T-Nexus§8] §a設定ファイルとメッセージを再読み込みしました。", message);
        assertTrue(message.startsWith("§8[§6T-Nexus§8] "));
    }
    @Test
    void shouldReplaceNamedPlaceholders() throws IOException {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        TNexus plugin = TestPluginSupport.loadPlugin(this.server, TestPluginSupport.TestTNexus.class);
        MessageConfig messageConfig = plugin.getMessageConfig();

        File localeFile = plugin.getDataFolder().toPath().resolve("lang").resolve("ja_JP.yml").toFile();
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(localeFile);
        configuration.set("resource.command.list.entry", "&e> &f{display_name} &7({world})");
        configuration.save(localeFile);

        messageConfig.reload();

        String message = messageConfig.getMessage(
                "resource.command.list.entry",
                Map.of("display_name", "通常世界", "world", "resource"));
        assertTrue(message.contains("通常世界"));
        assertTrue(message.contains("(resource)"));
    }
}
