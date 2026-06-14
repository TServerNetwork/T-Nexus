package network.tserver.tnexus.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.Objects;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.manager.ActivityType;
import network.tserver.tnexus.manager.PlayerStatsManager;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Emits AFK activity signals from gameplay events.
 */
public final class PlayerActivityListener implements Listener {

    private static final PlainTextComponentSerializer PLAIN_TEXT = PlainTextComponentSerializer.plainText();

    private final PlayerStatsManager playerStatsManager;

    /**
     * Creates a new activity listener.
     *
     * @param plugin plugin instance
     */
    public PlayerActivityListener(TNexus plugin) {
        this.playerStatsManager = plugin.getPlayerStatsManager();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAsyncChat(AsyncChatEvent event) {
        this.playerStatsManager.recordActivity(
                event.getPlayer(),
                ActivityType.CHAT,
                PLAIN_TEXT.serialize(event.message()),
                null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        this.playerStatsManager.recordActivity(
                event.getPlayer(),
                ActivityType.COMMAND,
                event.getMessage(),
                null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        this.playerStatsManager.recordActivity(
                event.getPlayer(),
                ActivityType.BLOCK_BREAK,
                null,
                event.getBlock().getType().name());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        this.playerStatsManager.recordActivity(
                event.getPlayer(),
                ActivityType.BLOCK_PLACE,
                null,
                event.getBlockPlaced().getType().name());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action == Action.PHYSICAL) {
            return;
        }
        Block clickedBlock = event.getClickedBlock();
        ActivityType type = event.hasItem() ? ActivityType.ITEM_USE : ActivityType.INTERACT;
        this.playerStatsManager.recordActivity(
                event.getPlayer(),
                type,
                null,
                clickedBlock == null ? action.name() : clickedBlock.getType().name());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        this.playerStatsManager.recordActivity(
                event.getPlayer(),
                ActivityType.INTERACT,
                null,
                event.getRightClicked().getType().name());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        this.playerStatsManager.recordActivity(player, ActivityType.INVENTORY_CLICK, null, event.getClick().name());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        this.playerStatsManager.recordActivity(
                event.getPlayer(),
                ActivityType.HOTBAR_CHANGE,
                null,
                event.getNewSlot());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerAnimation(PlayerAnimationEvent event) {
        this.playerStatsManager.recordActivity(event.getPlayer(), ActivityType.ARM_SWING);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        if (hasPositionChanged(from, to)) {
            this.playerStatsManager.recordActivity(event.getPlayer(), ActivityType.POSITION, null, locationPayload(to));
        }
        if (hasRotationChanged(from, to)) {
            this.playerStatsManager.recordActivity(event.getPlayer(), ActivityType.ROTATION, null, rotationPayload(to));
        }
    }

    private boolean hasPositionChanged(Location from, Location to) {
        return Double.compare(from.getX(), to.getX()) != 0
                || Double.compare(from.getY(), to.getY()) != 0
                || Double.compare(from.getZ(), to.getZ()) != 0
                || !Objects.equals(from.getWorld(), to.getWorld());
    }

    private boolean hasRotationChanged(Location from, Location to) {
        return Float.compare(from.getYaw(), to.getYaw()) != 0
                || Float.compare(from.getPitch(), to.getPitch()) != 0;
    }

    private String locationPayload(Location location) {
        return "%s:%.3f,%.3f,%.3f".formatted(
                location.getWorld() == null ? "unknown" : location.getWorld().getName(),
                location.getX(),
                location.getY(),
                location.getZ());
    }

    private String rotationPayload(Location location) {
        return "%.3f,%.3f".formatted(location.getYaw(), location.getPitch());
    }
}
