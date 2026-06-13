package network.tserver.tnexus.manager;

import com.onarandombox.MultiverseCore.api.MVWorldManager;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.TestPluginSupport;
import network.tserver.tnexus.database.repository.ResourceWorldResetRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceWorldManagerTest {

    private ServerMock server;

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void shouldLoadConfiguredWorldsAndPersistNextResetTimes() throws Exception {
        TNexus plugin = loadPlugin();
        ResourceWorldResetRepository repository = new ResourceWorldResetRepository(plugin.getDatabaseManager());
        ResourceWorldManager manager = new ResourceWorldManager(
                plugin,
                repository,
                createTrackingWorldManager(new ConcurrentHashMap<>()),
                Clock.fixed(Instant.parse("2026-06-14T00:00:00Z"), ZoneId.of("Asia/Tokyo")));

        manager.onEnable().get(5, TimeUnit.SECONDS);

        assertEquals(3, manager.getSettings().worlds().size());
        assertTrue(manager.getWorldDefinition("resource").isPresent());
        assertTrue(manager.getWorldDefinition("resource_nether").isPresent());
        assertTrue(manager.getWorldDefinition("resource_end").isPresent());

        LocalDateTime expected = manager.calculateNextResetTime(
                manager.getWorldDefinition("resource").orElseThrow(),
                LocalDateTime.ofInstant(Instant.parse("2026-06-14T00:00:00Z"), ZoneId.of("Asia/Tokyo")));
        assertEquals(expected, manager.getNextResetTime("resource").get(5, TimeUnit.SECONDS).orElseThrow());
    }

    @Test
    void shouldTrackResettingStateAndDelegateWorldOperations() {
        TNexus plugin = loadPlugin();
        Map<String, AtomicReference<String>> calls = new ConcurrentHashMap<>();
        ResourceWorldManager manager = new ResourceWorldManager(
                plugin,
                new ResourceWorldResetRepository(plugin.getDatabaseManager()),
                createTrackingWorldManager(calls),
                Clock.systemDefaultZone());

        assertFalse(manager.isResetting("resource"));
        manager.markResetting("resource");
        assertTrue(manager.isResetting("resource"));
        manager.clearResetting("resource");
        assertFalse(manager.isResetting("resource"));

        assertTrue(manager.unloadWorld("resource"));
        assertTrue(manager.regenerateWorld("resource", "12345"));
        assertTrue(manager.loadWorld("resource"));

        assertEquals("resource", calls.get("unloadWorld").get());
        assertEquals("resource", calls.get("loadWorld").get());
        assertEquals("resource|12345", calls.get("regenWorld").get());
    }

    @Test
    void shouldExecuteResetAndPersistNextSchedule() throws Exception {
        TNexus plugin = loadPlugin();
        plugin.getConfigManager().getConfiguration().set("resource-world.worlds", List.of(Map.of(
                "name", "resource",
                "dimension", "NORMAL",
                "reset-interval-days", 1,
                "reset-start-date", "2026-06-14T09:00:10")));
        Map<String, AtomicReference<String>> calls = new ConcurrentHashMap<>();
        ResourceWorldResetRepository repository = new ResourceWorldResetRepository(plugin.getDatabaseManager());
        ResourceWorldManager manager = new ResourceWorldManager(
                plugin,
                repository,
                createTrackingWorldManager(calls),
                Clock.fixed(Instant.parse("2026-06-14T00:00:00Z"), ZoneId.of("Asia/Tokyo")));

        manager.onEnable().get(5, TimeUnit.SECONDS);
        LocalDateTime currentReset = manager.getNextResetTime("resource").get(5, TimeUnit.SECONDS).orElseThrow();
        CompletableFuture<LocalDateTime> resetFuture = manager.executeReset("resource");
        waitFor(resetFuture);
        LocalDateTime nextReset = resetFuture.get(5, TimeUnit.SECONDS);

        assertEquals(LocalDateTime.of(2026, 6, 15, 9, 0, 10), nextReset);
        assertEquals(nextReset, repository.findNextResetTime("resource").get(5, TimeUnit.SECONDS).orElseThrow());
        assertEquals("resource", calls.get("unloadWorld").get());
        assertEquals("resource", calls.get("loadWorld").get());
        assertNotNull(calls.get("regenWorld").get());
        assertFalse(manager.isResetting("resource"));

        PlayerMock player = (PlayerMock) this.server.getPlayerExact("Player0");
        String startMessage = player.nextMessage();
        String completeMessage = player.nextMessage();
        assertNotNull(startMessage);
        assertNotNull(completeMessage);
        assertTrue(startMessage.contains("resource"));
        assertTrue(completeMessage.contains("resource"));
        assertTrue(repository.findByWorldNameAndNextResetAt("resource", currentReset).get(5, TimeUnit.SECONDS).isPresent());
    }

    private TNexus loadPlugin() {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        this.server.addPlayer();
        return TestPluginSupport.loadPlugin(this.server, TestPluginSupport.H2TestTNexus.class);
    }

    private void waitFor(CompletableFuture<?> future) throws Exception {
        long deadline = System.currentTimeMillis() + 5_000L;
        while (!future.isDone() && System.currentTimeMillis() < deadline) {
            this.server.getScheduler().performOneTick();
            Thread.sleep(10L);
        }
        if (!future.isDone()) {
            throw new AssertionError("Future did not complete before timeout");
        }
    }

    private MVWorldManager createTrackingWorldManager(Map<String, AtomicReference<String>> calls) {
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "unloadWorld" -> {
                calls.computeIfAbsent("unloadWorld", ignored -> new AtomicReference<>()).set((String) args[0]);
                yield true;
            }
            case "loadWorld" -> {
                calls.computeIfAbsent("loadWorld", ignored -> new AtomicReference<>()).set((String) args[0]);
                yield true;
            }
            case "regenWorld" -> {
                calls.computeIfAbsent("regenWorld", ignored -> new AtomicReference<>())
                        .set(args[0] + "|" + args[3]);
                yield true;
            }
            case "getMVWorlds", "getUnloadedWorlds", "getPotentialWorlds" -> java.util.List.of();
            default -> defaultValue(method.getReturnType());
        };
        Object proxy = Proxy.newProxyInstance(
                MVWorldManager.class.getClassLoader(),
                new Class<?>[]{MVWorldManager.class},
                handler);
        assertSame(MVWorldManager.class, proxy.getClass().getInterfaces()[0]);
        return (MVWorldManager) proxy;
    }

    private Object defaultValue(Class<?> returnType) {
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        return null;
    }
}
