package network.tserver.tnexus.manager;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.TestPluginSupport;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EconomyManagerTest {

    private ServerMock server;

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void shouldResolveCurrencyNameFromVaultProvider() {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        TNexus plugin = TestPluginSupport.loadPlugin(this.server, TestPluginSupport.TestTNexus.class);

        assertEquals("Coins", plugin.getEconomyManager().getCurrencyName());
    }

    @Test
    void shouldPerformBalanceOperationsAsynchronously() throws Exception {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        TNexus plugin = TestPluginSupport.loadPlugin(this.server, TestPluginSupport.TestTNexus.class);
        AtomicLong lastThreadId = new AtomicLong(Thread.currentThread().getId());
        EconomyManager manager = new EconomyManager(plugin, createEconomyProxy(lastThreadId));
        UUID playerId = UUID.randomUUID();

        assertTrue(manager.deposit(playerId, 50.0D).get(3, TimeUnit.SECONDS));
        assertTrue(manager.has(playerId, 30.0D).get(3, TimeUnit.SECONDS));
        assertTrue(manager.withdraw(playerId, 20.0D).get(3, TimeUnit.SECONDS));
        assertTrue(manager.setBalance(playerId, 15.0D).get(3, TimeUnit.SECONDS));
        assertEquals(15.0D, manager.getBalance(playerId).get(3, TimeUnit.SECONDS));
        assertNotEquals(Thread.currentThread().getId(), lastThreadId.get());
    }

    @Test
    void shouldRejectNegativeAmounts() throws Exception {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        TNexus plugin = TestPluginSupport.loadPlugin(this.server, TestPluginSupport.TestTNexus.class);
        EconomyManager manager = new EconomyManager(plugin, createEconomyProxy(new AtomicLong()));
        UUID playerId = UUID.randomUUID();

        assertFalse(manager.deposit(playerId, -1.0D).get(3, TimeUnit.SECONDS));
        assertFalse(manager.withdraw(playerId, -1.0D).get(3, TimeUnit.SECONDS));
        assertFalse(manager.setBalance(playerId, -1.0D).get(3, TimeUnit.SECONDS));
        assertFalse(manager.has(playerId, -1.0D).get(3, TimeUnit.SECONDS));
    }

    private Economy createEconomyProxy(AtomicLong lastThreadId) {
        Map<UUID, Double> balances = new ConcurrentHashMap<>();
        InvocationHandler handler = (proxy, method, args) -> {
            lastThreadId.set(Thread.currentThread().getId());
            return switch (method.getName()) {
                case "getBalance" -> balances.getOrDefault(getPlayerId(args), 0.0D);
                case "has" -> balances.getOrDefault(getPlayerId(args), 0.0D) >= getAmount(args);
                case "depositPlayer" -> deposit(balances, getPlayerId(args), getAmount(args));
                case "withdrawPlayer" -> withdraw(balances, getPlayerId(args), getAmount(args));
                case "currencyNamePlural" -> "Coins";
                case "currencyNameSingular" -> "Coin";
                case "isEnabled" -> true;
                case "hasBankSupport" -> false;
                case "fractionalDigits" -> 2;
                case "format" -> String.format("%.2f", ((Number) args[0]).doubleValue());
                case "getName" -> "ThreadAwareEconomy";
                case "hasAccount", "createPlayerAccount" -> true;
                default -> defaultValue(method.getReturnType());
            };
        };
        return (Economy) Proxy.newProxyInstance(
                Economy.class.getClassLoader(),
                new Class<?>[]{Economy.class},
                handler);
    }

    private UUID getPlayerId(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof OfflinePlayer player) {
                return player.getUniqueId();
            }
        }
        throw new IllegalArgumentException("OfflinePlayer argument is required");
    }

    private double getAmount(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof Number number) {
                return number.doubleValue();
            }
        }
        throw new IllegalArgumentException("Numeric amount argument is required");
    }

    private EconomyResponse deposit(Map<UUID, Double> balances, UUID playerId, double amount) {
        double newBalance = balances.getOrDefault(playerId, 0.0D) + amount;
        balances.put(playerId, newBalance);
        return new EconomyResponse(amount, newBalance, EconomyResponse.ResponseType.SUCCESS, null);
    }

    private EconomyResponse withdraw(Map<UUID, Double> balances, UUID playerId, double amount) {
        double currentBalance = balances.getOrDefault(playerId, 0.0D);
        if (currentBalance < amount) {
            return new EconomyResponse(amount, currentBalance, EconomyResponse.ResponseType.FAILURE, "Insufficient funds");
        }

        double newBalance = currentBalance - amount;
        balances.put(playerId, newBalance);
        return new EconomyResponse(amount, newBalance, EconomyResponse.ResponseType.SUCCESS, null);
    }

    private Object defaultValue(Class<?> returnType) {
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == double.class) {
            return 0.0D;
        }
        return null;
    }
}
