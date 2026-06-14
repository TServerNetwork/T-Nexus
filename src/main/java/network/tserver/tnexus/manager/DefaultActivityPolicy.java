package network.tserver.tnexus.manager;

import java.util.Objects;
import org.bukkit.entity.Player;

/**
 * Default AFK activity policy.
 */
public final class DefaultActivityPolicy implements ActivityPolicy {

    @Override
    public int calculateScore(Player player, ActivityType type, ActivityContext context) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(context, "context");
        return switch (type) {
            case CHAT, COMMAND -> 100;
            case BLOCK_BREAK, BLOCK_PLACE, INTERACT, INVENTORY_CLICK -> 80;
            case ITEM_USE, ARM_SWING -> 50;
            case HOTBAR_CHANGE -> 30;
            case ROTATION -> 10;
            case POSITION -> 0;
        };
    }
}
