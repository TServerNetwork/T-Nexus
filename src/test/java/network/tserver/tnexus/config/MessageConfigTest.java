package network.tserver.tnexus.config;

import java.io.File;
import java.io.IOException;
import network.tserver.tnexus.TNexus;
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
        this.server = MockBukkit.mock();
        TNexus plugin = MockBukkit.load(TNexus.class);
        MessageConfig messageConfig = plugin.getMessageConfig();

        File localeFile = plugin.getDataFolder().toPath().resolve("lang").resolve("ja_JP.yml").toFile();
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(localeFile);
        configuration.set("general.balance", "&e残高: {0}{1}");
        configuration.save(localeFile);

        messageConfig.reload();

        assertEquals("§e残高: ¥2500", messageConfig.getMessage("general.balance", "¥", 2500));
        assertEquals("missing.key", messageConfig.getMessage("missing.key"));
        assertEquals("§e次のページ", messageConfig.getMessage("gui.navigation.next.enabled.name"));
    }

    @Test
    void shouldSendPrefixedMessagesToPlayers() {
        this.server = MockBukkit.mock();
        TNexus plugin = MockBukkit.load(TNexus.class);
        PlayerMock player = this.server.addPlayer();

        plugin.getMessageConfig().sendMessage(player, "general.reload-success");

        String message = player.nextMessage();
        assertEquals("§8[§6T-Nexus§8] §a設定をリロードしました", message);
        assertTrue(message.startsWith("§8[§6T-Nexus§8] "));
    }
}
