package network.tserver.tnexus.listener;

import io.papermc.paper.block.bed.BedEnterAction;
import io.papermc.paper.block.bed.BedEnterProblem;
import io.papermc.paper.block.bed.BedRuleResult;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import network.tserver.tnexus.TestPluginSupport;
import net.kyori.adventure.chat.SignedMessage;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerMiscStatsListenerTest {

    private static final BedEnterAction ALLOWED_BED_ENTER_ACTION = new BedEnterAction() {
        @Override
        public BedRuleResult canSleep() {
            return BedRuleResult.ALLOWED;
        }

        @Override
        public BedRuleResult canSetSpawn() {
            return BedRuleResult.ALLOWED;
        }

        @Override
        public BedEnterProblem problem() {
            return BedEnterProblem.OTHER;
        }

        @Override
        public Component errorMessage() {
            return Component.empty();
        }
    };

    private ServerMock server;

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void shouldRecordOnlySuccessfulBedEntriesAndNonCancelledPortals() {
        TrackingMiscStatsPlugin plugin = loadPlugin();
        PlayerMock player = this.server.addPlayer("Sleeper");
        var world = player.getWorld();
        var bedBlock = world.getBlockAt(0, 64, 0);
        bedBlock.setType(Material.RED_BED);

        PlayerBedEnterEvent successfulEvent = new PlayerBedEnterEvent(
                player,
                bedBlock,
                PlayerBedEnterEvent.BedEnterResult.OK,
                ALLOWED_BED_ENTER_ACTION);
        PlayerBedEnterEvent deniedEvent = new PlayerBedEnterEvent(
                player,
                bedBlock,
                PlayerBedEnterEvent.BedEnterResult.NOT_POSSIBLE_NOW,
                ALLOWED_BED_ENTER_ACTION);
        deniedEvent.setCancelled(true);

        PlayerPortalEvent portalEvent = new PlayerPortalEvent(
                player,
                new Location(world, 0.0D, 64.0D, 0.0D),
                new Location(world, 10.0D, 70.0D, 10.0D),
                PlayerTeleportEvent.TeleportCause.NETHER_PORTAL);
        PlayerPortalEvent cancelledPortalEvent = new PlayerPortalEvent(
                player,
                new Location(world, 1.0D, 64.0D, 1.0D),
                new Location(world, 11.0D, 70.0D, 11.0D),
                PlayerTeleportEvent.TeleportCause.END_PORTAL);
        cancelledPortalEvent.setCancelled(true);

        this.server.getPluginManager().callEvent(successfulEvent);
        this.server.getPluginManager().callEvent(deniedEvent);
        this.server.getPluginManager().callEvent(portalEvent);
        this.server.getPluginManager().callEvent(cancelledPortalEvent);

        assertEquals(1, plugin.getTrackingPlayerStatsManager().sleepCount);
        assertEquals(1, plugin.getTrackingPlayerStatsManager().portalCount);
    }

    @Test
    void shouldRecordOnlyPlayerProjectileLaunchesAndNonCancelledChat() {
        TrackingMiscStatsPlugin plugin = loadPlugin();
        PlayerMock player = this.server.addPlayer("Archer");
        var world = player.getWorld();

        Projectile playerProjectile = (Projectile) world.spawnEntity(
                new Location(world, 2.0D, 64.0D, 2.0D),
                EntityType.ARROW);
        playerProjectile.setShooter(player);

        Projectile worldProjectile = (Projectile) world.spawnEntity(
                new Location(world, 3.0D, 64.0D, 3.0D),
                EntityType.SNOWBALL);

        ProjectileLaunchEvent playerProjectileEvent = new ProjectileLaunchEvent(playerProjectile);
        ProjectileLaunchEvent worldProjectileEvent = new ProjectileLaunchEvent(worldProjectile);
        ProjectileLaunchEvent cancelledProjectileEvent = new ProjectileLaunchEvent(playerProjectile);
        cancelledProjectileEvent.setCancelled(true);

        AsyncChatEvent chatEvent = new AsyncChatEvent(
                true,
                player,
                Set.of(player),
                ChatRenderer.defaultRenderer(),
                Component.text("hello"),
                Component.text("hello"),
                SignedMessage.system("hello", Component.text("hello")));
        AsyncChatEvent cancelledChatEvent = new AsyncChatEvent(
                true,
                player,
                Set.of(player),
                ChatRenderer.defaultRenderer(),
                Component.text("nope"),
                Component.text("nope"),
                SignedMessage.system("nope", Component.text("nope")));
        cancelledChatEvent.setCancelled(true);

        this.server.getPluginManager().callEvent(playerProjectileEvent);
        this.server.getPluginManager().callEvent(worldProjectileEvent);
        this.server.getPluginManager().callEvent(cancelledProjectileEvent);
        CompletableFuture.runAsync(() -> this.server.getPluginManager().callEvent(chatEvent)).join();
        CompletableFuture.runAsync(() -> this.server.getPluginManager().callEvent(cancelledChatEvent)).join();

        assertEquals(1, plugin.getTrackingPlayerStatsManager().projectileCount);
        assertEquals(EntityType.ARROW.name(), plugin.getTrackingPlayerStatsManager().lastProjectileType);
        assertEquals(1, plugin.getTrackingPlayerStatsManager().chatCount);
    }

    private TrackingMiscStatsPlugin loadPlugin() {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        return TestPluginSupport.loadPlugin(this.server, TrackingMiscStatsPlugin.class);
    }
}
