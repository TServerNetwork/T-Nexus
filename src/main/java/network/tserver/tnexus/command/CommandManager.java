package network.tserver.tnexus.command;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.command.subcommand.HelpCommand;
import network.tserver.tnexus.command.subcommand.MenuCommand;
import network.tserver.tnexus.command.subcommand.ReloadCommand;
import network.tserver.tnexus.command.subcommand.VersionCommand;
import network.tserver.tnexus.manager.AuditLogFilter;
import network.tserver.tnexus.manager.PaymentManager;
import network.tserver.tnexus.manager.PaymentManager.ConfirmationResult;
import network.tserver.tnexus.manager.PaymentManager.QueueResult;
import network.tserver.tnexus.database.repository.TransactionRepository.TransactionType;
import network.tserver.tnexus.util.CurrencyFormatter;
import network.tserver.tnexus.util.TabCompleterUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Registers and dispatches the main /tnexus command tree.
 */
public final class CommandManager {

    private static final String COMMAND_NAME = "tnexus";
    private static final String COMMAND_DESCRIPTION = "T-Nexus main command";
    private static final List<String> COMMAND_ALIASES = List.of("tn", "nexus");
    private static final String BALANCE_COMMAND_NAME = "balance";
    private static final String BALANCE_ALIAS_COMMAND_NAME = "bal";
    private static final String PAY_COMMAND_NAME = "pay";
    private static final String SHOP_COMMAND_NAME = "shop";
    private static final String SHOPS_COMMAND_NAME = "shops";
    private static final String HISTORY_COMMAND_NAME = "history";
    private static final String STATS_COMMAND_NAME = "stats";
    private static final String SERVER_STATS_COMMAND_NAME = "server-stats";
    private static final List<String> BALANCE_ACTIONS = List.of("add", "set", "take");
    private static final List<String> PAY_ACTIONS = List.of("confirm", "cancel");
    private static final List<String> SHOP_ACTIONS = List.of("linkitem");
    private static final String USE_PERMISSION = "tnexus.use";
    private static final String BALANCE_ADMIN_PERMISSION = "tnexus.admin.balance";
    private static final String SHOP_ADMIN_PERMISSION = "tnexus.shop.admin";
    private static final String SHOP_PLAYER_PERMISSION = "tnexus.shop.player";
    private static final String AUDIT_ADMIN_PERMISSION = "tnexus.audit.admin";
    private static final String STATS_SELF_PERMISSION = "tnexus.stats.self";
    private static final String STATS_OTHERS_PERMISSION = "tnexus.stats.others";
    private static final String STATS_ADMIN_PERMISSION = "tnexus.stats.admin";

    private final TNexus plugin;
    private final BaseCommand rootCommand;
    private final Map<String, BaseCommand> subcommands;

    /**
     * Creates a new command manager.
     *
     * @param plugin plugin instance
     */
    public CommandManager(TNexus plugin) {
        this.plugin = plugin;
        this.rootCommand = new MenuCommand(plugin);
        this.subcommands = new LinkedHashMap<>();
        registerSubcommand(new HelpCommand(plugin));
        registerSubcommand(new ReloadCommand(plugin));
        registerSubcommand(new VersionCommand(plugin));
    }

