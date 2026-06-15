package network.tserver.tnexus.manager;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockTypes;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.config.ConfigManager;
import org.bukkit.Material;
import org.bukkit.World;

/**
 * Default FAWE-backed resource-world edit service.
 */
public final class FaweResourceWorldEditService implements ResourceWorldEditService {

    private static final int SCHEMATIC_MARGIN = 4;
    private static final int BLEND_DISTANCE = 12;
    private static final int FALLBACK_BLEND_DISTANCE = 6;

    private final TNexus plugin;
    private final ConfigManager.ResourceWorldSpawnSchematicSettings schematicSettings;

    /**
     * Creates a new FAWE edit service.
     *
     * @param plugin owner plugin
     */
    public FaweResourceWorldEditService(TNexus plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.schematicSettings = this.plugin.getConfigManager().getResourceWorldSettings().spawnSchematicSettings();
    }

    @Override
    public void prepareSpawnArea(World world, Path schematicPath, int fallbackRadius, int surfaceY) {
        Objects.requireNonNull(world, "world");
        com.sk89q.worldedit.world.World adaptedWorld = adaptWorld(world);
        TerrainPlan terrainPlan = buildTerrainPlan(world, schematicPath, fallbackRadius);
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight() - 1;
        BlockState fillBlock = resolveFillBlock(world).getDefaultState();
        BlockState topBlock = resolveTopBlock(world).getDefaultState();

        try (EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder()
                .world(adaptedWorld)
                .maxBlocks(-1)
                .build()) {
            flattenInnerArea(editSession, terrainPlan, minY, maxY, surfaceY, fillBlock, topBlock);
            blendOuterArea(editSession, world, terrainPlan, minY, maxY, surfaceY, fillBlock, topBlock);
            editSession.flushQueue();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to flatten resource world with FAWE", exception);
        }
    }

    @Override
    public void pasteSchematic(World world, Path schematicPath, int x, int y, int z) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(schematicPath, "schematicPath");

        var clipboardFormat = ClipboardFormats.findByFile(schematicPath.toFile());
        if (clipboardFormat == null) {
            throw new IllegalStateException("Unsupported schematic format: " + schematicPath);
        }

