package network.tserver.tnexus.command;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.TestPluginSupport;
import network.tserver.tnexus.database.repository.ItemStatsDelta;
import network.tserver.tnexus.database.repository.PlayerStatsRepository;
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
        try {
            assertEquals("§8[§6T-Nexus§8] §aReload complete", waitForNextMessage(player));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }

        assertTrue(this.server.dispatchCommand(player, "tnexus"));
        assertEquals("§bReloaded Menu", player.getOpenInventory().getTitle());
    }

    @Test
    void shouldReloadDatabaseSettingsAndResourceWorldManager() throws Exception {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        TNexus plugin = TestPluginSupport.loadPlugin(this.server, TestPluginSupport.H2TestTNexus.class);
        PlayerMock admin = this.server.addPlayer("Admin");
        admin.addAttachment(plugin, "tnexus.admin", true);

        updateReloadDatabaseSettings(plugin, "reload_command");
        updateResourceWorldName(plugin, "mine_reload");

        assertTrue(this.server.dispatchCommand(admin, "tnexus reload"));
        assertEquals(
                "§8[§6T-Nexus§8] §a設定ファイルとメッセージを再読み込みしました。",
                waitForNextMessage(admin));

        try (var connection = plugin.getDatabaseManager().getConnection()) {
            assertTrue(connection.getMetaData().getURL().contains("reload_command"));
        }
        assertTrue(plugin.getResourceWorldManager().getWorldDefinition("mine_reload").isPresent());
        assertIterableEquals(
                List.of("mine_reload"),
                invokeTabCompletion(plugin, "onResourceTabComplete", admin, new String[]{"info", "mine"}));
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
    void shouldProvideAdminBalanceTabCompletions() throws Exception {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        TNexus plugin = TestPluginSupport.loadPlugin(this.server, TestPluginSupport.TestTNexus.class);
        PlayerMock admin = this.server.addPlayer("Admin");
        this.server.addPlayer("BalanceTarget");
        admin.addAttachment(plugin, "tnexus.admin.balance", true);

        assertIterableEquals(
                List.of("add", "set", "take"),
                invokeTabCompletion(plugin, "onBalanceTabComplete", admin, new String[0]));
        assertIterableEquals(
                List.of("add"),
                invokeTabCompletion(plugin, "onBalanceTabComplete", admin, new String[]{"a"}));
        assertIterableEquals(
                List.of("BalanceTarget"),
                invokeTabCompletion(plugin, "onBalanceTabComplete", admin, new String[]{"add", "Balance"}));
        assertIterableEquals(
                List.of(),
                invokeTabCompletion(plugin, "onBalanceTabComplete", admin, new String[]{"add", "BalanceTarget", "10"}));
    }

    @Test
    void shouldHideBalanceTabCompletionsWithoutPermission() throws Exception {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        TNexus plugin = TestPluginSupport.loadPlugin(this.server, TestPluginSupport.TestTNexus.class);
        PlayerMock player = this.server.addPlayer("User");

        assertIterableEquals(
                List.of(),
                invokeTabCompletion(plugin, "onBalanceTabComplete", player, new String[]{"a"}));
    }

    @Test
    void shouldProvidePayTabCompletionsForPlayersAndActions() throws Exception {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        TNexus plugin = TestPluginSupport.loadPlugin(this.server, TestPluginSupport.TestTNexus.class);
        PlayerMock sender = this.server.addPlayer("Sender");
        this.server.addPlayer("Receiver");
        sender.addAttachment(plugin, "tnexus.use", true);

        assertIterableEquals(
                List.of("Receiver"),
                invokeTabCompletion(plugin, "onPayTabComplete", sender, new String[]{"Re"}));
        assertIterableEquals(
                List.of("Receiver", "confirm", "cancel"),
                invokeTabCompletion(plugin, "onPayTabComplete", sender, new String[]{""}));
    }

    @Test
    void shouldProvideShopAndAdminPlayerTabCompletions() throws Exception {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        TNexus plugin = TestPluginSupport.loadPlugin(this.server, TestPluginSupport.TestTNexus.class);
        PlayerMock admin = this.server.addPlayer("Admin");
        this.server.addPlayer("Owner");
        admin.addAttachment(plugin, "tnexus.shop.admin", true);

        assertIterableEquals(
                List.of("linkitem"),
                invokeTabCompletion(plugin, "onShopTabComplete", admin, new String[]{"l"}));
        assertIterableEquals(
                List.of("Owner"),
                invokeTabCompletion(plugin, "onShopsTabComplete", admin, new String[]{"Ow"}));
        assertIterableEquals(
                List.of(),
                invokeTabCompletion(plugin, "onShopsTabComplete", admin, new String[]{"Owner", "extra"}));
    }

    @Test
    void shouldProvideHistoryPlayerTabCompletionForAuditAdmins() throws Exception {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        TNexus plugin = TestPluginSupport.loadPlugin(this.server, TestPluginSupport.TestTNexus.class);
        PlayerMock admin = this.server.addPlayer("Admin");
        this.server.addPlayer("Target");
        admin.addAttachment(plugin, "tnexus.audit.admin", true);

        assertIterableEquals(
                List.of("Target"),
                invokeTabCompletion(plugin, "onHistoryTabComplete", admin, new String[]{"Ta"}));
    }

    @Test
    void shouldGrantShopLinkTool() {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        TNexus plugin = TestPluginSupport.loadPlugin(this.server, TestPluginSupport.TestTNexus.class);
        PlayerMock player = this.server.addPlayer();
        player.addAttachment(plugin, "tnexus.shop.player", true);

        assertTrue(this.server.dispatchCommand(player, "shop linkitem"));
        assertEquals(
                "§8[§6T-Nexus§8] §aSignShop リンクツールを付与しました。",
                player.nextMessage());
        assertTrue(plugin.getSignShopManager().isLinkTool(player.getInventory().getItemInMainHand()));
    }

    @Test
    void shouldAllowAdminsToAddBalanceAndWriteAudit() throws Exception {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        TNexus plugin = TestPluginSupport.loadPlugin(this.server, TestPluginSupport.H2TestTNexus.class);
        PlayerMock admin = this.server.addPlayer("Admin");
        PlayerMock target = this.server.addPlayer("Target");
        admin.addAttachment(plugin, "tnexus.admin.balance", true);

        assertTrue(this.server.dispatchCommand(admin, "balance add Target 50"));
        String message = waitForNextMessage(admin);
        assertNotNull(message);
        assertTrue(message.contains("Target"));
        assertTrue(message.contains("50"));
        assertEquals(50.0D, plugin.getEconomyManager().getBalance(target.getUniqueId()).get(5, TimeUnit.SECONDS));

        List<TransactionRepository.AuditEntry> entries = plugin.getAuditLogManager()
                .getHistory(target.getUniqueId(), network.tserver.tnexus.manager.AuditLogFilter.DEPOSIT)
                .get(5, TimeUnit.SECONDS);
        assertEquals(1, entries.size());
        assertEquals(TransactionType.DEPOSIT, entries.getFirst().type());
        assertEquals(50.0D, entries.getFirst().amount());
        assertEquals(admin.getUniqueId(), entries.getFirst().counterpartUuid());
    }

    @Test
    void shouldAllowAdminsToSetOfflinePlayerBalanceAndWriteWithdrawAudit() throws Exception {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        TNexus plugin = TestPluginSupport.loadPlugin(this.server, TestPluginSupport.H2TestTNexus.class);
        PlayerMock admin = this.server.addPlayer("Admin");
        PlayerMock target = this.server.addPlayer("Target");
        admin.addAttachment(plugin, "tnexus.admin.balance", true);
        plugin.getEconomyManager().deposit(target.getUniqueId(), 120.0D).get(5, TimeUnit.SECONDS);
        target.disconnect();

        assertTrue(this.server.dispatchCommand(admin, "balance set Target 40"));
        String message = waitForNextMessage(admin);
        assertNotNull(message);
        assertTrue(message.contains("40"));
        assertEquals(40.0D, plugin.getEconomyManager().getBalance(target.getUniqueId()).get(5, TimeUnit.SECONDS));

        List<TransactionRepository.AuditEntry> entries = plugin.getAuditLogManager()
                .getHistory(target.getUniqueId(), network.tserver.tnexus.manager.AuditLogFilter.WITHDRAW)
                .get(5, TimeUnit.SECONDS);
        assertEquals(1, entries.size());
        assertEquals(TransactionType.WITHDRAW, entries.getFirst().type());
        assertEquals(80.0D, entries.getFirst().amount());
        assertEquals(admin.getUniqueId(), entries.getFirst().counterpartUuid());
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
        assertTrue(entry.contains("1"));
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
        assertTrue(entry.contains("1"));
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
    void shouldOpenServerStatsGuiAndRenderLoadedValues() throws Exception {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        TNexus plugin = TestPluginSupport.loadPlugin(this.server, TestPluginSupport.H2TestTNexus.class);
        PlayerMock player = this.server.addPlayer("StatsUser");

        insertAudit(plugin, player, TransactionType.DEPOSIT, 100.0D, 100.0D, "Deposit");
        insertAudit(plugin, player, TransactionType.WITHDRAW, 40.0D, 60.0D, "Withdraw");
        insertServerStatsFixtures(plugin, player);

        assertTrue(this.server.dispatchCommand(player, "server-stats"));
        waitUntil(() -> plugin.getGuiManager().hasOpenGui(player));
        waitUntil(() -> {
            org.bukkit.inventory.ItemStack item = player.getOpenInventory().getTopInventory().getItem(12);
            return item != null
                    && item.getItemMeta() != null
                    && plugin.getMessageConfig().getMessage("server-stats.gui.total-transactions.name")
                    .equals(item.getItemMeta().getDisplayName());
        });

        assertEquals(plugin.getMessageConfig().getMessage("server-stats.gui.title"), player.getOpenInventory().getTitle());
        assertEquals(Material.PLAYER_HEAD, player.getOpenInventory().getTopInventory().getItem(10).getType());
        assertEquals(Material.GOLD_INGOT, player.getOpenInventory().getTopInventory().getItem(12).getType());
        assertEquals(Material.GOLD_BLOCK, player.getOpenInventory().getTopInventory().getItem(14).getType());
        assertEquals(Material.CLOCK, player.getOpenInventory().getTopInventory().getItem(19).getType());
        assertEquals(Material.EMERALD, player.getOpenInventory().getTopInventory().getItem(21).getType());
        assertEquals(Material.CHEST, player.getOpenInventory().getTopInventory().getItem(23).getType());
    }

    @Test
    void shouldOpenOwnStatsGui() throws Exception {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        TNexus plugin = TestPluginSupport.loadPlugin(this.server, TestPluginSupport.H2TestTNexus.class);
        PlayerMock player = this.server.addPlayer("StatsUser");
        player.addAttachment(plugin, "tnexus.stats.self", true);

        assertTrue(this.server.dispatchCommand(player, "stats"));
        waitUntil(() -> plugin.getGuiManager().hasOpenGui(player));
        waitUntil(() -> player.getOpenInventory().getTopInventory().getItem(20) != null);

        assertEquals(plugin.getMessageConfig().getMessage("stats.gui.main.title", "StatsUser"), player.getOpenInventory().getTitle());
        assertEquals(Material.PLAYER_HEAD, player.getOpenInventory().getTopInventory().getItem(4).getType());
        assertEquals(Material.BOOK, player.getOpenInventory().getTopInventory().getItem(20).getType());
        assertEquals(Material.GOLD_INGOT, player.getOpenInventory().getTopInventory().getItem(21).getType());
        assertEquals(Material.GRASS_BLOCK, player.getOpenInventory().getTopInventory().getItem(22).getType());
        assertEquals(Material.DIAMOND_SWORD, player.getOpenInventory().getTopInventory().getItem(23).getType());
        assertEquals(Material.CRAFTING_TABLE, player.getOpenInventory().getTopInventory().getItem(24).getType());
    }

    @Test
    void shouldOpenOtherPlayersStatsGuiWithPermission() throws Exception {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        TNexus plugin = TestPluginSupport.loadPlugin(this.server, TestPluginSupport.H2TestTNexus.class);
        PlayerMock viewer = this.server.addPlayer("Viewer");
        PlayerMock target = this.server.addPlayer("Target");
        viewer.addAttachment(plugin, "tnexus.stats.others", true);

        assertTrue(this.server.dispatchCommand(viewer, "stats Target"));
        waitUntil(() -> plugin.getGuiManager().hasOpenGui(viewer));
        waitUntil(() -> viewer.getOpenInventory().getTopInventory().getItem(20) != null);

        assertEquals(plugin.getMessageConfig().getMessage("stats.gui.main.title", "Target"), viewer.getOpenInventory().getTitle());
        assertEquals(Material.PLAYER_HEAD, viewer.getOpenInventory().getTopInventory().getItem(4).getType());
    }

    @Test
    void shouldOpenStatsRankingGui() throws Exception {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        TNexus plugin = TestPluginSupport.loadPlugin(this.server, TestPluginSupport.H2TestTNexus.class);
        PlayerMock player = this.server.addPlayer("StatsUser");
        player.addAttachment(plugin, "tnexus.stats.self", true);

        assertTrue(this.server.dispatchCommand(player, "stats ranking"));
        waitUntil(() -> plugin.getGuiManager().hasOpenGui(player));
        waitUntil(() -> player.getOpenInventory().getTopInventory().getItem(4) != null);

        assertEquals(plugin.getMessageConfig().getMessage("stats.ranking.gui.title"), player.getOpenInventory().getTitle());
        assertEquals(Material.CLOCK, player.getOpenInventory().getTopInventory().getItem(4).getType());
        assertEquals(Material.COMPASS, player.getOpenInventory().getTopInventory().getItem(7).getType());
    }

    @Test
    void shouldRejectStatsResetFromPlayers() {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        TNexus plugin = TestPluginSupport.loadPlugin(this.server, TestPluginSupport.TestTNexus.class);
        PlayerMock player = this.server.addPlayer("Admin");
        player.addAttachment(plugin, "tnexus.stats.admin", true);

        assertTrue(this.server.dispatchCommand(player, "tnexus stats reset all"));
        assertEquals(plugin.getMessageConfig().getMessage("prefix")
                        + plugin.getMessageConfig().getMessage("general.console-only"),
                player.nextMessage());
    }

    @Test
    void shouldResetSpecificStatsAndRequireConfirmationForAll() throws Exception {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        TNexus plugin = TestPluginSupport.loadPlugin(this.server, TestPluginSupport.H2TestTNexus.class);
        ConsoleCommandSenderMock console = (ConsoleCommandSenderMock) this.server.getConsoleSender();
        PlayerMock viewer = this.server.addPlayer("Viewer");
        PlayerMock target = this.server.addPlayer("Target");

        seedStatsForReset(plugin, target);

        assertTrue(this.server.dispatchCommand(console, "tnexus stats reset Target ITEM:DIAMOND"));
        waitUntil(() -> {
            try {
                return plugin.getPlayerStatsViewerManager()
                        .loadSnapshot(viewer.getUniqueId(), target, network.tserver.tnexus.manager.PlayerStatsViewerManager.StatsPeriodFilter.ALL_TIME)
                        .get(5, TimeUnit.SECONDS)
                        .getEntry("ITEM:DIAMOND") == null;
            } catch (Exception exception) {
                return false;
            }
        });

        assertTrue(this.server.dispatchCommand(console, "tnexus stats reset all"));
        waitUntil(() -> {
            Component message = console.nextComponentMessage();
            return message != null
                    && PlainTextComponentSerializer.plainText().serialize(message).contains("CONFIRM");
        });

        assertTrue(this.server.dispatchCommand(console, "tnexus stats reset all CONFIRM"));
        waitUntil(() -> {
            try {
                network.tserver.tnexus.manager.PlayerStatsViewerManager.PlayerStatsSnapshot snapshot = plugin
                        .getPlayerStatsViewerManager()
                        .loadSnapshot(
                                viewer.getUniqueId(),
                                target,
                                network.tserver.tnexus.manager.PlayerStatsViewerManager.StatsPeriodFilter.ALL_TIME)
                        .get(5, TimeUnit.SECONDS);
                network.tserver.tnexus.manager.PlayerStatsViewerManager.StatsEntry playTime = snapshot.getEntry("GENERAL_PLAY_TIME");
                network.tserver.tnexus.manager.PlayerStatsViewerManager.StatsEntry projectile = snapshot.getEntry("ACTIVITY_PROJECTILE_TOTAL");
                return playTime != null
                        && "0:00:00".equals(playTime.valueText())
                        && projectile != null
                        && "0".equals(projectile.valueText());
            } catch (Exception exception) {
                return false;
            }
        });
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

    private void updateReloadDatabaseSettings(TNexus plugin, String databaseName) throws IOException {
        File configFile = plugin.getDataFolder().toPath().resolve("config.yml").toFile();
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(configFile);
        configuration.set(
                "tnexus.database.jdbc-url",
                "jdbc:h2:mem:%s;MODE=MySQL;DB_CLOSE_DELAY=-1".formatted(databaseName));
        configuration.set("tnexus.database.driver-class-name", "org.h2.Driver");
        configuration.set("tnexus.database.username", "sa");
        configuration.set("tnexus.database.password", "");
        configuration.set("tnexus.database.pool-size", 4);
        configuration.save(configFile);
    }

    private void updateResourceWorldName(TNexus plugin, String worldName) throws IOException {
        File configFile = plugin.getDataFolder().toPath().resolve("config.yml").toFile();
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(configFile);
        List<Map<?, ?>> worlds = new java.util.ArrayList<>(configuration.getMapList("resource-world.worlds"));
        Map<Object, Object> updatedWorld = new java.util.LinkedHashMap<>(worlds.getFirst());
        updatedWorld.put("name", worldName);
        worlds.set(0, updatedWorld);
        configuration.set("resource-world.worlds", worlds);
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

    private void insertServerStatsFixtures(TNexus plugin, PlayerMock player) throws Exception {
        try (var connection = plugin.getDatabaseManager().getConnection()) {
            try (var statement = connection.prepareStatement(
                    "INSERT INTO tnexus_server_shops (name, material, amount, buy_price, sell_price, category, enabled, created_by) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                statement.setString(1, "Server Shop");
                statement.setString(2, "DIAMOND");
                statement.setInt(3, 1);
                statement.setDouble(4, 100.0D);
                statement.setDouble(5, 90.0D);
                statement.setString(6, "test");
                statement.setBoolean(7, true);
                statement.setString(8, player.getUniqueId().toString());
                statement.executeUpdate();
            }
            try (var statement = connection.prepareStatement(
                    "INSERT INTO tnexus_player_shops (owner_uuid, material, amount, price, type, stock, enabled) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                statement.setString(1, player.getUniqueId().toString());
                statement.setString(2, "EMERALD");
                statement.setInt(3, 1);
                statement.setDouble(4, 50.0D);
                statement.setString(5, "SELL");
                statement.setInt(6, 8);
                statement.setBoolean(7, true);
                statement.executeUpdate();
            }
        }
    }

    private void seedStatsForReset(TNexus plugin, PlayerMock target) throws Exception {
        PlayerStatsRepository repository = new PlayerStatsRepository(plugin.getDatabaseManager());
        repository.ensurePlayerExists(target.getUniqueId(), Instant.parse("2026-06-01T00:00:00Z")).get(5, TimeUnit.SECONDS);
        repository.addPlayTime(target.getUniqueId(), 120L).get(5, TimeUnit.SECONDS);
        repository.addItemStats(
                        java.util.Map.of(
                                target.getUniqueId(),
                                java.util.Map.of(Material.DIAMOND.name(), new ItemStatsDelta(4, 1))))
                .get(5, TimeUnit.SECONDS);
        repository.incrementProjectileCount(target.getUniqueId(), "ARROW").get(5, TimeUnit.SECONDS);
    }

    @Test
    void shouldShowResourceWorldListAndInfo() throws Exception {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        TNexus plugin = TestPluginSupport.loadPlugin(this.server, TestPluginSupport.H2TestTNexus.class);
        PlayerMock player = this.server.addPlayer("Explorer");

        waitUntil(() -> {
            try {
                return plugin.getResourceWorldManager()
                        .getNextResetTime("resource")
                        .get(5, TimeUnit.SECONDS)
                        .isPresent();
            } catch (Exception exception) {
                return false;
            }
        });

        assertTrue(this.server.dispatchCommand(player, "resource"));
        List<String> listMessages = waitForMessages(player, 9);
        assertTrue(listMessages.get(1).contains("資源ワールド"));
        assertTrue(listMessages.stream().anyMatch(message -> message.contains("(resource)")));
        assertTrue(listMessages.stream().anyMatch(message -> message.contains("(resource_nether)")));
        assertTrue(listMessages.stream().anyMatch(message -> message.contains("(resource_end)")));

        assertTrue(this.server.dispatchCommand(player, "resource info resource"));
        List<String> infoMessages = waitForMessages(player, 8);
        assertTrue(infoMessages.get(1).contains("資源ワールド詳細"));
        assertTrue(infoMessages.stream().anyMatch(message -> message.contains("resource")));
        assertTrue(infoMessages.stream().anyMatch(message -> message.contains("未実施")));
    }

    @Test
    void shouldShowAdminResourceStatusAndTabCompletions() throws Exception {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        TNexus plugin = TestPluginSupport.loadPlugin(this.server, TestPluginSupport.H2TestTNexus.class);
        PlayerMock admin = this.server.addPlayer("Admin");
        admin.addAttachment(plugin, "tnexus.admin", true);

        waitUntil(() -> {
            try {
                return plugin.getResourceWorldManager()
                        .getNextResetTime("resource")
                        .get(5, TimeUnit.SECONDS)
                        .isPresent();
            } catch (Exception exception) {
                return false;
            }
        });

        assertTrue(this.server.dispatchCommand(admin, "resource status"));
        List<String> statusMessages = waitForMessages(admin, 8);
        assertTrue(statusMessages.get(1).contains("管理ステータス"));
        assertTrue(statusMessages.stream().anyMatch(message -> message.contains("scheduled")));
        assertIterableEquals(
                List.of("info", "status"),
                invokeTabCompletion(plugin, "onResourceTabComplete", admin, new String[]{""}));
        assertIterableEquals(
                List.of("resource", "resource_nether", "resource_end"),
                invokeTabCompletion(plugin, "onResourceTabComplete", admin, new String[]{"info", "resource"}));
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

    private List<String> waitForMessages(PlayerMock player, int count) throws Exception {
        List<String> messages = new java.util.ArrayList<>();
        while (messages.size() < count) {
            String message = waitForNextMessage(player);
            assertNotNull(message);
            messages.add(message);
        }
        return messages;
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

    @SuppressWarnings("unchecked")
    private List<String> invokeTabCompletion(
            TNexus plugin,
            String methodName,
            CommandSender sender,
            String[] args) throws Exception {
        Method method = CommandManager.class.getDeclaredMethod(methodName, CommandSender.class, String[].class);
        method.setAccessible(true);
        Collection<String> completions = (Collection<String>) method.invoke(plugin.getCommandManager(), sender, args);
        return List.copyOf(completions);
    }
}
