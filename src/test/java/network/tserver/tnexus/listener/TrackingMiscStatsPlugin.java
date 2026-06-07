package network.tserver.tnexus.listener;

import java.util.concurrent.CompletableFuture;
import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.TestPluginSupport;
import network.tserver.tnexus.database.repository.PlayerStatsRepository;
import network.tserver.tnexus.manager.PlayerStatsManager;
import org.bukkit.entity.Player;

public class TrackingMiscStatsPlugin extends TestPluginSupport.H2TestTNexus {

    private TrackingPlayerStatsManager trackingPlayerStatsManager;

    @Override
    protected PlayerStatsManager createPlayerStatsManager() {
        this.trackingPlayerStatsManager = new TrackingPlayerStatsManager(this);
        return this.trackingPlayerStatsManager;
    }

    TrackingPlayerStatsManager getTrackingPlayerStatsManager() {
        return this.trackingPlayerStatsManager;
    }

    static final class TrackingPlayerStatsManager extends PlayerStatsManager {

        int sleepCount;
        int portalCount;
        int chatCount;
        int projectileCount;
        String lastProjectileType;

        private TrackingPlayerStatsManager(TNexus plugin) {
            super(plugin, new PlayerStatsRepository(plugin.getDatabaseManager()));
        }

        @Override
        public CompletableFuture<Void> recordSleep(Player player) {
            this.sleepCount++;
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> recordPortal(Player player) {
            this.portalCount++;
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> recordChat(Player player) {
            this.chatCount++;
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> recordProjectileLaunch(Player player, String entityType) {
            this.projectileCount++;
            this.lastProjectileType = entityType;
            return CompletableFuture.completedFuture(null);
        }
    }
}
