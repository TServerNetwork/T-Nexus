package network.tserver.tnexus.listener;

import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.event.extent.EditSessionEvent;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.util.eventbus.Subscribe;
import java.util.logging.Level;
import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.manager.PlayerStatsManager;

/**
 * Suppresses stat tracking for WorldEdit-driven bulk edits.
 */
public final class WorldEditStatsListener implements AutoCloseable {

    private final TNexus plugin;
    private final PlayerStatsManager playerStatsManager;
    private boolean registered;

    /**
     * Creates a new WorldEdit stats listener and attempts to register it.
     *
     * @param plugin plugin instance
     */
    public WorldEditStatsListener(TNexus plugin) {
        this.plugin = plugin;
        this.playerStatsManager = plugin.getPlayerStatsManager();
        this.registered = false;
        register();
    }

    @Subscribe
    public void onEditSession(EditSessionEvent event) {
        Actor actor = event.getActor();
        if (actor == null || !actor.isPlayer()) {
            return;
        }
        this.playerStatsManager.markWorldEditOperation(actor.getUniqueId());
    }

    /**
     * Unregisters this listener from the WorldEdit event bus when previously registered.
     */
    public void shutdown() {
        if (!this.registered) {
            return;
        }
        try {
            WorldEdit.getInstance().getEventBus().unregister(this);
        } catch (RuntimeException exception) {
            this.plugin.getLogger().log(Level.WARNING, "Failed to unregister WorldEdit stats listener.", exception);
        } finally {
            this.registered = false;
        }
    }

    @Override
    public void close() {
        shutdown();
    }

    private void register() {
        try {
            WorldEdit worldEdit = WorldEdit.getInstance();
            if (worldEdit == null) {
                return;
            }
            worldEdit.getEventBus().register(this);
            this.registered = true;
        } catch (RuntimeException exception) {
            this.plugin.getLogger().log(
                    Level.FINE,
                    "WorldEdit event bus was unavailable; block stats suppression is disabled in this environment.",
                    exception);
        }
    }
}