        com.sk89q.worldedit.world.World adaptedWorld = adaptWorld(world);
        try (FileInputStream inputStream = new FileInputStream(schematicPath.toFile());
             var reader = clipboardFormat.getReader(inputStream);
             EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder()
                     .world(adaptedWorld)
                     .maxBlocks(-1)
                     .build()) {
            Clipboard clipboard = reader.read();
            ClipboardHolder holder = new ClipboardHolder(clipboard);
            MarkerReplacementPlan replacementPlan = createMarkerReplacementPlan(clipboard, x, y, z);
            this.plugin.getLogger().info("Pasting resource schematic with ignoreAirBlocks="
                    + this.schematicSettings.ignoreAirBlocks()
                    + ", airMarkerBlock="
                    + this.schematicSettings.airMarkerBlock()
                    + ", replacementRange="
                    + replacementPlan.worldBounds());
            Operations.complete(holder.createPaste(editSession)
                    .to(BlockVector3.at(x, y, z))
                    .ignoreAirBlocks(this.schematicSettings.ignoreAirBlocks())
                    .copyBiomes(true)
                    .copyEntities(true)
                    .build());
            if (this.schematicSettings.replaceAirMarkerAfterPaste()) {
                replaceMarkerBlocks(editSession, replacementPlan);
            }
            editSession.flushQueue();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read resource world schematic", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to paste resource world schematic with FAWE", exception);
        }
    }

    private com.sk89q.worldedit.world.World adaptWorld(World world) {
        try {
            Class<?> adapterClass = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
            Method adaptMethod = adapterClass.getMethod("adapt", World.class);
            return (com.sk89q.worldedit.world.World) adaptMethod.invoke(null, world);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("WorldEdit Bukkit adapter is not available", exception);
        }
    }

    private TerrainPlan buildTerrainPlan(World world, Path schematicPath, int fallbackRadius) {
        if (schematicPath == null || !java.nio.file.Files.isRegularFile(schematicPath)) {
            return TerrainPlan.circular(fallbackRadius, FALLBACK_BLEND_DISTANCE);
        }

        ClipboardData clipboardData = loadClipboardData(schematicPath);
        int innerMinX = clipboardData.minX() - SCHEMATIC_MARGIN;
        int innerMaxX = clipboardData.maxX() + SCHEMATIC_MARGIN;
        int innerMinZ = clipboardData.minZ() - SCHEMATIC_MARGIN;
        int innerMaxZ = clipboardData.maxZ() + SCHEMATIC_MARGIN;
        return TerrainPlan.rectangular(innerMinX, innerMaxX, innerMinZ, innerMaxZ, BLEND_DISTANCE);
    }

    private ClipboardData loadClipboardData(Path schematicPath) {
        var clipboardFormat = ClipboardFormats.findByFile(schematicPath.toFile());
        if (clipboardFormat == null) {
            throw new IllegalStateException("Unsupported schematic format: " + schematicPath);
        }

        try (FileInputStream inputStream = new FileInputStream(schematicPath.toFile());
             var reader = clipboardFormat.getReader(inputStream)) {
            var clipboard = reader.read();
            BlockVector3 minimum = clipboard.getRegion().getMinimumPoint();
            BlockVector3 maximum = clipboard.getRegion().getMaximumPoint();
            BlockVector3 origin = clipboard.getOrigin();
            return new ClipboardData(
                    minimum.x() - origin.x(),
                    maximum.x() - origin.x(),
                    minimum.z() - origin.z(),
                    maximum.z() - origin.z());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read resource world schematic", exception);
        }
    }

    private MarkerReplacementPlan createMarkerReplacementPlan(Clipboard clipboard, int x, int y, int z) {
        Material markerMaterial = resolveMarkerMaterial();
        String markerBlockId = markerMaterial.getKey().toString();
        BlockVector3 targetOrigin = BlockVector3.at(x, y, z);
        BlockVector3 clipboardOrigin = clipboard.getOrigin();
        BlockVector3 regionMinimum = clipboard.getRegion().getMinimumPoint();
        BlockVector3 regionMaximum = clipboard.getRegion().getMaximumPoint();
        BlockVector3 worldMinimum = regionMinimum.subtract(clipboardOrigin).add(targetOrigin);
        BlockVector3 worldMaximum = regionMaximum.subtract(clipboardOrigin).add(targetOrigin);
        Map<BlockVector3, Boolean> markerPositions = new HashMap<>();
        for (BlockVector3 position : clipboard.getRegion()) {
            if (!clipboard.getBlock(position).getBlockType().id().equals(markerBlockId)) {
                continue;
            }
            BlockVector3 worldPosition = position.subtract(clipboardOrigin).add(targetOrigin);
            markerPositions.put(worldPosition, Boolean.TRUE);
        }
        return new MarkerReplacementPlan(
                markerMaterial,
                worldMinimum,
                worldMaximum,
                markerPositions);
    }

    private void replaceMarkerBlocks(EditSession editSession, MarkerReplacementPlan replacementPlan) throws Exception {
        int replacedCount = 0;
        for (BlockVector3 markerPosition : replacementPlan.markerPositions().keySet()) {
            editSession.setBlock(markerPosition, BlockTypes.AIR.getDefaultState());
            replacedCount++;
        }
        this.plugin.getLogger().info("Replaced air marker blocks after schematic paste: marker="
                + replacementPlan.markerMaterial().getKey()
                + ", count="
                + replacedCount
                + ", range="
                + replacementPlan.worldBounds());
    }

    private Material resolveMarkerMaterial() {
        String configured = this.schematicSettings.airMarkerBlock();
        Material material = Material.matchMaterial(configured, true);
        if (material == null || !material.isBlock()) {
            throw new IllegalStateException("Invalid resource-world air marker block: " + configured);
        }
        return material;
    }

    private void flattenInnerArea(
            EditSession editSession,
            TerrainPlan terrainPlan,
            int minY,
            int maxY,
            int surfaceY,
            BlockState fillBlock,
            BlockState topBlock) throws Exception {
        if (terrainPlan.circular()) {
            for (int x = terrainPlan.outerMinX(); x <= terrainPlan.outerMaxX(); x++) {
                for (int z = terrainPlan.outerMinZ(); z <= terrainPlan.outerMaxZ(); z++) {
                    if (!terrainPlan.isInsideInnerArea(x, z)) {
                        continue;
                    }
                    reshapeColumn(editSession, x, z, minY, maxY, surfaceY, fillBlock, topBlock);
                }
            }
            return;
        }

        CuboidRegion clearRegion = new CuboidRegion(
                BlockVector3.at(terrainPlan.innerMinX(), minY, terrainPlan.innerMinZ()),
                BlockVector3.at(terrainPlan.innerMaxX(), maxY, terrainPlan.innerMaxZ()));
        editSession.setBlocks((com.sk89q.worldedit.regions.Region) clearRegion, BlockTypes.AIR.getDefaultState());

        if (surfaceY > minY) {
            CuboidRegion fillRegion = new CuboidRegion(
                    BlockVector3.at(terrainPlan.innerMinX(), minY, terrainPlan.innerMinZ()),
                    BlockVector3.at(terrainPlan.innerMaxX(), surfaceY - 1, terrainPlan.innerMaxZ()));
            editSession.setBlocks((com.sk89q.worldedit.regions.Region) fillRegion, fillBlock);
        }

        CuboidRegion topRegion = new CuboidRegion(
                BlockVector3.at(terrainPlan.innerMinX(), surfaceY, terrainPlan.innerMinZ()),
                BlockVector3.at(terrainPlan.innerMaxX(), surfaceY, terrainPlan.innerMaxZ()));
        editSession.setBlocks((com.sk89q.worldedit.regions.Region) topRegion, topBlock);
    }

    private void blendOuterArea(
            EditSession editSession,
            World world,
            TerrainPlan terrainPlan,
            int minY,
            int maxY,
            int surfaceY,
            BlockState fillBlock,
            BlockState topBlock) throws Exception {
        if (terrainPlan.blendDistance() <= 0) {
            return;
        }

        Map<ColumnKey, Integer> originalHeights = captureOuterHeights(world, terrainPlan);
        for (Map.Entry<ColumnKey, Integer> entry : originalHeights.entrySet()) {
            ColumnKey column = entry.getKey();
            int targetSurfaceY = calculateBlendedSurfaceY(
                    terrainPlan,
                    column.x(),
                    column.z(),
                    surfaceY,
                    entry.getValue(),
                    minY,
                    maxY);
            reshapeColumn(editSession, column.x(), column.z(), minY, maxY, targetSurfaceY, fillBlock, topBlock);
        }
    }

    private Map<ColumnKey, Integer> captureOuterHeights(World world, TerrainPlan terrainPlan) {
        Map<ColumnKey, Integer> heights = new HashMap<>();
        for (int x = terrainPlan.outerMinX(); x <= terrainPlan.outerMaxX(); x++) {
            for (int z = terrainPlan.outerMinZ(); z <= terrainPlan.outerMaxZ(); z++) {
                if (terrainPlan.isInsideInnerArea(x, z)) {
                    continue;
                }
                heights.put(new ColumnKey(x, z), world.getHighestBlockYAt(x, z));
            }
        }
        return heights;
    }

    private int calculateBlendedSurfaceY(
            TerrainPlan terrainPlan,
            int x,
            int z,
            int surfaceY,
            int originalHeight,
            int minY,
            int maxY) {
        int blendStep = terrainPlan.circular()
                ? circularBlendStep(terrainPlan, x, z)
                : Math.max(
                        distanceOutsideRange(x, terrainPlan.innerMinX(), terrainPlan.innerMaxX()),
                        distanceOutsideRange(z, terrainPlan.innerMinZ(), terrainPlan.innerMaxZ()));
        if (blendStep <= 0) {
            return clamp(surfaceY, minY + 1, maxY);
        }

        double ratio = Math.min(1.0D, blendStep / (double) terrainPlan.blendDistance());
        int blended = (int) Math.round(surfaceY + ((originalHeight - surfaceY) * ratio));
        return clamp(blended, minY + 1, maxY);
    }

    private int circularBlendStep(TerrainPlan terrainPlan, int x, int z) {
        double distance = Math.sqrt((x * (double) x) + (z * (double) z));
        return (int) Math.ceil(Math.max(0.0D, distance - terrainPlan.innerRadius()));
    }

    private int distanceOutsideRange(int value, int min, int max) {
        if (value < min) {
            return min - value;
        }
        if (value > max) {
            return value - max;
        }
        return 0;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    private void reshapeColumn(
            EditSession editSession,
            int x,
            int z,
            int minY,
            int maxY,
            int targetSurfaceY,
            BlockState fillBlock,
            BlockState topBlock) throws Exception {
        if (targetSurfaceY < maxY) {
            CuboidRegion clearRegion = new CuboidRegion(
                    BlockVector3.at(x, targetSurfaceY + 1, z),
                    BlockVector3.at(x, maxY, z));
            editSession.setBlocks((com.sk89q.worldedit.regions.Region) clearRegion, BlockTypes.AIR.getDefaultState());
        }
        if (targetSurfaceY > minY) {
            CuboidRegion fillRegion = new CuboidRegion(
                    BlockVector3.at(x, minY, z),
                    BlockVector3.at(x, targetSurfaceY - 1, z));
            editSession.setBlocks((com.sk89q.worldedit.regions.Region) fillRegion, fillBlock);
        }
        editSession.setBlock(BlockVector3.at(x, targetSurfaceY, z), topBlock);
    }

    private com.sk89q.worldedit.world.block.BlockType resolveFillBlock(World world) {
        return switch (world.getEnvironment()) {
            case NETHER -> BlockTypes.NETHERRACK;
            case THE_END -> BlockTypes.END_STONE;
            default -> BlockTypes.DIRT;
        };
    }

    private com.sk89q.worldedit.world.block.BlockType resolveTopBlock(World world) {
        return switch (world.getEnvironment()) {
            case NETHER -> BlockTypes.NETHERRACK;
            case THE_END -> BlockTypes.END_STONE;
            default -> BlockTypes.GRASS_BLOCK;
        };
    }

    private record ClipboardData(int minX, int maxX, int minZ, int maxZ) {
    }

    private record MarkerReplacementPlan(
            Material markerMaterial,
            BlockVector3 worldMinimum,
            BlockVector3 worldMaximum,
            Map<BlockVector3, Boolean> markerPositions) {

        private String worldBounds() {
            return "[" + this.worldMinimum.x() + "," + this.worldMinimum.y() + "," + this.worldMinimum.z()
                    + "] -> ["
                    + this.worldMaximum.x() + "," + this.worldMaximum.y() + "," + this.worldMaximum.z() + "]";
        }
    }

    private record TerrainPlan(
            int innerMinX,
            int innerMaxX,
            int innerMinZ,
            int innerMaxZ,
            int blendDistance,
            boolean circular,
            int innerRadius) {

        private static TerrainPlan rectangular(
                int innerMinX,
                int innerMaxX,
                int innerMinZ,
                int innerMaxZ,
                int blendDistance) {
            return new TerrainPlan(innerMinX, innerMaxX, innerMinZ, innerMaxZ, blendDistance, false, 0);
        }

        private static TerrainPlan circular(int innerRadius, int blendDistance) {
            return new TerrainPlan(
                    -innerRadius,
                    innerRadius,
                    -innerRadius,
                    innerRadius,
                    blendDistance,
                    true,
                    innerRadius);
        }

        private int outerMinX() {
            return this.innerMinX - this.blendDistance;
        }

        private int outerMaxX() {
            return this.innerMaxX + this.blendDistance;
        }

        private int outerMinZ() {
            return this.innerMinZ - this.blendDistance;
        }

        private int outerMaxZ() {
            return this.innerMaxZ + this.blendDistance;
        }

        private boolean isInsideInnerArea(int x, int z) {
            if (this.circular) {
                return ((x * x) + (z * z)) <= (this.innerRadius * this.innerRadius);
            }
            return x >= this.innerMinX && x <= this.innerMaxX
                    && z >= this.innerMinZ && z <= this.innerMaxZ;
        }
    }

    private record ColumnKey(int x, int z) {
    }
}
