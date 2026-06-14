package network.tserver.tnexus.manager;

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
}
