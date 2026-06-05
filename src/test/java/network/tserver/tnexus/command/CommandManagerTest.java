package network.tserver.tnexus.command;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.TestPluginSupport;
import network.tserver.tnexus.database.repository.TransactionRepository;
import network.tserver.tnexus.database.repository.TransactionRepository.AuditRecord;
import network.tserver.tnexus.database.repository.TransactionRepository.TransactionType;
import network.tserver.tnexus.manager.ShopType;
import network.tserver.tnexus.manager.SignShop;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
        assertEquals("§8[§6T-Nexus§8] §6T-Nexus コマンド一覧", player.nextMessage());
        assertEquals("§8[§6T-Nexus§8] §e/tnexus §7- メインメニューを開きます", player.nextMessage());
        assertEquals("§8[§6T-Nexus§8] §e/tnexus help §7- ヘルプを表示します", player.nextMessage());
        assertEquals("§8[§6T-Nexus§8] §e/tnexus reload §7- 設定とメッセージを再読み込みします", player.nextMessage());
        assertEquals("§8[§6T-Nexus§8] §e/tnexus version §7- プラグインのバージョンを表示します", player.nextMessage());

        assertTrue(this.server.dispatchCommand(player, "tnexus version"));
        assertEquals(
                "§8[§6T-Nexus§8] §aT-Nexus バージョン: §f" + plugin.getPluginMeta().getVersion(),
                player.nextMessage());
    }

    @Test
    void shouldShowBalanceForPlayers() throws Exception {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        TNexus plugin = TestPluginSupport.loadPlugin(this.server, TestPluginSupport.TestTNexus.class);
        PlayerMock player = this.server.addPlayer("BalanceUser");
        player.addAttachment(plugin, "tnexus.use", true);
        plugin.getEconomyManager().deposit(player.getUniqueId(), 1234.0D).get();

        Method onBalanceCommand = CommandManager.class.getDeclaredMethod(
                "onBalanceCommand",
                org.bukkit.command.CommandSender.class,
                String[].class);
        onBalanceCommand.setAccessible(true);
        onBalanceCommand.invoke(plugin.getCommandManager(), player, new String[0]);
        String message = waitForNextMessage(player);
        assertNotNull(message);
        assertTrue(message.contains("1234") || message.contains("1,234"));
    }

    @Test
    void shouldRouteBalAliasToBalanceCommand() throws Exception {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        TNexus plugin = TestPluginSupport.loadPlugin(this.server, TestPluginSupport.TestTNexus.class);
        PlayerMock player = this.server.addPlayer("BalanceAliasUser");
        player.addAttachment(plugin, "tnexus.use", true);
        plugin.getEconomyManager().deposit(player.getUniqueId(), 4321.0D).get();

        assertTrue(this.server.dispatchCommand(player, "bal"));
        String message = waitForNextMessage(player);
        assertNotNull(message);
        assertTrue(message.contains("4321") || message.contains("4,321"));
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
        CommandSender console = this.server.getConsoleSender();

        assertTrue(this.server.dispatchCommand(player, "tnexus reload"));
        assertEquals("§8[§6T-Nexus§8] §cこの操作を行う権限がありません。", player.nextMessage());

        player.addAttachment(plugin, "tnexus.use", true);
        assertTrue(this.server.dispatchCommand(console, "tnexus"));
        assertEquals(
                "[T-Nexus] このコマンドはプレイヤーのみ実行できます。",
                PlainTextComponentSerializer.plainText().serialize(
                        ((ConsoleCommandSenderMock) console).nextComponentMessage()));

        assertTrue(this.server.dispatchCommand(player, "tnexus reload"));
        assertEquals("§8[§6T-Nexus§8] §cこの操作を行う権限がありません。", player.nextMessage());
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
                new String[]{""});
        assertIterableEquals(List.of("help", "version"), userCompletions);

        player.addAttachment(plugin, "tnexus.admin", true);
        List<String> adminCompletions = plugin.getCommandManager().onTabComplete(
                player,
                TEST_COMMAND,
                "tnexus",
                new String[]{"r"});
        assertIterableEquals(List.of("reload"), adminCompletions);
    }

    @Test
    void shouldSuppressPermissionMessageDuringTabCompletion() {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        TNexus plugin = TestPluginSupport.loadPlugin(this.server, TestPluginSupport.TestTNexus.class);
        PlayerMock player = this.server.addPlayer();
        player.addAttachment(plugin, "tnexus.use", true);

        List<String> completions = plugin.getCommandManager().onTabComplete(
                player,
                TEST_COMMAND,
                "tnexus",
                new String[]{"r"});

        assertIterableEquals(List.of(), completions);
        assertNull(player.nextMessage());
    }

    @Test
    void shouldStartShopLinkMode() {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        TNexus plugin = TestPluginSupport.loadPlugin(this.server, TestPluginSupport.TestTNexus.class);
        PlayerMock player = this.server.addPlayer();
        player.addAttachment(plugin, "tnexus.shop.use", true);

        assertTrue(this.server.dispatchCommand(player, "shop link"));
        assertEquals(
                "§8[§6T-Nexus§8] §eリンクモードを開始しました。ショップ看板を右クリックし、その後チェストを右クリックしてください。",
                player.nextMessage());
    }

    @Test
    void shouldListOwnPlayerShops() throws Exception {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        TNexus plugin = TestPluginSupport.loadPlugin(this.server, TestPluginSupport.H2TestTNexus.class);
        PlayerMock owner = this.server.addPlayer("Owner");
        owner.addAttachment(plugin, "tnexus.shop.player", true);

        World world = owner.getWorld();
        Block chestBlock = world.getBlockAt(70, 64, 0);
        chestBlock.setType(Material.CHEST);
        ((org.bukkit.block.Chest) chestBlock.getState()).getBlockInventory()
                .addItem(new org.bukkit.inventory.ItemStack(Material.DIAMOND, 1));
        Block signBlock = world.getBlockAt(71, 64, 0);
        signBlock.setType(Material.OAK_SIGN);

        SignShop shop = plugin.getSignShopManager().createShop(
                owner,
                signBlock,
                ShopType.PLAYER,
                "List test",
                chestBlock,
                new org.bukkit.inventory.ItemStack(Material.DIAMOND));
        assertNotNull(shop);

        waitUntil(() -> plugin.getSignShopManager().getShop(signBlock) != null);

        assertTrue(this.server.dispatchCommand(owner, "shops"));
        String header = owner.nextMessage();
        String entry = owner.nextMessage();
        assertNotNull(header);
        assertNotNull(entry);
        assertTrue(header.contains("あなたのショップ一覧"));
        assertTrue(entry.contains("Diamond"));
        assertTrue(entry.contains(world.getName()));
    }

    @Test
    void shouldListOtherPlayersShopsForAdmins() throws Exception {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        TNexus plugin = TestPluginSupport.loadPlugin(this.server, TestPluginSupport.H2TestTNexus.class);
        PlayerMock owner = this.server.addPlayer("Owner");
        PlayerMock admin = this.server.addPlayer("Admin");
        owner.addAttachment(plugin, "tnexus.shop.player", true);
        admin.addAttachment(plugin, "tnexus.shop.admin", true);

        World world = owner.getWorld();
        Block chestBlock = world.getBlockAt(80, 64, 0);
        chestBlock.setType(Material.CHEST);
        ((org.bukkit.block.Chest) chestBlock.getState()).getBlockInventory()
                .addItem(new org.bukkit.inventory.ItemStack(Material.EMERALD, 1));
        Block signBlock = world.getBlockAt(81, 64, 0);
        signBlock.setType(Material.OAK_SIGN);

        SignShop shop = plugin.getSignShopManager().createShop(
                owner,
                signBlock,
                ShopType.PLAYER,
                "Admin list",
                chestBlock,
                new org.bukkit.inventory.ItemStack(Material.EMERALD));
        assertNotNull(shop);

        waitUntil(() -> plugin.getSignShopManager().getShop(signBlock) != null);

        assertTrue(this.server.dispatchCommand(admin, "shops Owner"));
        String header = admin.nextMessage();
        String entry = admin.nextMessage();
        assertNotNull(header);
        assertNotNull(entry);
        assertTrue(header.contains("Owner"));
        assertTrue(entry.contains("Emerald"));
    }

    @Test
    void shouldUsePayConfirmCommandInPaymentClickEvent() throws Exception {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        TNexus plugin = TestPluginSupport.loadPlugin(this.server, TestPluginSupport.TestTNexus.class);
        PlayerMock player = this.server.addPlayer("Sender");
        player.addAttachment(plugin, "tnexus.use", true);

        Method method = CommandManager.class.getDeclaredMethod(
                "sendPaymentConfirmationMessage",
                Player.class,
                String.class,
                String.class,
                double.class);
        method.setAccessible(true);
        method.invoke(plugin.getCommandManager(), player, "token-123", "Receiver", 25.0D);

        Component message = player.nextComponentMessage();
        ClickEvent confirmClickEvent = findFirstClickEvent(message);
        assertNotNull(confirmClickEvent);
        assertEquals("/pay confirm token-123", confirmClickEvent.value());
    }

    @Test
    void shouldOpenOwnHistoryGui() throws Exception {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        TNexus plugin = TestPluginSupport.loadPlugin(this.server, TestPluginSupport.H2TestTNexus.class);
        PlayerMock player = this.server.addPlayer("HistoryUser");
        player.addAttachment(plugin, "tnexus.use", true);
        insertAudit(plugin, player, TransactionType.DEPOSIT, 150.0D, 150.0D, "System deposit");

        assertTrue(this.server.dispatchCommand(player, "history"));
        waitUntil(() -> plugin.getGuiManager().hasOpenGui(player));

        assertEquals(ChatColor.translateAlternateColorCodes('&', "&6取引履歴"), player.getOpenInventory().getTitle());
        assertEquals(Material.HOPPER, player.getOpenInventory().getTopInventory().getItem(4).getType());
    }

    @Test
    void shouldOpenOtherPlayersHistoryGuiForAdmins() throws Exception {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        TNexus plugin = TestPluginSupport.loadPlugin(this.server, TestPluginSupport.H2TestTNexus.class);
        PlayerMock admin = this.server.addPlayer("Admin");
        PlayerMock target = this.server.addPlayer("Target");
        admin.addAttachment(plugin, "tnexus.use", true);
        admin.addAttachment(plugin, "tnexus.audit.admin", true);
        insertAudit(plugin, target, TransactionType.PAYMENT_RECEIVED, 75.0D, 75.0D, "Payment received from Admin");

        assertTrue(this.server.dispatchCommand(admin, "history Target"));
        waitUntil(() -> plugin.getGuiManager().hasOpenGui(admin));

        assertEquals(ChatColor.translateAlternateColorCodes('&', "&6Target の取引履歴"), admin.getOpenInventory().getTitle());
    }

    @Test
    void shouldDenyOtherPlayersHistoryWithoutAuditPermission() {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        TNexus plugin = TestPluginSupport.loadPlugin(this.server, TestPluginSupport.H2TestTNexus.class);
        PlayerMock player = this.server.addPlayer("User");
        this.server.addPlayer("Target");
        player.addAttachment(plugin, "tnexus.use", true);

        assertTrue(this.server.dispatchCommand(player, "history Target"));
        assertEquals(
                ChatColor.translateAlternateColorCodes('&', "&8[&6T-Nexus&8] &cこの操作を行う権限がありません。"),
                player.nextMessage());
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

    private void insertAudit(
            TNexus plugin,
            PlayerMock player,
            TransactionType type,
            double amount,
            double balanceAfter,
            String description) throws Exception {
        new TransactionRepository(plugin.getDatabaseManager()).insert(new AuditRecord(
                player.getUniqueId(),
                type,
                amount,
                balanceAfter,
                description,
                null)).get();
    }

    private static final class TestCommand extends Command {

        private TestCommand() {
            super("tnexus");
        }

        @Override
        public boolean execute(CommandSender sender, String commandLabel, String[] args) {
            return false;
        }
    }

    private void waitUntil(java.util.function.BooleanSupplier condition) throws Exception {
        long deadline = System.currentTimeMillis() + 5000L;
        while (System.currentTimeMillis() < deadline) {
            this.server.getScheduler().performOneTick();
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(25L);
        }
        assertTrue(condition.getAsBoolean(), "Condition was not met in time");
    }

    private String waitForNextMessage(PlayerMock player) throws Exception {
        long deadline = System.currentTimeMillis() + 5000L;
        while (System.currentTimeMillis() < deadline) {
            this.server.getScheduler().performOneTick();
            String message = player.nextMessage();
            if (message != null) {
                return message;
            }
            Thread.sleep(25L);
        }
        return player.nextMessage();
    }

    private ClickEvent findFirstClickEvent(Component component) {
        if (component.clickEvent() != null) {
            return component.clickEvent();
        }
        for (Component child : component.children()) {
            ClickEvent clickEvent = findFirstClickEvent(child);
            if (clickEvent != null) {
                return clickEvent;
            }
        }
        return null;
    }
}
