package network.tserver.tnexus.manager;

import java.nio.file.Path;
import org.bukkit.World;

/**
 * Provides FAWE-backed edit operations for resource worlds.
 */
public interface ResourceWorldEditService {

    /**
     * Flattens the area around world origin to a constant surface level.
     *
     * @param world target world
     * @param radius flatten radius around 0,0
     * @param surfaceY final surface height
     */
    void flattenArea(World world, int radius, int surfaceY);

    /**
     * Pastes the given schematic at the provided world coordinates.
     *
     * @param world target world
     * @param schematicPath schematic path
     * @param x target x
     * @param y target y
     * @param z target z
     */
    void pasteSchematic(World world, Path schematicPath, int x, int y, int z);
}
