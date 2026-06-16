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
import com.sk89q.worldedit.world.block.BlockType;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.config.ConfigManager;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.TileState;

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
    public void prepareSpawnArea(
            World world,
            Path schematicPath,
            int anchorX,
            int anchorZ,
            int fallbackRadius,
            int surfaceY) {
        Objects.requireNonNull(world, "world");
        com.sk89q.worldedit.world.World adaptedWorld = adaptWorld(world);
        TerrainPlan terrainPlan = buildTerrainPlan(schematicPath, anchorX, anchorZ, fallbackRadius);
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight() - 1;
        BlockState fillBlock = resolveFillBlock(world).getDefaultState();
        BlockState topBlock = resolveTopBlock(world).getDefaultState();

        try (EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder()
                .world(adaptedWorld)
                .maxBlocks(-1)
                .build()) {
            removeOverwrittenTileEntities(world, terrainPlan, minY, maxY);
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
            editSession.flushQueue();
            if (this.schematicSettings.replaceAirMarkerAfterPaste()) {
                replaceMarkerBlocks(world, replacementPlan);
                editSession.flushQueue();
            }
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

    private TerrainPlan buildTerrainPlan(Path schematicPath, int anchorX, int anchorZ, int fallbackRadius) {
        if (schematicPath == null || !java.nio.file.Files.isRegularFile(schematicPath)) {
            return TerrainPlan.circular(anchorX, anchorZ, fallbackRadius, FALLBACK_BLEND_DISTANCE);
        }

        ClipboardData clipboardData = loadClipboardData(schematicPath);
        int innerMinX = anchorX + clipboardData.minX() - SCHEMATIC_MARGIN;
        int innerMaxX = anchorX + clipboardData.maxX() + SCHEMATIC_MARGIN;
        int innerMinZ = anchorZ + clipboardData.minZ() - SCHEMATIC_MARGIN;
        int innerMaxZ = anchorZ + clipboardData.maxZ() + SCHEMATIC_MARGIN;
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

    private void removeOverwrittenTileEntities(World world, TerrainPlan terrainPlan, int minY, int maxY) {
        int removedCount = 0;
        Map<String, Integer> removedTypes = new HashMap<>();
        for (int x = terrainPlan.outerMinX(); x <= terrainPlan.outerMaxX(); x++) {
            for (int z = terrainPlan.outerMinZ(); z <= terrainPlan.outerMaxZ(); z++) {
                for (int y = minY; y <= maxY; y++) {
                    org.bukkit.block.Block block = world.getBlockAt(x, y, z);
                    org.bukkit.block.BlockState state = block.getState();
                    if (!(state instanceof TileState)) {
                        continue;
                    }
                    removedTypes.merge(block.getType().getKey().toString(), 1, Integer::sum);
                    block.setType(Material.AIR, false);
                    removedCount++;
                }
            }
        }
        this.plugin.getLogger().info("Removed tile entities before resource-world terrain reshape: count="
                + removedCount
                + ", range=["
                + terrainPlan.outerMinX() + "," + minY + "," + terrainPlan.outerMinZ()
                + "] -> ["
                + terrainPlan.outerMaxX() + "," + maxY + "," + terrainPlan.outerMaxZ()
                + "], types="
                + removedTypes);
    }

    private MarkerReplacementPlan createMarkerReplacementPlan(Clipboard clipboard, int x, int y, int z) {
        Material markerMaterial = resolveMarkerMaterial();
        BlockVector3 targetOrigin = BlockVector3.at(x, y, z);
        BlockVector3 clipboardOrigin = clipboard.getOrigin();
        BlockVector3 regionMinimum = clipboard.getRegion().getMinimumPoint();
        BlockVector3 regionMaximum = clipboard.getRegion().getMaximumPoint();
        BlockVector3 worldMinimum = regionMinimum.subtract(clipboardOrigin).add(targetOrigin);
        BlockVector3 worldMaximum = regionMaximum.subtract(clipboardOrigin).add(targetOrigin);
        return new MarkerReplacementPlan(
                markerMaterial,
                worldMinimum,
                worldMaximum);
    }

    private void replaceMarkerBlocks(World world, MarkerReplacementPlan replacementPlan) {
        int replacedCount = 0;
        for (int x = replacementPlan.worldMinimum().x(); x <= replacementPlan.worldMaximum().x(); x++) {
            for (int y = replacementPlan.worldMinimum().y(); y <= replacementPlan.worldMaximum().y(); y++) {
                for (int z = replacementPlan.worldMinimum().z(); z <= replacementPlan.worldMaximum().z(); z++) {
                    org.bukkit.block.Block block = world.getBlockAt(x, y, z);
                    if (block.getType() != replacementPlan.markerMaterial()) {
                        continue;
                    }
                    block.setType(Material.AIR, false);
                    replacedCount++;
                }
            }
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
        Material material = resolveConfiguredBlockMaterial(configured);
        if (material == null || !material.isBlock()) {
            throw new IllegalStateException("Invalid resource-world air marker block: " + configured);
        }
        return material;
    }

    static Material resolveConfiguredBlockMaterial(String configured) {
        if (configured == null || configured.isBlank()) {
            return null;
        }

        NamespacedKey namespacedKey = NamespacedKey.fromString(configured);
        if (namespacedKey != null) {
            Material registryMaterial = Registry.MATERIAL.get(namespacedKey);
            if (registryMaterial != null) {
                return registryMaterial;
            }
        }

        Material matchedMaterial = Material.matchMaterial(configured, true);
        if (matchedMaterial != null) {
            return matchedMaterial;
        }
        return Material.matchMaterial(configured);
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
                    reshapeColumn(editSession, x, z, minY, maxY, surfaceY, null, null, fillBlock, topBlock);
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

        Map<ColumnKey, ColumnSnapshot> capturedColumns = captureOuterColumns(world, terrainPlan, minY, maxY);
        Map<ColumnKey, Integer> smoothedHeights = smoothOuterHeights(extractTerrainHeights(capturedColumns), minY, maxY);
        for (Map.Entry<ColumnKey, Integer> entry : smoothedHeights.entrySet()) {
            ColumnKey column = entry.getKey();
            int targetSurfaceY = calculateBlendedSurfaceY(
                    terrainPlan,
                    column.x(),
                    column.z(),
                    surfaceY,
                    entry.getValue(),
                    minY,
                    maxY);
            ColumnSnapshot snapshot = capturedColumns.get(column);
            reshapeColumn(
                    editSession,
                    column.x(),
                    column.z(),
                    minY,
                    maxY,
                    targetSurfaceY,
                    snapshot == null ? null : snapshot.liquidState(),
                    snapshot == null ? null : snapshot.liquidTopY(),
                    fillBlock,
                    topBlock);
        }
    }

    private Map<ColumnKey, ColumnSnapshot> captureOuterColumns(World world, TerrainPlan terrainPlan, int minY, int maxY) {
        Map<ColumnKey, ColumnSnapshot> columns = new HashMap<>();
        for (int x = terrainPlan.outerMinX(); x <= terrainPlan.outerMaxX(); x++) {
            for (int z = terrainPlan.outerMinZ(); z <= terrainPlan.outerMaxZ(); z++) {
                if (terrainPlan.isInsideInnerArea(x, z)) {
                    continue;
                }
                columns.put(new ColumnKey(x, z), captureColumnSnapshot(world, x, z, minY, maxY));
            }
        }
        return columns;
    }

    private Map<ColumnKey, Integer> extractTerrainHeights(Map<ColumnKey, ColumnSnapshot> capturedColumns) {
        Map<ColumnKey, Integer> heights = new HashMap<>();
        for (Map.Entry<ColumnKey, ColumnSnapshot> entry : capturedColumns.entrySet()) {
            heights.put(entry.getKey(), entry.getValue().terrainSurfaceY());
        }
        return heights;
    }

    private ColumnSnapshot captureColumnSnapshot(World world, int x, int z, int minY, int maxY) {
        int terrainSurfaceY = sampleTerrainSurfaceY(world, x, z, minY, maxY, false);
        int highestY = clamp(world.getHighestBlockYAt(x, z), minY + 1, maxY);
        Material highestMaterial = world.getBlockAt(x, highestY, z).getType();
        if (!isLiquidSurfaceMaterial(highestMaterial)) {
            return new ColumnSnapshot(terrainSurfaceY, null, null);
        }

        BlockState liquidState = resolveLiquidState(highestMaterial);
        if (liquidState == null) {
            return new ColumnSnapshot(terrainSurfaceY, null, null);
        }
        return new ColumnSnapshot(terrainSurfaceY, highestY, liquidState);
    }

    private Map<ColumnKey, Integer> smoothOuterHeights(
            Map<ColumnKey, Integer> capturedHeights,
            int minY,
            int maxY) {
        Map<ColumnKey, Integer> smoothed = new HashMap<>();
        for (Map.Entry<ColumnKey, Integer> entry : capturedHeights.entrySet()) {
            ColumnKey center = entry.getKey();
            int total = entry.getValue() * 2;
            int samples = 2;
            for (int offsetX = -1; offsetX <= 1; offsetX++) {
                for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
                    if (offsetX == 0 && offsetZ == 0) {
                        continue;
                    }
                    Integer neighborHeight = capturedHeights.get(new ColumnKey(center.x() + offsetX, center.z() + offsetZ));
                    if (neighborHeight == null) {
                        continue;
                    }
                    total += neighborHeight;
                    samples++;
                }
            }
            smoothed.put(center, clamp((int) Math.round(total / (double) samples), minY + 1, maxY));
        }
        return smoothed;
    }

    static int sampleTerrainSurfaceY(World world, int x, int z, int minY, int maxY) {
        return sampleTerrainSurfaceY(world, x, z, minY, maxY, false);
    }

    static int sampleTerrainSurfaceY(
            World world,
            int x,
            int z,
            int minY,
            int maxY,
            boolean allowLiquidSurface) {
        int highestY = clamp(world.getHighestBlockYAt(x, z), minY + 1, maxY);
        Material highestMaterial = world.getBlockAt(x, highestY, z).getType();
        if (allowLiquidSurface && isLiquidSurfaceMaterial(highestMaterial)) {
            return highestY;
        }
        for (int y = highestY; y >= minY + 1; y--) {
            Material material = world.getBlockAt(x, y, z).getType();
            if (isTerrainSurfaceMaterial(material)) {
                return y;
            }
        }
        return minY + 1;
    }

    static boolean isTerrainSurfaceMaterial(Material material) {
        if (material.isAir() || !material.isSolid()) {
            return false;
        }
        if (Tag.LOGS.isTagged(material) || Tag.LEAVES.isTagged(material)) {
            return false;
        }
        if (Tag.SNOW.isTagged(material) || Tag.FLOWERS.isTagged(material) || Tag.SAPLINGS.isTagged(material)) {
            return false;
        }
        String materialName = material.name();
        return !materialName.endsWith("_BUSH")
                && !materialName.endsWith("_GRASS")
                && !materialName.endsWith("_FERN")
                && !materialName.contains("MUSHROOM")
                && !materialName.contains("VINE")
                && !materialName.contains("BAMBOO")
                && !materialName.contains("ROOTS")
                && !materialName.contains("CORAL")
                && !materialName.contains("KELP")
                && !materialName.contains("SEAGRASS");
    }

    static boolean isLiquidSurfaceMaterial(Material material) {
        return material == Material.WATER
                || material == Material.LAVA
                || material == Material.BUBBLE_COLUMN;
    }

    static List<ColumnKey> perimeterColumns(int radius) {
        List<ColumnKey> columns = new ArrayList<>();
        for (int x = -radius; x <= radius; x++) {
            columns.add(new ColumnKey(x, -radius));
            columns.add(new ColumnKey(x, radius));
        }
        for (int z = (-radius + 1); z <= (radius - 1); z++) {
            columns.add(new ColumnKey(-radius, z));
            columns.add(new ColumnKey(radius, z));
        }
        return columns;
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
        double centerX = (terrainPlan.innerMinX() + terrainPlan.innerMaxX()) / 2.0D;
        double centerZ = (terrainPlan.innerMinZ() + terrainPlan.innerMaxZ()) / 2.0D;
        double distance = Math.sqrt(Math.pow(x - centerX, 2.0D) + Math.pow(z - centerZ, 2.0D));
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

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    private void reshapeColumn(
            EditSession editSession,
            int x,
            int z,
            int minY,
            int maxY,
            int targetSurfaceY,
            BlockState liquidState,
            Integer liquidTopY,
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
        BlockState columnTopBlock = topBlock;
        if (liquidState != null && liquidTopY != null) {
            columnTopBlock = fillBlock;
        }
        editSession.setBlock(BlockVector3.at(x, targetSurfaceY, z), columnTopBlock);

        if (liquidState != null && liquidTopY != null) {
            int restoredTopY = clamp(liquidTopY, targetSurfaceY + 1, maxY);
            if (restoredTopY > targetSurfaceY) {
                CuboidRegion liquidRegion = new CuboidRegion(
                        BlockVector3.at(x, targetSurfaceY + 1, z),
                        BlockVector3.at(x, restoredTopY, z));
                editSession.setBlocks((com.sk89q.worldedit.regions.Region) liquidRegion, liquidState);
            }
        }
    }

    private BlockState resolveLiquidState(Material material) {
        BlockType liquidType = switch (material) {
            case WATER, BUBBLE_COLUMN -> BlockTypes.WATER;
            case LAVA -> BlockTypes.LAVA;
            default -> null;
        };
        return liquidType == null ? null : liquidType.getDefaultState();
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

    private record ColumnSnapshot(int terrainSurfaceY, Integer liquidTopY, BlockState liquidState) {
    }

    private record MarkerReplacementPlan(
            Material markerMaterial,
            BlockVector3 worldMinimum,
            BlockVector3 worldMaximum) {

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

        private static TerrainPlan circular(int centerX, int centerZ, int innerRadius, int blendDistance) {
            return new TerrainPlan(
                    centerX - innerRadius,
                    centerX + innerRadius,
                    centerZ - innerRadius,
                    centerZ + innerRadius,
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
                double centerX = (this.innerMinX + this.innerMaxX) / 2.0D;
                double centerZ = (this.innerMinZ + this.innerMaxZ) / 2.0D;
                double deltaX = x - centerX;
                double deltaZ = z - centerZ;
                return ((deltaX * deltaX) + (deltaZ * deltaZ)) <= (this.innerRadius * (double) this.innerRadius);
            }
            return x >= this.innerMinX && x <= this.innerMaxX
                    && z >= this.innerMinZ && z <= this.innerMaxZ;
        }
    }

    record ColumnKey(int x, int z) {
    }
}
