package network.tserver.tnexus.manager;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Wraps Vault economy access behind asynchronous operations.
 */
public final class EconomyManager {

    private final Plugin plugin;
    private final Economy economy;
    private final String fallbackCurrencyName;

    /**
     * Creates a new economy manager from the server Vault provider.
     *
     * @param plugin owner plugin
     * @throws IllegalStateException when no Vault economy provider is registered
     */
    public EconomyManager(Plugin plugin) {
        this(plugin, resolveEconomy(Objects.requireNonNull(plugin, "plugin")));
    }

    /**
     * Creates a new economy manager with an explicit Vault provider.
     *
     * @param plugin owner plugin
     * @param economy Vault economy provider
     */
    public EconomyManager(Plugin plugin, Economy economy) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.economy = Objects.requireNonNull(economy, "economy");
        this.fallbackCurrencyName = this.plugin.getConfig().getString("tnexus.economy.currency-name", "Coin");
    }

    /**
     * Returns the current balance for a player.
     *
     * @param playerId target player UUID
     * @return future resolving to the player balance
     */
    public CompletableFuture<Double> getBalance(UUID playerId) {
        return supplyAsync(() -> this.economy.getBalance(getOfflinePlayer(playerId)));
    }

    /**
     * Deposits funds into a player account.
     *
     * @param playerId target player UUID
     * @param amount amount to deposit
     * @return future resolving to whether the operation succeeded
     */
    public CompletableFuture<Boolean> deposit(UUID playerId, double amount) {
        return supplyAsync(() -> {
            if (amount < 0.0D) {
                return false;
            }
            return this.economy.depositPlayer(getOfflinePlayer(playerId), amount).transactionSuccess();
        });
    }

    /**
     * Withdraws funds from a player account.
     *
     * @param playerId target player UUID
     * @param amount amount to withdraw
     * @return future resolving to whether the operation succeeded
     */
    public CompletableFuture<Boolean> withdraw(UUID playerId, double amount) {
        return supplyAsync(() -> {
            if (amount < 0.0D) {
                return false;
            }
            return this.economy.withdrawPlayer(getOfflinePlayer(playerId), amount).transactionSuccess();
        });
    }

    /**
     * Sets the balance of a player account.
     *
     * @param playerId target player UUID
     * @param amount new balance
     * @return future resolving to whether the operation succeeded
     */
    public CompletableFuture<Boolean> setBalance(UUID playerId, double amount) {
        return supplyAsync(() -> {
            if (amount < 0.0D) {
                return false;
            }

            OfflinePlayer player = getOfflinePlayer(playerId);
            double currentBalance = this.economy.getBalance(player);
            double difference = amount - currentBalance;

            if (difference > 0.0D) {
                return this.economy.depositPlayer(player, difference).transactionSuccess();
            }
            if (difference < 0.0D) {
                return this.economy.withdrawPlayer(player, Math.abs(difference)).transactionSuccess();
            }
            return true;
        });
    }

    /**
     * Returns whether a player has at least the requested funds.
     *
     * @param playerId target player UUID
     * @param amount required amount
     * @return future resolving to whether the player has enough funds
     */
    public CompletableFuture<Boolean> has(UUID playerId, double amount) {
        return supplyAsync(() -> amount >= 0.0D && this.economy.has(getOfflinePlayer(playerId), amount));
    }

    /**
     * Returns the current balance immediately on the calling thread.
     *
     * @param playerId target player UUID
     * @return current balance
     */
    public double getBalanceNow(UUID playerId) {
        return this.economy.getBalance(getOfflinePlayer(playerId));
    }

    /**
     * Returns the configured currency name from the active Vault provider.
     *
     * @return plural currency name when available, otherwise singular
     */
    public String getCurrencyName() {
        String plural = this.economy.currencyNamePlural();
        if (plural != null && !plural.isBlank()) {
            return plural;
        }

        String singular = this.economy.currencyNameSingular();
        if (singular != null && !singular.isBlank()) {
            return singular;
        }
        return this.fallbackCurrencyName;
    }

    private OfflinePlayer getOfflinePlayer(UUID playerId) {
        return this.plugin.getServer().getOfflinePlayer(Objects.requireNonNull(playerId, "playerId"));
    }

    private <T> CompletableFuture<T> supplyAsync(CheckedSupplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        this.plugin.getServer().getScheduler().runTaskAsynchronously(this.plugin, task -> {
            try {
                future.complete(supplier.get());
            } catch (Exception exception) {
                future.completeExceptionally(exception);
            }
        });
        return future;
    }

    private static Economy resolveEconomy(Plugin plugin) {
        Server server = plugin.getServer();
        RegisteredServiceProvider<Economy> registration = server.getServicesManager().getRegistration(Economy.class);
        if (registration == null || registration.getProvider() == null) {
            throw new IllegalStateException("No Vault economy provider registered");
        }
        return registration.getProvider();
    }

    @FunctionalInterface
    private interface CheckedSupplier<T> {

        T get() throws Exception;
    }
}
