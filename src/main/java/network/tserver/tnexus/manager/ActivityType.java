package network.tserver.tnexus.manager;

/**
 * Supported activity signals used by AFK detection.
 */
public enum ActivityType {
    CHAT,
    COMMAND,
    BLOCK_BREAK,
    BLOCK_PLACE,
    INTERACT,
    INVENTORY_CLICK,
    ITEM_USE,
    ARM_SWING,
    HOTBAR_CHANGE,
    ROTATION,
    POSITION
}