    /**
     * Registers the plugin command and tab completions with Paper's lifecycle API.
     *
     * @param commands Paper commands registrar
     */
    public void registerCommands(Commands commands) {
        commands.register(COMMAND_NAME, COMMAND_DESCRIPTION, COMMAND_ALIASES, new BasicCommand() {
            @Override
            public void execute(CommandSourceStack commandSourceStack, String[] args) {
                CommandManager.this.onCommand(
                        commandSourceStack.getSender(),
                        null,
                        COMMAND_NAME,
                        args);
            }

            @Override
            public Collection<String> suggest(CommandSourceStack commandSourceStack, String[] args) {
                return CommandManager.this.onTabComplete(
                        commandSourceStack.getSender(),
                        null,
                        COMMAND_NAME,
                        args);
            }
                });

        BasicCommand balanceCommand = new BasicCommand() {
            @Override
            public void execute(CommandSourceStack commandSourceStack, String[] args) {
                CommandManager.this.onBalanceCommand(commandSourceStack.getSender(), args);
            }

            @Override
            public Collection<String> suggest(CommandSourceStack commandSourceStack, String[] args) {
                return CommandManager.this.onBalanceTabComplete(commandSourceStack.getSender(), args);
            }
        };
        commands.register(BALANCE_COMMAND_NAME, "Show your balance", List.of(BALANCE_ALIAS_COMMAND_NAME), balanceCommand);
        commands.register(BALANCE_ALIAS_COMMAND_NAME, "Show your balance", List.of(), balanceCommand);

        commands.register(PAY_COMMAND_NAME, "Pay another player", List.of(), new BasicCommand() {
            @Override
            public void execute(CommandSourceStack commandSourceStack, String[] args) {
                CommandManager.this.onPayCommand(commandSourceStack.getSender(), args);
            }

            @Override
            public Collection<String> suggest(CommandSourceStack commandSourceStack, String[] args) {
                return CommandManager.this.onPayTabComplete(commandSourceStack.getSender(), args);
            }
        });

        commands.register(SHOP_COMMAND_NAME, "Manage sign shops", List.of(), new BasicCommand() {
            @Override
            public void execute(CommandSourceStack commandSourceStack, String[] args) {
                CommandManager.this.onShopCommand(commandSourceStack.getSender(), args);
            }

            @Override
            public Collection<String> suggest(CommandSourceStack commandSourceStack, String[] args) {
                return CommandManager.this.onShopTabComplete(commandSourceStack.getSender(), args);
            }
        });

        commands.register(SHOPS_COMMAND_NAME, "List player shops", List.of(), new BasicCommand() {
            @Override
            public void execute(CommandSourceStack commandSourceStack, String[] args) {
                CommandManager.this.onShopsCommand(commandSourceStack.getSender(), args);
            }

            @Override
            public Collection<String> suggest(CommandSourceStack commandSourceStack, String[] args) {
                return CommandManager.this.onShopsTabComplete(commandSourceStack.getSender(), args);
            }
        });

        commands.register(HISTORY_COMMAND_NAME, "Open audit history", List.of(), new BasicCommand() {
            @Override
            public void execute(CommandSourceStack commandSourceStack, String[] args) {
                CommandManager.this.onHistoryCommand(commandSourceStack.getSender(), args);
            }

            @Override
            public Collection<String> suggest(CommandSourceStack commandSourceStack, String[] args) {
                return CommandManager.this.onHistoryTabComplete(commandSourceStack.getSender(), args);
            }
        });

        commands.register(STATS_COMMAND_NAME, "Open player stats", List.of(), new BasicCommand() {
            @Override
            public void execute(CommandSourceStack commandSourceStack, String[] args) {
                CommandManager.this.onStatsCommand(commandSourceStack.getSender(), args);
            }

            @Override
            public Collection<String> suggest(CommandSourceStack commandSourceStack, String[] args) {
                return CommandManager.this.onStatsTabComplete(commandSourceStack.getSender(), args);
            }
        });

        commands.register(SERVER_STATS_COMMAND_NAME, "Open server stats", List.of(), new BasicCommand() {
            @Override
            public void execute(CommandSourceStack commandSourceStack, String[] args) {
                CommandManager.this.onServerStatsCommand(commandSourceStack.getSender(), args);
            }

            @Override
            public Collection<String> suggest(CommandSourceStack commandSourceStack, String[] args) {
                return Collections.emptyList();
            }
        });
    }

