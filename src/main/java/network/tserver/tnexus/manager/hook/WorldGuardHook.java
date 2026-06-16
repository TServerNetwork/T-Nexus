package network.tserver.tnexus.manager.hook;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.managers.storage.StorageException;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import java.util.Objects;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

/**
 * Required hook for WorldGuard.
 */
public final class WorldGuardHook implements PluginHook<WorldGuardHook.WorldGuardRegionService> {

    private WorldGuardRegionService regionService;

    @Override
    public String getPluginName() {
        return "WorldGuard";
    }

    @Override
    public boolean isRequired() {
        return true;
    }

    @Override
    public WorldGuardRegionService getApi() {
        return this.regionService;
    }

    @Override
    public boolean hook(Plugin plugin) {
        if (plugin == null || !getPluginName().equals(plugin.getName())) {
            return false;
        }
        this.regionService = new BukkitWorldGuardRegionService();
        return true;
    }

    /**
     * Creates or replaces spawn protection regions in WorldGuard.
     */
    public interface WorldGuardRegionService {

        /**
         * Creates or replaces a cuboid spawn-protection region.
         *
         * @param world target world
         * @param regionName region name
         * @param spawnLocation spawn center
         * @param radius protection radius
         */
        void createOrReplaceSpawnProtection(World world, String regionName, Location spawnLocation, int radius);
    }

    private static final class BukkitWorldGuardRegionService implements WorldGuardRegionService {

        @Override
        public void createOrReplaceSpawnProtection(
                World world,
                String regionName,
                Location spawnLocation,
                int radius) {
            Objects.requireNonNull(world, "world");
            Objects.requireNonNull(regionName, "regionName");
            Objects.requireNonNull(spawnLocation, "spawnLocation");
            if (radius < 0) {
                throw new IllegalArgumentException("radius must be non-negative");
            }

            RegionManager regionManager = WorldGuard.getInstance()
                    .getPlatform()
                    .getRegionContainer()
                    .get(BukkitAdapter.adapt(world));
            if (regionManager == null) {
                throw new IllegalStateException("WorldGuard region manager is not available for " + world.getName());
            }

            regionManager.removeRegion(regionName);
            int centerX = spawnLocation.getBlockX();
            int centerZ = spawnLocation.getBlockZ();
            BlockVector3 minimumPoint = BlockVector3.at(
                    centerX - radius,
                    world.getMinHeight(),
                    centerZ - radius);
            BlockVector3 maximumPoint = BlockVector3.at(
                    centerX + radius,
                    world.getMaxHeight() - 1,
                    centerZ + radius);
            ProtectedCuboidRegion region = new ProtectedCuboidRegion(regionName, minimumPoint, maximumPoint);
            region.setFlag(Flags.PVP, StateFlag.State.DENY);
            region.setFlag(Flags.BLOCK_BREAK, StateFlag.State.DENY);
            region.setFlag(Flags.BLOCK_PLACE, StateFlag.State.DENY);
            regionManager.addRegion(region);
            try {
                regionManager.saveChanges();
            } catch (StorageException exception) {
                throw new IllegalStateException("Failed to save WorldGuard region " + regionName, exception);
            }
        }
    }
}
