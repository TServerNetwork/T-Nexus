package network.tserver.tnexus.command;

import java.io.File;
import java.io.IOException;
import java.util.List;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.TestPluginSupport;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandManagerTest {

    private static final Command TEST_COMMAND = new TestCommand();
    private ServerMock server;

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void shouldOpenMainMenuForPlayers() {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        TNexus plugin = TestPluginSupport.loadPlugin(this.server, TestPluginSupport.TestTNexus.class);
        PlayerMock player = this.server.addPlayer();
        player.addAttachment(plugin, "tnexus.use", true);

        assertTrue(this.server.dispatchCommand(player, "tnexus"));
        assertEquals(
                ChatColor.translateAlternateColorCodes('&', plugin.getConfigManager().getGuiSettings().mainMenuTitle()),
                player.getOpenInventory().getTitle());
        assertTrue(plugin.getGuiManager().hasOpenGui(player));
    }

    @Test
    void shouldShowHelpAndVersionMessages() {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        TNexus plugin = TestPluginSupport.loadPlugin(this.server, TestPluginSupport.TestTNexus.class);
        PlayerMock player = this.server.addPlayer();
        player.addAttachment(plugin, "tnexus.use", true);

        assertTrue(this.server.dispatchCommand(player, "tnexus help"));
        assertEquals("§8[§6T-Nexus§8] §6T-Nexus Commands", player.nextMessage());
        assertEquals("§8[§6T-Nexus§8] §e/tnexus §7- Open the main menu", player.nextMessage());
        assertEquals("§8[§6T-Nexus§8] §e/tnexus help §7- Show command help", player.nextMessage());
        assertEquals("§8[§6T-Nexus§8] §e/tnexus reload §7- Reload config and messages", player.nextMessage());
        assertEquals("§8[§6T-Nexus§8] §e/tnexus version §7- Show plugin version", player.nextMessage());

        assertTrue(this.server.dispatchCommand(player, "tnexus version"));
        assertEquals(
                "§8[§6T-Nexus§8] §aT-Nexus version: §f" + plugin.getPluginMeta().getVersion(),
                player.nextMessage());
    }

    @Test
    void shouldReloadConfigAndMessagesForAdmins() throws IOException {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        TNexus plugin = TestPluginSupport.loadPlugin(this.server, TestPluginSupport.TestTNexus.class);
        PlayerMock player = this.server.addPlayer();
        player.addAttachment(plugin, "tnexus.use", true);
        player.addAttachment(plugin, "tnexus.admin", true);

        updateReloadSuccessMessage(plugin, "&aReload complete");
        updateMainMenuTitle(plugin, "&bReloaded Menu");

        assertTrue(this.server.dispatchCommand(player, "tnexus reload"));
        assertEquals("§8[§6T-Nexus§8] §aReload complete", player.nextMessage());

        assertTrue(this.server.dispatchCommand(player, "tnexus"));
        assertEquals("§bReloaded Menu", player.getOpenInventory().getTitle());
    }

    @Test
    void shouldEnforcePermissionsAndPlayerOnlyChecks() {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        TNexus plugin = TestPluginSupport.loadPlugin(this.server, TestPluginSupport.TestTNexus.class);
        PlayerMock player = this.server.addPlayer();
        ConsoleCommandSender console = this.server.getConsoleSender();

        assertTrue(this.server.dispatchCommand(player, "tnexus reload"));
        assertEquals("§8[§6T-Nexus§8] §cこの操作を行う権限がありません", player.nextMessage());

        player.addAttachment(plugin, "tnexus.use", true);
        assertTrue(this.server.dispatchCommand(console, "tnexus"));
        assertEquals(
                "[T-Nexus] このコマンドはプレイヤーのみ実行できます",
                PlainTextComponentSerializer.plainText().serialize(
                        ((ConsoleCommandSenderMock) console).nextComponentMessage()));

        assertTrue(this.server.dispatchCommand(player, "tnexus reload"));
        assertEquals("§8[§6T-Nexus§8] §cこの操作を行う権限がありません", player.nextMessage());
    }

    @Test
    void shouldProvideTabCompletionsForAccessibleSubcommands() {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        TNexus plugin = TestPluginSupport.loadPlugin(this.server, TestPluginSupport.TestTNexus.class);
        PlayerMock player = this.server.addPlayer();
        player.addAttachment(plugin, "tnexus.use", true);

        List<String> userCompletions = plugin.getCommandManager().onTabComplete(
                player,
                TEST_COMMAND,
                "tnexus",
                new String[] {""});
        assertIterableEquals(List.of("help", "version"), userCompletions);

        player.addAttachment(plugin, "tnexus.admin", true);
        List<String> adminCompletions = plugin.getCommandManager().onTabComplete(
                player,
                TEST_COMMAND,
                "tnexus",
                new String[] {"r"});
        assertIterableEquals(List.of("reload"), adminCompletions);
    }

    private void updateReloadSuccessMessage(TNexus plugin, String message) throws IOException {
        File localeFile = plugin.getDataFolder().toPath().resolve("lang").resolve("ja_JP.yml").toFile();
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(localeFile);
        configuration.set("general.reload-success", message);
        configuration.save(localeFile);
    }

    private void updateMainMenuTitle(TNexus plugin, String title) throws IOException {
        File configFile = plugin.getDataFolder().toPath().resolve("config.yml").toFile();
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(configFile);
        configuration.set("tnexus.gui.main-menu-title", title);
        configuration.save(configFile);
    }

    private static final class TestCommand extends Command {

        private TestCommand() {
            super("tnexus");
        }

        @Override
        public boolean execute(org.bukkit.command.CommandSender sender, String commandLabel, String[] args) {
            return false;
        }
    }
}
