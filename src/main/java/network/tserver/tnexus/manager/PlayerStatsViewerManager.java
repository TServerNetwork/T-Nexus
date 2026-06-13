package network.tserver.tnexus.manager;

import java.text.NumberFormat;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.database.repository.BlockStatsDelta;
import network.tserver.tnexus.database.repository.EntityDamageDelta;
import network.tserver.tnexus.database.repository.ItemStatsDelta;
import network.tserver.tnexus.database.repository.PlayerStatsViewRepository;
import network.tserver.tnexus.database.repository.PlayerStatsViewRepository.FavoriteMutation;
import network.tserver.tnexus.database.repository.PlayerStatsViewRepository.FavoriteMutationStatus;
import network.tserver.tnexus.database.repository.PlayerStatsViewRepository.RawPlayerStatsData;
import network.tserver.tnexus.database.repository.TransactionRepository.TransactionType;
import network.tserver.tnexus.gui.player.PlayerStatsCategoryGui;
import network.tserver.tnexus.gui.player.PlayerStatsCombatDetailGui;
import network.tserver.tnexus.gui.player.PlayerStatsItemDetailGui;
import network.tserver.tnexus.gui.player.PlayerStatsMainGui;
import network.tserver.tnexus.util.CurrencyFormatter;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Loads, formats, and opens player stats viewer GUIs.
 */
public final class PlayerStatsViewerManager {

    private static final Locale NUMBER_LOCALE = Locale.JAPAN;
    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm").withZone(ZoneId.systemDefault());

    private final TNexus plugin;
    private final PlayerStatsViewRepository repository;
    private final Clock clock;

    /**
     * Creates a new manager.
     *
     * @param plugin plugin instance
     * @param repository view repository
     */
    public PlayerStatsViewerManager(TNexus plugin, PlayerStatsViewRepository repository) {
        this(plugin, repository, Clock.systemDefaultZone());
    }

