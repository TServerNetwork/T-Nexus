package network.tserver.tnexus.listener;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.chat.SignedMessage;
import net.kyori.adventure.text.Component;
import network.tserver.tnexus.TestPluginSupport;
import network.tserver.tnexus.manager.ActivityType;
import org.bukkit.Location;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerActivityListenerTest {

    private ServerMock server;

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void shouldCaptureChatAndCommandContent() {
        TrackingActivityPlugin plugin = loadPlugin();
        PlayerMock player = this.server.addPlayer("Speaker");

        AsyncChatEvent chatEvent = new AsyncChatEvent(
                true,
                player,
                Set.of(player),
                ChatRenderer.defaultRenderer(),
                Component.text("hello there"),
                Component.text("hello there"),
                SignedMessage.system("hello there", Component.text("hello there")));
        PlayerCommandPreprocessEvent commandEvent = new PlayerCommandPreprocessEvent(player, "/spawn");
        PlayerCommandPreprocessEvent cancelledCommandEvent = new PlayerCommandPreprocessEvent(player, "/ignored");
        cancelledCommandEvent.setCancelled(true);

        CompletableFuture.runAsync(() -> this.server.getPluginManager().callEvent(chatEvent)).join();
        this.server.getPluginManager().callEvent(commandEvent);
        this.server.getPluginManager().callEvent(cancelledCommandEvent);

        assertEquals(2, plugin.getTrackingPlayerStatsManager().activityInvocations().size());
        assertEquals(ActivityType.CHAT, plugin.getTrackingPlayerStatsManager().activityInvocations().get(0).type());
        assertEquals("hello there", plugin.getTrackingPlayerStatsManager().activityInvocations().get(0).content());
        assertEquals(ActivityType.COMMAND, plugin.getTrackingPlayerStatsManager().activityInvocations().get(1).type());
        assertEquals("/spawn", plugin.getTrackingPlayerStatsManager().activityInvocations().get(1).content());
    }

    @Test
    void shouldCapturePositionAndRotationSeparately() {
        TrackingActivityPlugin plugin = loadPlugin();
        PlayerMock player = this.server.addPlayer("Mover");
        PlayerMoveEvent moveEvent = new PlayerMoveEvent(
                player,
                new Location(player.getWorld(), 0.0D, 64.0D, 0.0D, 0.0F, 0.0F),
                new Location(player.getWorld(), 1.0D, 64.0D, 0.0D, 45.0F, 0.0F));

        this.server.getPluginManager().callEvent(moveEvent);

        assertEquals(2, plugin.getTrackingPlayerStatsManager().activityInvocations().size());
        assertEquals(ActivityType.POSITION, plugin.getTrackingPlayerStatsManager().activityInvocations().get(0).type());
        assertEquals(ActivityType.ROTATION, plugin.getTrackingPlayerStatsManager().activityInvocations().get(1).type());
    }

    private TrackingActivityPlugin loadPlugin() {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        return TestPluginSupport.loadPlugin(this.server, TrackingActivityPlugin.class);
    }
}
