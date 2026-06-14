package network.tserver.tnexus.listener;

import java.util.ArrayList;
import java.util.List;
import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.TestPluginSupport;
import network.tserver.tnexus.database.repository.PlayerStatsRepository;
import network.tserver.tnexus.manager.ActivityType;
import network.tserver.tnexus.manager.PlayerStatsManager;
import org.bukkit.entity.Player;

public class TrackingActivityPlugin extends TestPluginSupport.H2TestTNexus {

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

        private final List<ActivityInvocation> activityInvocations;

        private TrackingPlayerStatsManager(TNexus plugin) {
            super(plugin, new PlayerStatsRepository(plugin.getDatabaseManager()));
            this.activityInvocations = new ArrayList<>();
        }

        @Override
        public void recordActivity(Player player, ActivityType type, String content, Object payload) {
            this.activityInvocations.add(new ActivityInvocation(type, content, payload));
        }

        List<ActivityInvocation> activityInvocations() {
            return List.copyOf(this.activityInvocations);
        }
    }

    record ActivityInvocation(ActivityType type, String content, Object payload) {
    }
}
