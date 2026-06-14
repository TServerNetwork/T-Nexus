package network.tserver.tnexus.manager;

import org.bukkit.entity.Player;

/**
 * Calculates AFK activity score deltas for player actions.
 */
public interface ActivityPolicy {

    /**
     * Calculates the score delta for one activity event.
     *
     * @param player acting player
     * @param type activity type
     * @param context activity context
     * @return score delta
     */
    int calculateScore(Player player, ActivityType type, ActivityContext context);
}
