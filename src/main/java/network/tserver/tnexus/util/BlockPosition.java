package network.tserver.tnexus.util;

import java.util.Objects;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.jetbrains.annotations.Nullable;

/**
 * Immutable block-aligned position used for sign and chest lookups.
 *
 * @param worldName world name
 * @param x block x
 * @param y block y
 * @param z block z
 */
public record BlockPosition(String worldName, int x, int y, int z) {

    /**
     * Creates a position from a block.
     *
     * @param block source block
     * @return block position
     */
    public static BlockPosition from(Block block) {
        Objects.requireNonNull(block, "block");
        return new BlockPosition(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }

    /**
     * Creates a position from a location.
     *
     * @param location source location
     * @return block position
     */
    public static BlockPosition from(Location location) {
        Objects.requireNonNull(location, "location");
        World world = Objects.requireNonNull(location.getWorld(), "location world");
        return new BlockPosition(world.getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    /**
     * Resolves the block in the current server.
     *
     * @param server server instance
     * @return resolved block or {@code null} when the world is unavailable
     */
    public @Nullable Block resolveBlock(Server server) {
        Objects.requireNonNull(server, "server");
        World world = server.getWorld(this.worldName);
        if (world == null) {
            return null;
        }
        return world.getBlockAt(this.x, this.y, this.z);
    }
}