    PlayerStatsViewerManager(TNexus plugin, PlayerStatsViewRepository repository, Clock clock) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Opens the main stats GUI for the viewer and target.
     *
     * @param viewer viewing player
     * @param target target player
     * @param periodFilter active period filter
     * @param sortOrder active sort order
     */
    public void openMainGui(
            Player viewer,
            OfflinePlayer target,
            StatsPeriodFilter periodFilter,
            StatsSortOrder sortOrder) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(target, "target");
        loadSnapshot(viewer.getUniqueId(), target, periodFilter)
                .whenComplete((snapshot, throwable) -> Bukkit.getScheduler().runTask(this.plugin, () -> {
                    if (throwable != null) {
                        this.plugin.getMessageConfig().sendMessage(viewer, "stats.command.load-failed");
                        return;
                    }
                    new PlayerStatsMainGui(
                            this.plugin,
                            this,
                            viewer,
                            Objects.requireNonNull(snapshot, "snapshot"),
                            periodFilter,
                            sortOrder).open();
                }));
    }

    /**
     * Opens a category stats GUI for the viewer and target.
     *
     * @param viewer viewing player
     * @param target target player
     * @param category selected category
     * @param periodFilter active period filter
     * @param sortOrder active sort order
     */
    public void openCategoryGui(
            Player viewer,
            OfflinePlayer target,
            StatsCategory category,
            StatsPeriodFilter periodFilter,
            StatsSortOrder sortOrder) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(category, "category");
        loadSnapshot(viewer.getUniqueId(), target, periodFilter)
                .whenComplete((snapshot, throwable) -> Bukkit.getScheduler().runTask(this.plugin, () -> {
                    if (throwable != null) {
                        this.plugin.getMessageConfig().sendMessage(viewer, "stats.command.load-failed");
                        return;
                    }
                    new PlayerStatsCategoryGui(
                            this.plugin,
                            this,
                            viewer,
                            Objects.requireNonNull(snapshot, "snapshot"),
                            category,
                            periodFilter,
                            sortOrder).open();
                }));
    }

    /**
     * Opens a combat detail sub-GUI for the viewer and target.
     *
     * @param viewer viewing player
     * @param target target player
     * @param detailType selected combat detail type
     * @param periodFilter active period filter
     * @param sortOrder active sort order
     */
    public void openCombatDetailGui(
            Player viewer,
            OfflinePlayer target,
            CombatDetailType detailType,
            StatsPeriodFilter periodFilter,
            StatsSortOrder sortOrder) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(detailType, "detailType");
        loadSnapshot(viewer.getUniqueId(), target, periodFilter)
                .whenComplete((snapshot, throwable) -> Bukkit.getScheduler().runTask(this.plugin, () -> {
                    if (throwable != null) {
                        this.plugin.getMessageConfig().sendMessage(viewer, "stats.command.load-failed");
                        return;
                    }
                    new PlayerStatsCombatDetailGui(
                            this.plugin,
                            this,
                            viewer,
                            Objects.requireNonNull(snapshot, "snapshot"),
                            detailType,
                            periodFilter,
                            sortOrder).open();
                }));
    }

    /**
     * Opens the per-item pickup/drop detail GUI for the viewer and target.
     *
     * @param viewer viewing player
     * @param target target player
     * @param periodFilter active period filter
     * @param sortOrder active sort order
     */
    public void openItemDetailGui(
            Player viewer,
            OfflinePlayer target,
            StatsPeriodFilter periodFilter,
            StatsSortOrder sortOrder) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(target, "target");
        loadSnapshot(viewer.getUniqueId(), target, periodFilter)
                .whenComplete((snapshot, throwable) -> Bukkit.getScheduler().runTask(this.plugin, () -> {
                    if (throwable != null) {
                        this.plugin.getMessageConfig().sendMessage(viewer, "stats.command.load-failed");
                        return;
                    }
                    new PlayerStatsItemDetailGui(
                            this.plugin,
                            this,
                            viewer,
                            Objects.requireNonNull(snapshot, "snapshot"),
                            periodFilter,
                            sortOrder).open();
                }));
    }

    /**
     * Loads a formatted snapshot for the target.
     *
     * @param viewerId viewer UUID used for favorites
     * @param target target player
     * @param periodFilter active period filter
     * @return completion future
     */
    public CompletableFuture<PlayerStatsSnapshot> loadSnapshot(
            UUID viewerId,
            OfflinePlayer target,
            StatsPeriodFilter periodFilter) {
        Objects.requireNonNull(viewerId, "viewerId");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(periodFilter, "periodFilter");
        LocalDate periodStartDate = resolvePeriodStartDate(periodFilter);
        Instant periodStartInstant = resolvePeriodStartInstant(periodStartDate);
        CompletableFuture<RawPlayerStatsData> statsFuture =
                this.repository.loadSnapshot(viewerId, target.getUniqueId(), periodStartDate, periodStartInstant);
        CompletableFuture<Double> balanceFuture =
                this.plugin.getEconomyManager().getBalance(target.getUniqueId());
        return statsFuture.thenCombine(balanceFuture, (stats, balance) -> buildSnapshot(target, periodFilter, stats, balance));
    }

    /**
     * Toggles a favorite stat entry for the viewer.
     *
     * @param viewerId viewer UUID
     * @param snapshot current snapshot
     * @param entry target entry
     * @return completion future
     */
    public CompletableFuture<FavoriteToggleResult> toggleFavorite(
            UUID viewerId,
            PlayerStatsSnapshot snapshot,
            StatsEntry entry) {
        Objects.requireNonNull(viewerId, "viewerId");
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(entry, "entry");
        return this.repository.toggleFavorite(viewerId, entry.key())
                .thenApply(mutation -> applyFavoriteMutation(snapshot, mutation));
    }

    /**
     * Returns the localized category label.
     *
     * @param category category
     * @return localized label
     */
    public String getCategoryLabel(StatsCategory category) {
        return this.plugin.getMessageConfig().getMessage(category.labelKey());
    }

    /**
     * Returns the localized combat detail label.
     *
     * @param detailType detail type
     * @return localized label
     */
    public String getCombatDetailLabel(CombatDetailType detailType) {
        return this.plugin.getMessageConfig().getMessage(
                "stats.dynamic.combat.name",
                prettifyKey(detailType.name()));
    }

    /**
     * Returns the localized period label.
     *
     * @param filter filter
     * @return localized label
     */
    public String getPeriodLabel(StatsPeriodFilter filter) {
        return this.plugin.getMessageConfig().getMessage(filter.labelKey());
    }

    /**
     * Returns the localized sort-order label.
     *
     * @param sortOrder sort order
     * @return localized label
     */
    public String getSortLabel(StatsSortOrder sortOrder) {
        return this.plugin.getMessageConfig().getMessage(sortOrder.labelKey());
    }

    /**
     * Resolves a combat summary key to its target sub-GUI.
     *
     * @param entryKey summary entry key
     * @return detail type or {@code null}
     */
    public @Nullable CombatDetailType resolveCombatDetailType(String entryKey) {
        return switch (entryKey) {
            case "COMBAT_SUMMARY_MOB_DAMAGE" -> CombatDetailType.MOB_DAMAGE;
            case "COMBAT_SUMMARY_PLAYER_DAMAGE" -> CombatDetailType.PLAYER_DAMAGE;
            default -> null;
        };
    }

    private PlayerStatsSnapshot buildSnapshot(
            OfflinePlayer target,
            StatsPeriodFilter periodFilter,
            RawPlayerStatsData rawData,
            double currentBalance) {
        EnumMap<StatsCategory, List<StatsEntry>> entriesByCategory = new EnumMap<>(StatsCategory.class);
        EnumMap<CombatDetailType, List<StatsEntry>> combatDetailEntries = new EnumMap<>(CombatDetailType.class);
        List<StatsEntry> itemDetailEntries = new ArrayList<>();
        for (StatsCategory category : StatsCategory.values()) {
            entriesByCategory.put(category, new ArrayList<>());
        }
        for (CombatDetailType detailType : CombatDetailType.values()) {
            combatDetailEntries.put(detailType, new ArrayList<>());
        }

        addGeneralEntries(entriesByCategory.get(StatsCategory.GENERAL), target, rawData.playerSummary());
        addEconomyEntries(entriesByCategory.get(StatsCategory.ECONOMY), rawData.transactionSummary(), currentBalance);
        addBlockEntries(entriesByCategory.get(StatsCategory.BLOCKS), rawData.blockStats());
        addCombatEntries(
                entriesByCategory.get(StatsCategory.COMBAT),
                combatDetailEntries,
                rawData.entityDamageStats(),
                rawData.killStats());
        addActivityEntries(entriesByCategory.get(StatsCategory.ACTIVITY), rawData);
        itemDetailEntries.addAll(createItemDetailEntries(rawData.itemStats()));

        return new PlayerStatsSnapshot(
                target.getUniqueId(),
                resolveTargetName(target),
                resolveFirstLogin(target, rawData.playerSummary().firstLogin()),
                periodFilter,
                entriesByCategory,
                combatDetailEntries,
                itemDetailEntries,
                rawData.favorites());
    }

    private void addGeneralEntries(
            List<StatsEntry> entries,
            OfflinePlayer target,
            PlayerStatsViewRepository.PlayerSummary summary) {
        entries.add(createFixedEntry(
                "GENERAL_PLAY_TIME",
                StatsCategory.GENERAL,
                Material.CLOCK,
                "stats.labels.general.play-time",
                formatDuration(summary.playTimeSeconds()),
                summary.playTimeSeconds()));
        entries.add(createFixedEntry(
                "GENERAL_DISTANCE",
                StatsCategory.GENERAL,
                Material.LEATHER_BOOTS,
                "stats.labels.general.distance",
                formatDecimal(summary.distance()) + " m",
                summary.distance()));
        entries.add(createFixedEntry(
                "GENERAL_DEATHS",
                StatsCategory.GENERAL,
                Material.SKELETON_SKULL,
                "stats.labels.general.deaths",
                formatWholeNumber(summary.deaths()),
                summary.deaths()));
        entries.add(createFixedEntry(
                "GENERAL_RESPAWNS",
                StatsCategory.GENERAL,
                Material.TOTEM_OF_UNDYING,
                "stats.labels.general.respawns",
                formatWholeNumber(summary.respawns()),
                summary.respawns()));
        entries.add(createFixedEntry(
                "GENERAL_CHAT_COUNT",
                StatsCategory.GENERAL,
                Material.PAPER,
                "stats.labels.general.chat-count",
                formatWholeNumber(summary.chatCount()),
                summary.chatCount()));
        entries.add(createFixedEntry(
                "GENERAL_SLEEP_COUNT",
                StatsCategory.GENERAL,
                Material.RED_BED,
                "stats.labels.general.sleep-count",
                formatWholeNumber(summary.sleepCount()),
                summary.sleepCount()));
        entries.add(createFixedEntry(
                "GENERAL_PORTAL_COUNT",
                StatsCategory.GENERAL,
                Material.OBSIDIAN,
                "stats.labels.general.portal-count",
                formatWholeNumber(summary.portalCount()),
                summary.portalCount()));
        Instant firstLogin = resolveFirstLogin(target, summary.firstLogin());
        entries.add(new StatsEntry(
                "GENERAL_FIRST_LOGIN",
                StatsCategory.GENERAL,
                Material.PLAYER_HEAD,
                this.plugin.getMessageConfig().getMessage("stats.labels.general.first-login"),
                firstLogin == null
                        ? this.plugin.getMessageConfig().getMessage("stats.values.unavailable")
                        : DATE_TIME_FORMAT.format(firstLogin),
                List.of(),
                firstLogin == null ? 0.0D : firstLogin.getEpochSecond(),
                target.getUniqueId()));
    }

    private void addEconomyEntries(
            List<StatsEntry> entries,
            PlayerStatsViewRepository.TransactionSummary summary,
            double currentBalance) {
        entries.add(createFixedEntry(
                "ECONOMY_BALANCE",
                StatsCategory.ECONOMY,
                Material.EMERALD,
                "stats.labels.economy.balance",
                CurrencyFormatter.format(this.plugin, currentBalance),
                currentBalance));
        entries.add(createFixedEntry(
                "ECONOMY_DEPOSIT_AMOUNT",
                StatsCategory.ECONOMY,
                Material.SUNFLOWER,
                "stats.labels.economy.deposit-amount",
                CurrencyFormatter.format(this.plugin, summary.amounts().getOrDefault(TransactionType.DEPOSIT, 0.0D)),
                summary.amounts().getOrDefault(TransactionType.DEPOSIT, 0.0D)));
        entries.add(createFixedEntry(
                "ECONOMY_WITHDRAW_AMOUNT",
                StatsCategory.ECONOMY,
                Material.REDSTONE,
                "stats.labels.economy.withdraw-amount",
                CurrencyFormatter.format(this.plugin, summary.amounts().getOrDefault(TransactionType.WITHDRAW, 0.0D)),
                summary.amounts().getOrDefault(TransactionType.WITHDRAW, 0.0D)));
        entries.add(createFixedEntry(
                "ECONOMY_PAYMENT_RECEIVED_COUNT",
                StatsCategory.ECONOMY,
                Material.BOOK,
                "stats.labels.economy.payment-received-count",
                formatWholeNumber(summary.counts().getOrDefault(TransactionType.PAYMENT_RECEIVED, 0)),
                summary.counts().getOrDefault(TransactionType.PAYMENT_RECEIVED, 0)));
        entries.add(createFixedEntry(
                "ECONOMY_PAYMENT_SENT_COUNT",
                StatsCategory.ECONOMY,
                Material.MAP,
                "stats.labels.economy.payment-sent-count",
                formatWholeNumber(summary.counts().getOrDefault(TransactionType.PAYMENT_SENT, 0)),
                summary.counts().getOrDefault(TransactionType.PAYMENT_SENT, 0)));
        entries.add(createFixedEntry(
                "ECONOMY_SHOP_BUY_COUNT",
                StatsCategory.ECONOMY,
                Material.CHEST,
                "stats.labels.economy.shop-buy-count",
                formatWholeNumber(summary.counts().getOrDefault(TransactionType.SHOP_BUY, 0)),
                summary.counts().getOrDefault(TransactionType.SHOP_BUY, 0)));
        entries.add(createFixedEntry(
                "ECONOMY_SHOP_SELL_COUNT",
                StatsCategory.ECONOMY,
                Material.GOLD_INGOT,
                "stats.labels.economy.shop-sell-count",
                formatWholeNumber(summary.counts().getOrDefault(TransactionType.SHOP_SELL, 0)),
                summary.counts().getOrDefault(TransactionType.SHOP_SELL, 0)));
        entries.add(createFixedEntry(
                "ECONOMY_TOTAL_VOLUME",
                StatsCategory.ECONOMY,
                Material.GOLD_BLOCK,
                "stats.labels.economy.total-volume",
                CurrencyFormatter.format(this.plugin, summary.totalVolume()),
                summary.totalVolume()));
    }

    private void addBlockEntries(List<StatsEntry> entries, Map<String, BlockStatsDelta> blockStats) {
        for (Map.Entry<String, BlockStatsDelta> entry : blockStats.entrySet()) {
            String materialName = entry.getKey();
            BlockStatsDelta delta = entry.getValue();
            double total = delta.placedCount() + delta.brokenCount();
            entries.add(new StatsEntry(
                    "BLOCK:" + materialName,
                    StatsCategory.BLOCKS,
                    resolveDisplayMaterial(materialName, Material.STONE),
                    this.plugin.getMessageConfig().getMessage(
                            "stats.dynamic.block.name",
                            prettifyKey(materialName)),
                    formatWholeNumber((long) total),
                    List.of(
                            this.plugin.getMessageConfig().getMessage(
                                    "stats.dynamic.block.placed",
                                    formatWholeNumber(delta.placedCount())),
                            this.plugin.getMessageConfig().getMessage(
                                    "stats.dynamic.block.broken",
                                    formatWholeNumber(delta.brokenCount()))),
                    total,
                    null));
        }
    }

    private void addCombatEntries(
            List<StatsEntry> summaryEntries,
            EnumMap<CombatDetailType, List<StatsEntry>> combatDetailEntries,
            Map<String, EntityDamageDelta> entityDamageStats,
            Map<String, Integer> killStats) {
        Map<String, CombatAggregate> mobAggregates = new LinkedHashMap<>();
        Map<String, CombatAggregate> playerAggregates = new LinkedHashMap<>();
        mergeCombatAggregates(mobAggregates, playerAggregates, entityDamageStats, killStats);

        List<StatsEntry> mobEntries = createCombatDetailEntries(mobAggregates, false);
        List<StatsEntry> playerEntries = createCombatDetailEntries(playerAggregates, true);

        combatDetailEntries.put(CombatDetailType.MOB_DAMAGE, mobEntries);
        combatDetailEntries.put(CombatDetailType.PLAYER_DAMAGE, playerEntries);

        summaryEntries.add(createCombatSummaryEntry(
                "COMBAT_SUMMARY_MOB_DAMAGE",
                Material.ZOMBIE_HEAD,
                prettifyKey(CombatDetailType.MOB_DAMAGE.name()),
                mobEntries,
                sumCombatKills(mobAggregates),
                sumCombatDealt(mobAggregates),
                sumCombatTaken(mobAggregates)));
        summaryEntries.add(createCombatSummaryEntry(
                "COMBAT_SUMMARY_PLAYER_DAMAGE",
                Material.IRON_SWORD,
                prettifyKey(CombatDetailType.PLAYER_DAMAGE.name()),
                playerEntries,
                sumCombatKills(playerAggregates),
                sumCombatDealt(playerAggregates),
                sumCombatTaken(playerAggregates)));
    }

    private void mergeCombatAggregates(
            Map<String, CombatAggregate> mobAggregates,
            Map<String, CombatAggregate> playerAggregates,
            Map<String, EntityDamageDelta> entityDamageStats,
            Map<String, Integer> killStats) {
        for (Map.Entry<String, EntityDamageDelta> entry : entityDamageStats.entrySet()) {
            Map<String, CombatAggregate> targetMap = isUuid(entry.getKey()) ? playerAggregates : mobAggregates;
            targetMap.put(
                    entry.getKey(),
                    new CombatAggregate(
                            entry.getKey(),
                            killStats.getOrDefault(entry.getKey(), 0),
                            entry.getValue().damageDealt(),
                            entry.getValue().damageTaken()));
        }
        for (Map.Entry<String, Integer> entry : killStats.entrySet()) {
            Map<String, CombatAggregate> targetMap = isUuid(entry.getKey()) ? playerAggregates : mobAggregates;
            targetMap.computeIfAbsent(entry.getKey(), key -> new CombatAggregate(key, 0, 0.0D, 0.0D))
                    .setKillCount(entry.getValue());
        }
    }

    private List<StatsEntry> createCombatDetailEntries(Map<String, CombatAggregate> aggregates, boolean playerEntries) {
        List<StatsEntry> entries = new ArrayList<>();
        for (CombatAggregate aggregate : aggregates.values()) {
            double sortValue = aggregate.killCount + aggregate.damageDealt + aggregate.damageTaken;
            UUID playerHeadId = playerEntries ? UUID.fromString(aggregate.key) : null;
            entries.add(new StatsEntry(
                    (playerEntries ? "COMBAT_PLAYER:" : "COMBAT_MOB:") + aggregate.key,
                    StatsCategory.COMBAT,
                    playerEntries ? Material.PLAYER_HEAD : resolveCombatMaterial(aggregate.key),
                    this.plugin.getMessageConfig().getMessage(
                            "stats.dynamic.combat.name",
                            resolveEntityName(aggregate.key)),
                    formatWholeNumber(aggregate.killCount),
                    List.of(
                            this.plugin.getMessageConfig().getMessage(
                                    "stats.dynamic.combat.kills",
                                    formatWholeNumber(aggregate.killCount)),
                            this.plugin.getMessageConfig().getMessage(
                                    "stats.dynamic.combat.damage-dealt",
                                    formatDecimal(aggregate.damageDealt)),
                            this.plugin.getMessageConfig().getMessage(
                                    "stats.dynamic.combat.damage-taken",
                                    formatDecimal(aggregate.damageTaken))),
                    sortValue,
                    playerHeadId));
        }
        return List.copyOf(entries);
    }

    private StatsEntry createCombatSummaryEntry(
            String key,
            Material material,
            String label,
            List<StatsEntry> detailEntries,
            double killCount,
            double damageDealt,
            double damageTaken) {
        return new StatsEntry(
                key,
                StatsCategory.COMBAT,
                material,
                this.plugin.getMessageConfig().getMessage("stats.dynamic.combat.name", label),
                formatWholeNumber(detailEntries.size()),
                List.of(
                        this.plugin.getMessageConfig().getMessage(
                                "stats.dynamic.combat.kills",
                                formatWholeNumber(killCount)),
                        this.plugin.getMessageConfig().getMessage(
                                "stats.dynamic.combat.damage-dealt",
                                formatDecimal(damageDealt)),
                        this.plugin.getMessageConfig().getMessage(
                                "stats.dynamic.combat.damage-taken",
                                formatDecimal(damageTaken))),
                killCount + damageDealt + damageTaken,
                null);
    }

    private void addActivityEntries(List<StatsEntry> entries, RawPlayerStatsData rawData) {
        entries.add(createFixedEntry(
                "ACTIVITY_CRAFT_TOTAL",
                StatsCategory.ACTIVITY,
                Material.CRAFTING_TABLE,
                "stats.labels.activity.craft-total",
                formatWholeNumber(sumIntegers(rawData.craftStats())),
                sumIntegers(rawData.craftStats())));
        entries.add(createFixedEntry(
                "ACTIVITY_SMELT_TOTAL",
                StatsCategory.ACTIVITY,
                Material.FURNACE,
                "stats.labels.activity.smelt-total",
                formatWholeNumber(sumIntegers(rawData.smeltStats())),
                sumIntegers(rawData.smeltStats())));
        entries.add(createFixedEntry(
                "ACTIVITY_BREW_TOTAL",
                StatsCategory.ACTIVITY,
                Material.BREWING_STAND,
                "stats.labels.activity.brew-total",
                formatWholeNumber(rawData.playerSummary().brewCount()),
                rawData.playerSummary().brewCount()));
        entries.add(createFixedEntry(
                "ACTIVITY_ENCHANT_TOTAL",
                StatsCategory.ACTIVITY,
                Material.ENCHANTING_TABLE,
                "stats.labels.activity.enchant-total",
                formatWholeNumber(sumIntegers(rawData.enchantStats())),
                sumIntegers(rawData.enchantStats())));
        entries.add(createFixedEntry(
                "ACTIVITY_HARVEST_TOTAL",
                StatsCategory.ACTIVITY,
                Material.WHEAT,
                "stats.labels.activity.harvest-total",
                formatWholeNumber(sumIntegers(rawData.harvestStats())),
                sumIntegers(rawData.harvestStats())));
        entries.add(createFixedEntry(
                "ACTIVITY_BREED_TOTAL",
                StatsCategory.ACTIVITY,
                Material.HAY_BLOCK,
                "stats.labels.activity.breed-total",
                formatWholeNumber(sumIntegers(rawData.breedStats())),
                sumIntegers(rawData.breedStats())));
        entries.add(createFixedEntry(
                "ACTIVITY_FISH_TOTAL",
                StatsCategory.ACTIVITY,
                Material.FISHING_ROD,
                "stats.labels.activity.fish-total",
                formatWholeNumber(sumIntegers(rawData.fishStats())),
                sumIntegers(rawData.fishStats())));
        entries.add(createFixedEntry(
                "ACTIVITY_PICKUP_TOTAL",
                StatsCategory.ACTIVITY,
                Material.HOPPER,
                "stats.labels.activity.pickup-total",
                formatWholeNumber(sumItemPickups(rawData.itemStats())),
                sumItemPickups(rawData.itemStats())));
        entries.add(createFixedEntry(
                "ACTIVITY_DROP_TOTAL",
                StatsCategory.ACTIVITY,
                Material.DROPPER,
                "stats.labels.activity.drop-total",
                formatWholeNumber(sumItemDrops(rawData.itemStats())),
                sumItemDrops(rawData.itemStats())));
        entries.add(createFixedEntry(
                "ACTIVITY_PROJECTILE_TOTAL",
                StatsCategory.ACTIVITY,
                Material.BOW,
                "stats.labels.activity.projectile-total",
                formatWholeNumber(sumIntegers(rawData.projectileStats())),
                sumIntegers(rawData.projectileStats())));
    }

    private List<StatsEntry> createItemDetailEntries(Map<String, ItemStatsDelta> itemStats) {
        List<StatsEntry> entries = new ArrayList<>();
        for (Map.Entry<String, ItemStatsDelta> entry : itemStats.entrySet()) {
            ItemStatsDelta delta = entry.getValue();
            double total = delta.pickupCount() + delta.dropCount();
            entries.add(new StatsEntry(
                    "ITEM:" + entry.getKey(),
                    StatsCategory.ACTIVITY,
                    resolveMaterial(entry.getKey(), Material.CHEST),
                    this.plugin.getMessageConfig().getMessage("stats.dynamic.block.name", prettifyKey(entry.getKey())),
                    formatWholeNumber((long) total),
                    List.of(
                            this.plugin.getMessageConfig().getMessage(
                                    "stats.dynamic.item.pickup",
                                    formatWholeNumber(delta.pickupCount())),
                            this.plugin.getMessageConfig().getMessage(
                                    "stats.dynamic.item.drop",
                                    formatWholeNumber(delta.dropCount()))),
                    total,
                    null));
        }
        return List.copyOf(entries);
    }

    private StatsEntry createFixedEntry(
            String key,
            StatsCategory category,
            Material material,
            String labelKey,
            String valueText,
            double sortValue) {
        return new StatsEntry(
                key,
                category,
                material,
                this.plugin.getMessageConfig().getMessage(labelKey),
                valueText,
                List.of(),
                sortValue,
                null);
    }

    private FavoriteToggleResult applyFavoriteMutation(PlayerStatsSnapshot snapshot, FavoriteMutation mutation) {
        FavoriteToggleStatus status = switch (mutation.status()) {
            case ADDED -> FavoriteToggleStatus.ADDED;
            case REMOVED -> FavoriteToggleStatus.REMOVED;
            case FULL -> FavoriteToggleStatus.FULL;
        };
        if (mutation.status() != FavoriteMutationStatus.FULL) {
            snapshot.applyFavorites(mutation.favorites());
        }
        return new FavoriteToggleResult(status, mutation.slotPosition());
    }

    private @Nullable LocalDate resolvePeriodStartDate(StatsPeriodFilter filter) {
        LocalDate today = LocalDate.now(this.clock);
        return switch (filter) {
            case ALL_TIME -> null;
            case TODAY -> today;
            case THIS_WEEK -> today.minusDays(6L);
            case THIS_MONTH -> today.minusDays(29L);
        };
    }

    private @Nullable Instant resolvePeriodStartInstant(@Nullable LocalDate periodStartDate) {
        if (periodStartDate == null) {
            return null;
        }
        return LocalDateTime.of(periodStartDate, LocalTime.MIN)
                .atZone(this.clock.getZone())
                .toInstant();
    }

    private @Nullable Instant resolveFirstLogin(OfflinePlayer target, @Nullable Instant persistedFirstLogin) {
        if (persistedFirstLogin != null) {
            return persistedFirstLogin;
        }
        long firstPlayed = target.getFirstPlayed();
        return firstPlayed > 0L ? Instant.ofEpochMilli(firstPlayed) : null;
    }

    private String resolveTargetName(OfflinePlayer target) {
        return target.getName() == null || target.getName().isBlank()
                ? target.getUniqueId().toString()
                : target.getName();
    }

    private Material resolveMaterial(String materialName, Material fallback) {
        Material material = Material.matchMaterial(materialName);
        return material == null ? fallback : material;
    }

    private Material resolveDisplayMaterial(String materialName, Material fallback) {
        Material material = Material.matchMaterial(materialName);
        if (material == null) {
            return fallback;
        }
        return material.isItem() ? material : fallback;
    }

    private Material resolveCombatMaterial(String entityKey) {
        return switch (entityKey) {
            case "SKELETON" -> Material.SKELETON_SKULL;
            case "WITHER_SKELETON" -> Material.WITHER_SKELETON_SKULL;
            case "CREEPER" -> Material.CREEPER_HEAD;
            case "ZOMBIE" -> Material.ZOMBIE_HEAD;
            case "FALL" -> Material.FEATHER;
            case "PIGLIN", "PIGLIN_BRUTE" -> Material.GOLDEN_SWORD;
            default -> Material.DIAMOND_SWORD;
        };
    }

    private Material resolveProjectileMaterial(String entityType) {
        return switch (entityType) {
            case "ARROW", "SPECTRAL_ARROW", "TIPPED_ARROW" -> Material.ARROW;
            case "TRIDENT" -> Material.TRIDENT;
            case "SNOWBALL" -> Material.SNOWBALL;
            case "EGG" -> Material.EGG;
            case "ENDER_PEARL" -> Material.ENDER_PEARL;
            case "FIREBALL", "SMALL_FIREBALL", "DRAGON_FIREBALL" -> Material.FIRE_CHARGE;
            case "POTION" -> Material.SPLASH_POTION;
            default -> Material.BOW;
        };
    }

    private String resolveEntityName(String entityKey) {
        if (isUuid(entityKey)) {
            OfflinePlayer player = Bukkit.getOfflinePlayer(UUID.fromString(entityKey));
            return resolveTargetName(player);
        }
        return prettifyKey(entityKey);
    }

    private boolean isUuid(String rawValue) {
        try {
            UUID.fromString(rawValue);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private String prettifyKey(String rawKey) {
        return Arrays.stream(rawKey.toLowerCase(Locale.ROOT).split("_"))
                .filter(part -> !part.isBlank())
                .map(part -> Character.toUpperCase(part.charAt(0)) + part.substring(1))
                .collect(Collectors.joining(" "));
    }

    private String formatWholeNumber(double value) {
        return NumberFormat.getIntegerInstance(NUMBER_LOCALE).format(Math.round(value));
    }

    private String formatWholeNumber(long value) {
        return NumberFormat.getIntegerInstance(NUMBER_LOCALE).format(value);
    }

    private String formatDecimal(double value) {
        return NumberFormat.getNumberInstance(NUMBER_LOCALE).format(value);
    }

    private String formatDuration(long totalSeconds) {
        long safeSeconds = Math.max(0L, totalSeconds);
        long hours = safeSeconds / 3600L;
        long minutes = (safeSeconds % 3600L) / 60L;
        long seconds = safeSeconds % 60L;
        return "%d:%02d:%02d".formatted(hours, minutes, seconds);
    }

    private double sumIntegers(Map<String, Integer> values) {
        return values.values().stream().mapToInt(Integer::intValue).sum();
    }

    private double sumItemPickups(Map<String, ItemStatsDelta> values) {
        return values.values().stream().mapToInt(ItemStatsDelta::pickupCount).sum();
    }

    private double sumItemDrops(Map<String, ItemStatsDelta> values) {
        return values.values().stream().mapToInt(ItemStatsDelta::dropCount).sum();
    }

    private double sumCombatKills(Map<String, CombatAggregate> aggregates) {
        return aggregates.values().stream().mapToInt(CombatAggregate::killCount).sum();
    }

    private double sumCombatDealt(Map<String, CombatAggregate> aggregates) {
        return aggregates.values().stream().mapToDouble(CombatAggregate::damageDealt).sum();
    }

    private double sumCombatTaken(Map<String, CombatAggregate> aggregates) {
        return aggregates.values().stream().mapToDouble(CombatAggregate::damageTaken).sum();
    }

    private Comparator<StatsEntry> createComparator(StatsSortOrder sortOrder) {
        return switch (sortOrder) {
            case VALUE_DESC -> Comparator.comparingDouble(StatsEntry::sortValue)
                    .reversed()
                    .thenComparing(entry -> entry.displayName().toLowerCase(Locale.ROOT));
            case VALUE_ASC -> Comparator.comparingDouble(StatsEntry::sortValue)
                    .thenComparing(entry -> entry.displayName().toLowerCase(Locale.ROOT));
            case NAME_ASC -> Comparator.comparing((StatsEntry entry) -> entry.displayName().toLowerCase(Locale.ROOT))
                    .thenComparing(Comparator.comparingDouble(StatsEntry::sortValue).reversed());
        };
    }

    private static final class CombatAggregate {

        private final String key;
        private int killCount;
        private final double damageDealt;
        private final double damageTaken;

        private CombatAggregate(String key, int killCount, double damageDealt, double damageTaken) {
            this.key = key;
            this.killCount = killCount;
            this.damageDealt = damageDealt;
            this.damageTaken = damageTaken;
        }

        private void setKillCount(int killCount) {
            this.killCount = killCount;
        }

        private int killCount() {
            return this.killCount;
        }

        private double damageDealt() {
            return this.damageDealt;
        }

        private double damageTaken() {
            return this.damageTaken;
        }
    }

    /**
     * Available stats categories.
     */
    public enum StatsCategory {
        GENERAL(Material.BOOK, "stats.categories.general"),
        ECONOMY(Material.GOLD_INGOT, "stats.categories.economy"),
        BLOCKS(Material.GRASS_BLOCK, "stats.categories.blocks"),
        COMBAT(Material.DIAMOND_SWORD, "stats.categories.combat"),
        ACTIVITY(Material.CRAFTING_TABLE, "stats.categories.activity");

        private final Material icon;
        private final String labelKey;

        StatsCategory(Material icon, String labelKey) {
            this.icon = icon;
            this.labelKey = labelKey;
        }

        /**
         * Returns the category icon material.
         *
         * @return icon material
         */
        public Material icon() {
            return this.icon;
        }

        String labelKey() {
            return this.labelKey;
        }
    }

    /**
     * Combat sub-GUI types.
     */
    public enum CombatDetailType {
        MOB_DAMAGE(Material.ZOMBIE_HEAD),
        PLAYER_DAMAGE(Material.IRON_SWORD),
        PROJECTILES(Material.BOW);

        private final Material icon;

        CombatDetailType(Material icon) {
            this.icon = icon;
        }

        /**
         * Returns the icon material.
         *
         * @return icon material
         */
        public Material icon() {
            return this.icon;
        }

    }

    /**
     * Period filters shown in the GUI.
     */
    public enum StatsPeriodFilter {
        TODAY("stats.filters.today"),
        THIS_WEEK("stats.filters.this-week"),
        THIS_MONTH("stats.filters.this-month"),
        ALL_TIME("stats.filters.all-time");

        private final String labelKey;

        StatsPeriodFilter(String labelKey) {
            this.labelKey = labelKey;
        }

        /**
         * Returns the next filter in the cycle.
         *
         * @return next filter
         */
        public StatsPeriodFilter next() {
            return switch (this) {
                case TODAY -> THIS_WEEK;
                case THIS_WEEK -> THIS_MONTH;
                case THIS_MONTH -> ALL_TIME;
                case ALL_TIME -> TODAY;
            };
        }

        String labelKey() {
            return this.labelKey;
        }
    }

    /**
     * Sort orders shown in the GUI.
     */
    public enum StatsSortOrder {
        VALUE_DESC("stats.sorts.value-desc"),
        VALUE_ASC("stats.sorts.value-asc"),
        NAME_ASC("stats.sorts.name-asc");

        private final String labelKey;

        StatsSortOrder(String labelKey) {
            this.labelKey = labelKey;
        }

        /**
         * Returns the next sort order in the cycle.
         *
         * @return next sort order
         */
        public StatsSortOrder next() {
            return switch (this) {
                case VALUE_DESC -> VALUE_ASC;
                case VALUE_ASC -> NAME_ASC;
                case NAME_ASC -> VALUE_DESC;
            };
        }

        String labelKey() {
            return this.labelKey;
        }
    }

    /**
     * Render-ready stats entry.
     *
     * @param key unique stat key
     * @param category owning category
     * @param material icon material
     * @param displayName localized name
     * @param valueText localized value text
     * @param detailLines additional detail lines
     * @param sortValue numeric sort value
     * @param playerHeadId optional player UUID for PLAYER_HEAD skin rendering
     */
    public record StatsEntry(
            String key,
            StatsCategory category,
            Material material,
            String displayName,
            String valueText,
            List<String> detailLines,
            double sortValue,
            @Nullable UUID playerHeadId) {
    }

    /**
     * Mutable GUI snapshot.
     */
    public final class PlayerStatsSnapshot {

        private final UUID targetId;
        private final String targetName;
        private final @Nullable Instant firstLogin;
        private final StatsPeriodFilter periodFilter;
        private final EnumMap<StatsCategory, List<StatsEntry>> entriesByCategory;
        private final EnumMap<CombatDetailType, List<StatsEntry>> combatDetailEntries;
        private final List<StatsEntry> itemDetailEntries;
        private final Map<String, StatsEntry> entriesByKey;
        private final Map<Integer, String> favorites;

        private PlayerStatsSnapshot(
                UUID targetId,
                String targetName,
                @Nullable Instant firstLogin,
                StatsPeriodFilter periodFilter,
                EnumMap<StatsCategory, List<StatsEntry>> entriesByCategory,
                EnumMap<CombatDetailType, List<StatsEntry>> combatDetailEntries,
                List<StatsEntry> itemDetailEntries,
                Map<Integer, String> favorites) {
            this.targetId = targetId;
            this.targetName = targetName;
            this.firstLogin = firstLogin;
            this.periodFilter = periodFilter;
            this.entriesByCategory = new EnumMap<>(StatsCategory.class);
            this.combatDetailEntries = new EnumMap<>(CombatDetailType.class);
            this.itemDetailEntries = List.copyOf(itemDetailEntries);
            this.entriesByKey = new LinkedHashMap<>();
            for (StatsCategory category : StatsCategory.values()) {
                List<StatsEntry> entries = List.copyOf(entriesByCategory.getOrDefault(category, List.of()));
                this.entriesByCategory.put(category, entries);
                for (StatsEntry entry : entries) {
                    this.entriesByKey.put(entry.key(), entry);
                }
            }
            for (CombatDetailType detailType : CombatDetailType.values()) {
                List<StatsEntry> entries = List.copyOf(combatDetailEntries.getOrDefault(detailType, List.of()));
                this.combatDetailEntries.put(detailType, entries);
                for (StatsEntry entry : entries) {
                    this.entriesByKey.put(entry.key(), entry);
                }
            }
            for (StatsEntry entry : this.itemDetailEntries) {
                this.entriesByKey.put(entry.key(), entry);
            }
            this.favorites = new LinkedHashMap<>(favorites);
        }

        /**
         * Returns the target UUID.
         *
         * @return target UUID
         */
        public UUID targetId() {
            return this.targetId;
        }

        /**
         * Returns the target name.
         *
         * @return target name
         */
        public String targetName() {
            return this.targetName;
        }

        /**
         * Returns the target first-login instant.
         *
         * @return first-login instant or {@code null}
         */
        public @Nullable Instant firstLogin() {
            return this.firstLogin;
        }

        /**
         * Returns the selected period filter.
         *
         * @return period filter
         */
        public StatsPeriodFilter periodFilter() {
            return this.periodFilter;
        }

        /**
         * Returns the entries for the given category.
         *
         * @param category category
         * @return category entries
         */
        public List<StatsEntry> getEntries(StatsCategory category) {
            return this.entriesByCategory.getOrDefault(category, List.of());
        }

        /**
         * Returns the entries sorted by the requested sort order.
         *
         * @param category category
         * @param sortOrder sort order
         * @return sorted entries
         */
        public List<StatsEntry> getSortedEntries(StatsCategory category, StatsSortOrder sortOrder) {
            return getEntries(category).stream()
                    .sorted(createComparator(sortOrder))
                    .toList();
        }

        /**
         * Returns the detail entries sorted by the requested sort order.
         *
         * @param detailType detail type
         * @param sortOrder sort order
         * @return sorted detail entries
         */
        public List<StatsEntry> getSortedCombatDetailEntries(
                CombatDetailType detailType,
                StatsSortOrder sortOrder) {
            return this.combatDetailEntries.getOrDefault(detailType, List.of()).stream()
                    .sorted(createComparator(sortOrder))
                    .toList();
        }

        /**
         * Returns the item pickup/drop detail entries sorted by the requested sort order.
         *
         * @param sortOrder sort order
         * @return sorted item detail entries
         */
        public List<StatsEntry> getSortedItemDetailEntries(StatsSortOrder sortOrder) {
            return this.itemDetailEntries.stream()
                    .sorted(createComparator(sortOrder))
                    .toList();
        }

        /**
         * Returns the stat entry for the given key.
         *
         * @param key stat key
         * @return entry or {@code null}
         */
        public @Nullable StatsEntry getEntry(String key) {
            return this.entriesByKey.get(key);
        }

        /**
         * Returns the current favorites keyed by slot position.
         *
         * @return favorites map
         */
        public Map<Integer, String> getFavorites() {
            return Map.copyOf(this.favorites);
        }

        private void applyFavorites(Map<Integer, String> updatedFavorites) {
            this.favorites.clear();
            this.favorites.putAll(updatedFavorites);
        }
    }

    /**
     * Favorite toggle result.
     *
     * @param status result status
     * @param slotPosition affected slot position
     */
    public record FavoriteToggleResult(FavoriteToggleStatus status, int slotPosition) {
    }

    /**
     * Favorite toggle statuses.
     */
    public enum FavoriteToggleStatus {
        ADDED,
        REMOVED,
        FULL
    }
}
