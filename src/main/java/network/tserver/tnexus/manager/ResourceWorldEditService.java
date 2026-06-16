package network.tserver.tnexus.manager;

import java.nio.file.Path;
import org.bukkit.World;

/**
 * Provides FAWE-backed edit operations for resource worlds.
 */
public interface ResourceWorldEditService {

    /**
     * Prepares the terrain around spawn for the given schematic.
     *
     * @param world target world
     * @param schematicPath schematic path; when absent, implementations may fall back to a simple flat area
     * @param anchorX x coordinate of the schematic origin / spawn anchor
     * @param anchorZ z coordinate of the schematic origin / spawn anchor
     * @param fallbackRadius fallback radius around the anchor when no schematic is present
     * @param surfaceY final surface height at the schematic origin
     */
    void prepareSpawnArea(World world, Path schematicPath, int anchorX, int anchorZ, int fallbackRadius, int surfaceY);

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
