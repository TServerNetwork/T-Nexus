package network.tserver.tnexus.manager;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.block.BlockTypes;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Objects;
import network.tserver.tnexus.TNexus;
import org.bukkit.World;

/**
 * Default FAWE-backed resource-world edit service.
 */
public final class FaweResourceWorldEditService implements ResourceWorldEditService {

    private final TNexus plugin;

    /**
     * Creates a new FAWE edit service.
     *
     * @param plugin owner plugin
     */
    public FaweResourceWorldEditService(TNexus plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public void flattenArea(World world, int radius, int surfaceY) {
        Objects.requireNonNull(world, "world");
        com.sk89q.worldedit.world.World adaptedWorld = adaptWorld(world);
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight() - 1;
        BlockVector3 min = BlockVector3.at(-radius, minY, -radius);
        BlockVector3 max = BlockVector3.at(radius, maxY, radius);

        try (EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder()
                .world(adaptedWorld)
                .maxBlocks(-1)
                .build()) {
            CuboidRegion clearRegion = new CuboidRegion(min, max);
            editSession.<com.sk89q.worldedit.world.block.BlockState>setBlocks(
                    (com.sk89q.worldedit.regions.Region) clearRegion,
                    BlockTypes.AIR.getDefaultState());
            if (surfaceY > minY) {
                CuboidRegion fillRegion = new CuboidRegion(
                        BlockVector3.at(-radius, minY, -radius),
                        BlockVector3.at(radius, surfaceY - 1, radius));
                editSession.<com.sk89q.worldedit.world.block.BlockState>setBlocks(
                        (com.sk89q.worldedit.regions.Region) fillRegion,
                        resolveFillBlock(world).getDefaultState());
            }
            CuboidRegion topRegion = new CuboidRegion(
                    BlockVector3.at(-radius, surfaceY, -radius),
                    BlockVector3.at(radius, surfaceY, radius));
            editSession.<com.sk89q.worldedit.world.block.BlockState>setBlocks(
                    (com.sk89q.worldedit.regions.Region) topRegion,
                    resolveTopBlock(world).getDefaultState());
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
            ClipboardHolder holder = new ClipboardHolder(reader.read());
            Operations.complete(holder.createPaste(editSession)
                    .to(BlockVector3.at(x, y, z))
                    .ignoreAirBlocks(false)
                    .copyBiomes(true)
                    .copyEntities(true)
                    .build());
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
}
