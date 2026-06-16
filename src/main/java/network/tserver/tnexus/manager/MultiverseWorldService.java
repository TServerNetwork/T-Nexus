package network.tserver.tnexus.manager;

import org.bukkit.Location;
import org.bukkit.World;

/**
 * Minimal Multiverse world operations required by T-Nexus.
 */
public interface MultiverseWorldService {

    /**
     * Unloads a managed world.
     *
     * @param worldName world name
     * @return {@code true} when the unload succeeds
     */
    boolean unloadWorld(String worldName);

    /**
     * Removes a managed world from the Multiverse registry.
     *
     * @param worldName world name
     * @return {@code true} when the removal succeeds
     */
    boolean removeWorld(String worldName);

    /**
     * Imports a Bukkit-loaded world into the Multiverse registry.
     *
     * @param worldName world name
     * @param environment world environment
     * @return {@code true} when the import succeeds
     */
    boolean importWorld(String worldName, World.Environment environment);

    /**
     * Regenerates a managed world with the provided seed.
     *
     * @param worldName world name
     * @param seed world seed string
     * @return {@code true} when the regeneration succeeds
     */
    boolean regenerateWorld(String worldName, String seed);

    /**
     * Loads a managed world.
     *
     * @param worldName world name
     * @return {@code true} when the load succeeds
     */
    boolean loadWorld(String worldName);

    /**
     * Updates the managed-world spawn location when supported by the provider.
     *
     * @param worldName world name
     * @param spawnLocation target spawn location
     * @return {@code true} when the update succeeds or is unsupported but non-fatal
     */
    default boolean setSpawnLocation(String worldName, Location spawnLocation) {
        return true;
    }
}
