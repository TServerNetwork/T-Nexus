package network.tserver.tnexus.command;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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
import network.tserver.tnexus.manager.PaymentManager;
import network.tserver.tnexus.manager.PaymentManager.ConfirmationResult;
import network.tserver.tnexus.manager.PaymentManager.QueueResult;
import network.tserver.tnexus.util.CurrencyFormatter;
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
    private static final String PAY_COMMAND_NAME = "pay";
    private static final String SHOP_COMMAND_NAME = "shop";
    private static final String SHOPS_COMMAND_NAME = "shops";
    private static final String USE_PERMISSION = "tnexus.use";
    private static final String SHOP_ADMIN_PERMISSION = "tnexus.shop.admin";

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

        commands.register(BALANCE_COMMAND_NAME, "Show your balance", List.of("bal"), new BasicCommand() {
            @Override
            public void execute(CommandSourceStack commandSourceStack, String[] args) {
                CommandManager.this.onBalanceCommand(commandSourceStack.getSender(), args);
            }
        });

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
            return completions;
        }

        BaseCommand subcommand = this.subcommands.get(normalize(args[0]));
        if (subcommand == null || !canTabComplete(sender, subcommand)) {
            return List.of();
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
        if (!canUseEconomyCommand(sender)) {
            return;
        }
        if (args.length > 0) {
            this.plugin.getMessageConfig().sendMessage(sender, "economy.balance.usage");
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
        if (!this.plugin.getSignShopManager().canUseShops(sender)) {
            this.plugin.getMessageConfig().sendMessage(sender, "general.no-permission");
            return;
        }
        if (!(sender instanceof Player player)) {
            this.plugin.getMessageConfig().sendMessage(sender, "general.player-only");
            return;
        }
        if (args.length != 1 || !"link".equalsIgnoreCase(args[0])) {
            this.plugin.getMessageConfig().sendMessage(sender, "shop.command.usage");
            return;
        }
        this.plugin.getSignShopManager().beginLinkMode(player);
    }

    private Collection<String> onShopTabComplete(CommandSender sender, String[] args) {
        if (!this.plugin.getSignShopManager().canUseShops(sender)) {
            return List.of();
        }
        if (args.length <= 1) {
            String input = args.length == 0 ? "" : normalize(args[0]);
            return List.of("link").stream()
                    .filter(option -> option.startsWith(input))
                    .toList();
        }
        return List.of();
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
                return List.of();
            }
            if (!sender.hasPermission(SHOP_ADMIN_PERMISSION)) {
                return List.of();
            }
            String input = normalize(args[0]);
            List<String> completions = new ArrayList<>();
            for (Player onlinePlayer : this.plugin.getServer().getOnlinePlayers()) {
                String name = onlinePlayer.getName();
                if (normalize(name).startsWith(input)) {
                    completions.add(name);
                }
            }
            return completions;
        }
        return List.of();
    }

    private Collection<String> onPayTabComplete(CommandSender sender, String[] args) {
        if (!canTabUseEconomyCommand(sender)) {
            return List.of();
        }
        if (args.length <= 1) {
            String input = args.length == 0 ? "" : normalize(args[0]);
            List<String> completions = new ArrayList<>();
            for (Player onlinePlayer : this.plugin.getServer().getOnlinePlayers()) {
                String name = onlinePlayer.getName();
                if (!name.equalsIgnoreCase(sender.getName()) && normalize(name).startsWith(input)) {
                    completions.add(name);
                }
            }
            return completions;
        }
        return List.of();
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
                        "/tnexus:pay confirm " + token))
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
                    shop.getSignPosition().worldName(),
                    shop.getSignPosition().x(),
                    shop.getSignPosition().y(),
                    shop.getSignPosition().z());
        }
    }

    private void runSync(Runnable runnable) {
        this.plugin.getServer().getScheduler().runTask(this.plugin, runnable);
    }
}