    public boolean onCommand(
            @NotNull CommandSender sender,
            @Nullable Command command,
            @NotNull String label,
            @NotNull String[] args) {
        if (args.length == 0) {
            return executeCommand(sender, this.rootCommand, args);
        }

        BaseCommand subcommand = this.subcommands.get(normalize(args[0]));
        if (subcommand == null) {
            this.plugin.getMessageConfig().sendMessage(sender, "general.unknown-command");
            return true;
        }
        return executeCommand(sender, subcommand, sliceArgs(args));
    }

    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @Nullable Command command,
            @NotNull String alias,
            @NotNull String[] args) {
        if (args.length <= 1) {
            String input = args.length == 0 ? "" : normalize(args[0]);
            List<String> completions = new ArrayList<>();
            for (BaseCommand subcommand : this.subcommands.values()) {
                if (!canTabComplete(sender, subcommand)) {
                    continue;
                }
                if (subcommand.getName().startsWith(input)) {
                    completions.add(subcommand.getName());
                }
            }
            return completions.isEmpty() ? Collections.emptyList() : completions;
        }

        BaseCommand subcommand = this.subcommands.get(normalize(args[0]));
        if (subcommand == null || !canTabComplete(sender, subcommand)) {
            return Collections.emptyList();
        }
        return subcommand.getTabCompletions(sender, sliceArgs(args));
    }

    private void registerSubcommand(BaseCommand subcommand) {
        this.subcommands.put(subcommand.getName(), subcommand);
    }

    private boolean executeCommand(CommandSender sender, BaseCommand command, String[] args) {
        if (!canUse(sender, command)) {
            return true;
        }
        return command.execute(sender, args);
    }

    private boolean canUse(CommandSender sender, BaseCommand command) {
        String permission = command.getPermission();
        if (permission != null && !permission.isBlank() && !sender.hasPermission(permission)) {
            this.plugin.getMessageConfig().sendMessage(sender, "general.no-permission");
            return false;
        }

        if (command.isPlayerOnly() && !(sender instanceof Player)) {
            this.plugin.getMessageConfig().sendMessage(sender, "general.player-only");
            return false;
        }
        return true;
    }

    private boolean canTabComplete(CommandSender sender, BaseCommand command) {
        String permission = command.getPermission();
        if (permission != null && !permission.isBlank() && !sender.hasPermission(permission)) {
            return false;
        }

        return !command.isPlayerOnly() || sender instanceof Player;
    }

    private String[] sliceArgs(String[] args) {
        if (args.length <= 1) {
            return new String[0];
        }

        String[] sliced = new String[args.length - 1];
        System.arraycopy(args, 1, sliced, 0, sliced.length);
        return sliced;
    }

    private String normalize(String input) {
        return input.toLowerCase(Locale.ROOT);
    }

    private void onBalanceCommand(CommandSender sender, String[] args) {
        if (args.length == 0) {
            showOwnBalance(sender);
            return;
        }

        if (!sender.hasPermission(BALANCE_ADMIN_PERMISSION)) {
            this.plugin.getMessageConfig().sendMessage(sender, "general.no-permission");
            return;
        }
        handleAdminBalanceCommand(sender, args);
    }

    private void onPayCommand(CommandSender sender, String[] args) {
        if (!canUseEconomyCommand(sender)) {
            return;
        }

        if (args.length == 0) {
            this.plugin.getMessageConfig().sendMessage(sender, "economy.pay.usage");
            return;
        }

        Player player = (Player) sender;
        if (args.length >= 2 && "confirm".equalsIgnoreCase(args[0])) {
            confirmPayment(player, args[1]);
            return;
        }
        if (args.length >= 2 && "cancel".equalsIgnoreCase(args[0])) {
            cancelPayment(player, args[1]);
            return;
        }
        if (args.length != 1) {
            this.plugin.getMessageConfig().sendMessage(sender, "economy.pay.usage");
            return;
        }

        OfflinePlayer target = resolveTarget(args[0]);
        if (target == null) {
            this.plugin.getMessageConfig().sendMessage(player, "economy.pay.target-not-found", args[0]);
            return;
        }

        String targetName = PaymentManager.resolveName(target);
        this.plugin.getAnvilGuiManager().openNumberInput(
                player,
                this.plugin.getMessageConfig().getMessage("economy.pay.anvil-title", targetName),
                amount -> queuePayment(player, target, amount));
    }

    private void onShopCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission(SHOP_PLAYER_PERMISSION) && !sender.hasPermission(SHOP_ADMIN_PERMISSION)) {
            this.plugin.getMessageConfig().sendMessage(sender, "general.no-permission");
            return;
        }
        if (!(sender instanceof Player player)) {
            this.plugin.getMessageConfig().sendMessage(sender, "general.player-only");
            return;
        }
        if (args.length != 1 || !"linkitem".equalsIgnoreCase(args[0])) {
            this.plugin.getMessageConfig().sendMessage(sender, "shop.command.usage");
            return;
        }
        player.getInventory().addItem(this.plugin.getSignShopManager().createLinkTool());
        this.plugin.getMessageConfig().sendMessage(player, "shop.link.tool-granted");
    }

    private Collection<String> onShopTabComplete(CommandSender sender, String[] args) {
        if (!sender.hasPermission(SHOP_PLAYER_PERMISSION) && !sender.hasPermission(SHOP_ADMIN_PERMISSION)) {
            return Collections.emptyList();
        }
        if (args.length <= 1) {
            String input = args.length == 0 ? "" : args[0];
            return TabCompleterUtil.filter(SHOP_ACTIONS, input);
        }
        return Collections.emptyList();
    }

    private void onShopsCommand(CommandSender sender, String[] args) {
        if (args.length > 1) {
            this.plugin.getMessageConfig().sendMessage(sender, "shop.list.usage");
            return;
        }
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                this.plugin.getMessageConfig().sendMessage(sender, "general.player-only");
                return;
            }
            if (!this.plugin.getSignShopManager().canUseShops(player)) {
                this.plugin.getMessageConfig().sendMessage(sender, "general.no-permission");
                return;
            }
            sendShopList(sender, player.getUniqueId(), player.getName(), true);
            return;
        }

        if (!sender.hasPermission(SHOP_ADMIN_PERMISSION)) {
            this.plugin.getMessageConfig().sendMessage(sender, "general.no-permission");
            return;
        }

        OfflinePlayer target = resolveTarget(args[0]);
        if (target == null) {
            this.plugin.getMessageConfig().sendMessage(sender, "shop.list.player-not-found", args[0]);
            return;
        }
        sendShopList(sender, target.getUniqueId(), target.getName() == null ? args[0] : target.getName(), false);
    }

    private Collection<String> onShopsTabComplete(CommandSender sender, String[] args) {
        if (args.length <= 1) {
            if (args.length == 0) {
                return Collections.emptyList();
            }
            if (!sender.hasPermission(SHOP_ADMIN_PERMISSION)) {
                return Collections.emptyList();
            }
            return TabCompleterUtil.filterPlayers(args[0]);
        }
        return Collections.emptyList();
    }

    private Collection<String> onPayTabComplete(CommandSender sender, String[] args) {
        if (!canTabUseEconomyCommand(sender)) {
            return Collections.emptyList();
        }
        if (args.length <= 1) {
            List<String> completions = new ArrayList<>(TabCompleterUtil.filterPlayers(args.length == 0 ? "" : args[0]));
            completions.removeIf(name -> name.equalsIgnoreCase(sender.getName()));
            completions.addAll(TabCompleterUtil.filter(PAY_ACTIONS, args.length == 0 ? "" : args[0]));
            return completions.isEmpty() ? Collections.emptyList() : completions;
        }
        return Collections.emptyList();
    }

    private void onHistoryCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            this.plugin.getMessageConfig().sendMessage(sender, "general.player-only");
            return;
        }
        if (!sender.hasPermission(USE_PERMISSION)) {
            this.plugin.getMessageConfig().sendMessage(sender, "general.no-permission");
            return;
        }
        if (args.length > 1) {
            this.plugin.getMessageConfig().sendMessage(sender, "audit.history.usage");
            return;
        }
        if (args.length == 0) {
            this.plugin.getAuditLogManager().openHistoryViewer(player, player, AuditLogFilter.ALL);
            return;
        }
        if (!sender.hasPermission(AUDIT_ADMIN_PERMISSION)) {
            this.plugin.getMessageConfig().sendMessage(sender, "general.no-permission");
            return;
        }

        OfflinePlayer target = resolveTarget(args[0]);
        if (target == null) {
            this.plugin.getMessageConfig().sendMessage(sender, "audit.history.player-not-found", args[0]);
            return;
        }
        this.plugin.getAuditLogManager().openHistoryViewer(player, target, AuditLogFilter.ALL);
    }

    private Collection<String> onHistoryTabComplete(CommandSender sender, String[] args) {
        if (args.length <= 1) {
            if (args.length == 0 || !sender.hasPermission(AUDIT_ADMIN_PERMISSION)) {
                return Collections.emptyList();
            }
            return TabCompleterUtil.filterPlayers(args[0]);
        }
        return Collections.emptyList();
    }

    private void onStatsCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            this.plugin.getMessageConfig().sendMessage(sender, "general.player-only");
            return;
        }
        if (args.length > 1) {
            this.plugin.getMessageConfig().sendMessage(sender, "stats.command.usage");
            return;
        }
        if (args.length == 0) {
            if (!sender.hasPermission(STATS_SELF_PERMISSION) && !sender.hasPermission(STATS_ADMIN_PERMISSION)) {
                this.plugin.getMessageConfig().sendMessage(sender, "general.no-permission");
                return;
            }
            this.plugin.getPlayerStatsViewerManager().openMainGui(
                    player,
                    player,
                    network.tserver.tnexus.manager.PlayerStatsViewerManager.StatsPeriodFilter.ALL_TIME,
                    network.tserver.tnexus.manager.PlayerStatsViewerManager.StatsSortOrder.VALUE_DESC);
            return;
        }

        if (!sender.hasPermission(STATS_OTHERS_PERMISSION) && !sender.hasPermission(STATS_ADMIN_PERMISSION)) {
            this.plugin.getMessageConfig().sendMessage(sender, "general.no-permission");
            return;
        }

        OfflinePlayer target = resolveTarget(args[0]);
        if (target == null) {
            this.plugin.getMessageConfig().sendMessage(sender, "stats.command.player-not-found", args[0]);
            return;
        }
        this.plugin.getPlayerStatsViewerManager().openMainGui(
                player,
                target,
                network.tserver.tnexus.manager.PlayerStatsViewerManager.StatsPeriodFilter.ALL_TIME,
                network.tserver.tnexus.manager.PlayerStatsViewerManager.StatsSortOrder.VALUE_DESC);
    }

    private Collection<String> onStatsTabComplete(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            return Collections.emptyList();
        }
        if (args.length <= 1) {
            if (!sender.hasPermission(STATS_OTHERS_PERMISSION) && !sender.hasPermission(STATS_ADMIN_PERMISSION)) {
                return Collections.emptyList();
            }
            return args.length == 0 ? Collections.emptyList() : TabCompleterUtil.filterPlayers(args[0]);
        }
        return Collections.emptyList();
    }

    private void onServerStatsCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            this.plugin.getMessageConfig().sendMessage(sender, "general.player-only");
            return;
        }
        if (args.length != 0) {
            this.plugin.getMessageConfig().sendMessage(sender, "server-stats.command.usage");
            return;
        }
        this.plugin.getServerStatsManager().openServerStats(player);
    }

    private Collection<String> onBalanceTabComplete(CommandSender sender, String[] args) {
        if (!sender.hasPermission(BALANCE_ADMIN_PERMISSION)) {
            return Collections.emptyList();
        }
        if (args.length <= 1) {
            return TabCompleterUtil.filter(BALANCE_ACTIONS, args.length == 0 ? "" : args[0]);
        }
        if (args.length == 2) {
            return TabCompleterUtil.filterPlayers(args[1]);
        }
        return Collections.emptyList();
    }

    private void queuePayment(Player sender, OfflinePlayer target, double amount) {
        this.plugin.getPaymentManager().queuePayment(sender, target, amount)
                .whenComplete((result, throwable) -> runSync(() -> {
                    if (throwable != null) {
                        this.plugin.getMessageConfig().sendMessage(sender, "economy.pay.failed");
                        return;
                    }
                    handleQueueResult(sender, Objects.requireNonNull(result, "result"), target, amount);
                }));
    }

    private void confirmPayment(Player sender, String token) {
        this.plugin.getPaymentManager().confirmPayment(sender, token)
                .whenComplete((result, throwable) -> runSync(() -> {
                    if (throwable != null) {
                        this.plugin.getMessageConfig().sendMessage(sender, "economy.pay.failed");
                        return;
                    }
                    handleConfirmationResult(sender, Objects.requireNonNull(result, "result"));
                }));
    }

    private void cancelPayment(Player sender, String token) {
        this.plugin.getPaymentManager().cancelPayment(sender, token)
                .whenComplete((result, throwable) -> runSync(() -> {
                    if (throwable != null) {
                        this.plugin.getMessageConfig().sendMessage(sender, "economy.pay.failed");
                        return;
                    }
                    ConfirmationResult confirmationResult = Objects.requireNonNull(result, "result");
                    if (confirmationResult.status() == PaymentManager.ConfirmationStatus.CANCELLED) {
                        this.plugin.getMessageConfig().sendMessage(sender, "economy.pay.cancelled");
                    } else if (confirmationResult.status() == PaymentManager.ConfirmationStatus.EXPIRED) {
                        this.plugin.getMessageConfig().sendMessage(sender, "economy.pay.expired");
                    } else {
                        this.plugin.getMessageConfig().sendMessage(sender, "economy.pay.failed");
                    }
                }));
    }

    private void handleQueueResult(Player sender, QueueResult result, OfflinePlayer target, double amount) {
        switch (result.status()) {
            case INVALID_AMOUNT -> this.plugin.getMessageConfig().sendMessage(sender, "economy.pay.invalid-amount");
            case INSUFFICIENT_FUNDS -> this.plugin.getMessageConfig().sendMessage(sender, "economy.pay.insufficient-funds");
            case QUEUED -> sendPaymentConfirmationMessage(
                    sender,
                    Objects.requireNonNull(result.entry(), "entry").token(),
                    PaymentManager.resolveName(target),
                    amount);
        }
    }

    private void handleConfirmationResult(Player sender, ConfirmationResult result) {
        switch (result.status()) {
            case SUCCESS -> {
                OfflinePlayer target = Objects.requireNonNull(result.target(), "target");
                String formattedAmount = CurrencyFormatter.format(this.plugin, result.amount());
                String targetName = PaymentManager.resolveName(target);
                this.plugin.getMessageConfig().sendMessage(
                        sender,
                        "economy.pay.sender-success",
                        targetName,
                        formattedAmount);
                if (target.isOnline() && target.getPlayer() != null) {
                    this.plugin.getMessageConfig().sendMessage(
                            target.getPlayer(),
                            "economy.pay.receiver-online",
                            sender.getName(),
                            formattedAmount);
                }
            }
            case EXPIRED -> this.plugin.getMessageConfig().sendMessage(sender, "economy.pay.expired");
            case INSUFFICIENT_FUNDS -> this.plugin.getMessageConfig().sendMessage(sender, "economy.pay.insufficient-funds");
            default -> this.plugin.getMessageConfig().sendMessage(sender, "economy.pay.failed");
        }
    }

    private void sendPaymentConfirmationMessage(Player sender, String token, String targetName, double amount) {
        String formattedAmount = CurrencyFormatter.format(this.plugin, amount);
        TextComponent message = LegacyComponentSerializer.legacySection().deserialize(
                this.plugin.getMessageConfig().getMessage("prefix")
                        + this.plugin.getMessageConfig().getMessage("economy.pay.confirmation", targetName, formattedAmount))
                .append(Component.space())
                .append(createActionComponent(
                        this.plugin.getMessageConfig().getMessage("economy.pay.confirm-button"),
                        this.plugin.getMessageConfig().getMessage("economy.pay.confirm-hover"),
                        "/pay confirm " + token))
                .append(Component.space())
                .append(createActionComponent(
                        this.plugin.getMessageConfig().getMessage("economy.pay.cancel-button"),
                        this.plugin.getMessageConfig().getMessage("economy.pay.cancel-hover"),
                        "/tnexus:pay cancel " + token));
        sender.sendMessage(message);
    }

    private Component createActionComponent(String text, String hoverText, String command) {
        return LegacyComponentSerializer.legacySection().deserialize(text)
                .hoverEvent(HoverEvent.showText(LegacyComponentSerializer.legacySection().deserialize(hoverText)))
                .clickEvent(ClickEvent.runCommand(command));
    }

    private OfflinePlayer resolveTarget(String name) {
        Player onlineTarget = this.plugin.getServer().getPlayerExact(name);
        if (onlineTarget != null) {
            return onlineTarget;
        }

        OfflinePlayer offlineTarget = this.plugin.getServer().getOfflinePlayer(name);
        if (offlineTarget.isOnline() || offlineTarget.hasPlayedBefore()) {
            return offlineTarget;
        }
        return null;
    }

    private @Nullable OfflinePlayer resolveBalanceTarget(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }

        Player onlineTarget = this.plugin.getServer().getPlayerExact(name);
        if (onlineTarget != null) {
            return onlineTarget;
        }

        OfflinePlayer offlineTarget = this.plugin.getServer().getOfflinePlayer(name);
        if (offlineTarget.getName() == null || offlineTarget.getName().isBlank()) {
            return null;
        }
        return offlineTarget;
    }

    private void showOwnBalance(CommandSender sender) {
        if (!canUseEconomyCommand(sender)) {
            return;
        }

        Player player = (Player) sender;
        this.plugin.getEconomyManager().getBalance(player.getUniqueId())
                .whenComplete((balance, throwable) -> runSync(() -> {
                    if (throwable != null) {
                        this.plugin.getMessageConfig().sendMessage(player, "economy.pay.failed");
                        return;
                    }
                    this.plugin.getMessageConfig().sendMessage(
                            player,
                            "economy.balance.self",
                            CurrencyFormatter.format(this.plugin, balance));
                }));
    }

    private void handleAdminBalanceCommand(CommandSender sender, String[] args) {
        if (args.length != 3) {
            this.plugin.getMessageConfig().sendMessage(sender, "economy.balance.admin.usage");
            return;
        }

        String action = normalize(args[0]);
        OfflinePlayer target = resolveBalanceTarget(args[1]);
        if (target == null) {
            this.plugin.getMessageConfig().sendMessage(sender, "economy.balance.admin.player-not-found", args[1]);
            return;
        }

        Double amount = parseAmount(args[2]);
        if (amount == null) {
            this.plugin.getMessageConfig().sendMessage(sender, "economy.balance.admin.invalid-amount", args[2]);
            return;
        }
        if (!isValidBalanceAmount(action, amount)) {
            this.plugin.getMessageConfig().sendMessage(sender, "economy.balance.admin.invalid-amount", args[2]);
            return;
        }

        switch (action) {
            case "add" -> adjustBalance(sender, target, amount);
            case "take" -> takeBalance(sender, target, amount);
            case "set" -> setBalance(sender, target, amount);
            default -> this.plugin.getMessageConfig().sendMessage(sender, "economy.balance.admin.usage");
        }
    }

    private void adjustBalance(CommandSender sender, OfflinePlayer target, double amount) {
        CompletableFuture<Boolean> operation = this.plugin.getEconomyManager().deposit(target.getUniqueId(), amount);
        completeAdminBalanceChange(
                sender,
                target,
                operation,
                TransactionType.DEPOSIT,
                amount,
                "economy.balance.admin.add.success",
                buildAuditDescription(sender, "balance add", amount));
    }

    private void takeBalance(CommandSender sender, OfflinePlayer target, double amount) {
        CompletableFuture<Boolean> operation = this.plugin.getEconomyManager().withdraw(target.getUniqueId(), amount);
        completeAdminBalanceChange(
                sender,
                target,
                operation,
                TransactionType.WITHDRAW,
                amount,
                "economy.balance.admin.take.success",
                buildAuditDescription(sender, "balance take", amount));
    }

    private void setBalance(CommandSender sender, OfflinePlayer target, double amount) {
        this.plugin.getEconomyManager().getBalance(target.getUniqueId())
                .thenCompose(beforeBalance -> this.plugin.getEconomyManager().setBalance(target.getUniqueId(), amount)
                        .thenCompose(updated -> {
                            if (!updated) {
                                return CompletableFuture.completedFuture(AdminBalanceResult.failed());
                            }
                            return this.plugin.getEconomyManager().getBalance(target.getUniqueId())
                                    .thenApply(afterBalance -> {
                                        double delta = afterBalance - beforeBalance;
                                        TransactionType type = delta < 0.0D ? TransactionType.WITHDRAW : TransactionType.DEPOSIT;
                                        return AdminBalanceResult.success(type, Math.abs(delta), afterBalance);
                                    });
                        }))
                .whenComplete((result, throwable) -> runSync(() -> {
                    if (throwable != null) {
                        logAdminBalanceFailure("Failed to set balance for " + target.getUniqueId(), throwable);
                        this.plugin.getMessageConfig().sendMessage(sender, "economy.balance.admin.failed");
                        return;
                    }
                    handleAdminBalanceResult(
                            sender,
                            target,
                            Objects.requireNonNull(result, "result"),
                            "economy.balance.admin.set.success",
                            buildAuditDescription(sender, "balance set", amount));
                }));
    }

    private void completeAdminBalanceChange(
            CommandSender sender,
            OfflinePlayer target,
            CompletableFuture<Boolean> operation,
            TransactionType type,
            double amount,
            String successMessageKey,
            String auditDescription) {
        operation.thenCompose(updated -> {
            if (!updated) {
                return CompletableFuture.completedFuture(AdminBalanceResult.failed());
            }
            return this.plugin.getEconomyManager().getBalance(target.getUniqueId())
                    .thenApply(balanceAfter -> AdminBalanceResult.success(type, amount, balanceAfter));
        }).whenComplete((result, throwable) -> runSync(() -> {
            if (throwable != null) {
                logAdminBalanceFailure("Failed to update balance for " + target.getUniqueId(), throwable);
                this.plugin.getMessageConfig().sendMessage(sender, "economy.balance.admin.failed");
                return;
            }
            handleAdminBalanceResult(
                    sender,
                    target,
                    Objects.requireNonNull(result, "result"),
                    successMessageKey,
                    auditDescription);
        }));
    }

    private void handleAdminBalanceResult(
            CommandSender sender,
            OfflinePlayer target,
            AdminBalanceResult result,
            String successMessageKey,
            String auditDescription) {
        if (!result.success()) {
            this.plugin.getMessageConfig().sendMessage(sender, "economy.balance.admin.failed");
            return;
        }

        this.plugin.getAuditLogManager().recordEntry(
                target.getUniqueId(),
                result.transactionType(),
                result.amount(),
                result.balanceAfter(),
                auditDescription,
                resolveCounterpartUuid(sender))
                .whenComplete((ignored, throwable) -> runSync(() -> {
                    if (throwable != null) {
                        logAdminBalanceFailure("Failed to write admin balance audit for " + target.getUniqueId(), throwable);
                        this.plugin.getMessageConfig().sendMessage(sender, "economy.balance.admin.failed");
                        return;
                    }
                    this.plugin.getMessageConfig().sendMessage(
                            sender,
                            successMessageKey,
                            resolveTargetName(target),
                            CurrencyFormatter.format(this.plugin, result.amount()),
                            CurrencyFormatter.format(this.plugin, result.balanceAfter()));
                }));
    }

    private @Nullable Double parseAmount(String rawAmount) {
        try {
            double parsed = Double.parseDouble(rawAmount);
            return Double.isFinite(parsed) ? parsed : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private boolean isValidBalanceAmount(String action, double amount) {
        if (amount < 0.0D) {
            return false;
        }
        if ("add".equals(action) || "take".equals(action)) {
            return amount > 0.0D;
        }
        return true;
    }

    private String buildAuditDescription(CommandSender sender, String action, double amount) {
        return action + " by " + sender.getName() + " (" + amount + ")";
    }

    private String resolveTargetName(OfflinePlayer target) {
        return target.getName() == null || target.getName().isBlank()
                ? target.getUniqueId().toString()
                : target.getName();
    }

    private @Nullable UUID resolveCounterpartUuid(CommandSender sender) {
        if (sender instanceof Player player) {
            return player.getUniqueId();
        }
        return null;
    }

    private void logAdminBalanceFailure(String message, Throwable throwable) {
        this.plugin.getLogger().log(Level.SEVERE, message, throwable);
    }

    private boolean canUseEconomyCommand(CommandSender sender) {
        if (!sender.hasPermission(USE_PERMISSION)) {
            this.plugin.getMessageConfig().sendMessage(sender, "general.no-permission");
            return false;
        }
        if (!(sender instanceof Player)) {
            this.plugin.getMessageConfig().sendMessage(sender, "general.player-only");
            return false;
        }
        return true;
    }

    private boolean canTabUseEconomyCommand(CommandSender sender) {
        return sender.hasPermission(USE_PERMISSION) && sender instanceof Player;
    }

    private void sendShopList(CommandSender sender, java.util.UUID ownerUuid, String ownerName, boolean selfView) {
        List<network.tserver.tnexus.manager.SignShop> shops = this.plugin.getSignShopManager().getOwnedShops(ownerUuid);
        if (shops.isEmpty()) {
            this.plugin.getMessageConfig().sendMessage(
                    sender,
                    selfView ? "shop.list.empty-self" : "shop.list.empty-other",
                    ownerName);
            return;
        }

        this.plugin.getMessageConfig().sendMessage(
                sender,
                selfView ? "shop.list.header-self" : "shop.list.header-other",
                ownerName);
        for (network.tserver.tnexus.manager.SignShop shop : shops) {
            this.plugin.getMessageConfig().sendMessage(
                    sender,
                    "shop.list.entry",
                    shop.getId(),
                    shop.getItemName(),
                    shop.getLinkedChestCount(),
                    shop.getSignPosition().worldName(),
                    shop.getSignPosition().x(),
                    shop.getSignPosition().y(),
                    shop.getSignPosition().z());
        }
    }

    private void runSync(Runnable runnable) {
        this.plugin.getServer().getScheduler().runTask(this.plugin, runnable);
    }

    private record AdminBalanceResult(
            boolean success,
            @Nullable TransactionType transactionType,
            double amount,
            double balanceAfter) {

        private static AdminBalanceResult success(TransactionType transactionType, double amount, double balanceAfter) {
            return new AdminBalanceResult(true, transactionType, amount, balanceAfter);
        }

        private static AdminBalanceResult failed() {
            return new AdminBalanceResult(false, null, 0.0D, 0.0D);
        }
    }
}
