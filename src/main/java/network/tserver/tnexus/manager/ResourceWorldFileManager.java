package network.tserver.tnexus.manager;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Objects;
import java.security.SecureRandom;
import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.config.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

/**
 * Handles filesystem work for resource-world resets.
 */
public class ResourceWorldFileManager {

    private static final List<String> STRUCTURE_SEED_KEYS = List.of(
            "seed-village",
            "seed-desert",
            "seed-igloo",
            "seed-jungle",
            "seed-swamp",
            "seed-monument",
            "seed-ocean",
            "seed-outpost",
            "seed-endcity",
            "seed-slime",
            "seed-nether",
            "seed-mansion",
            "seed-fossil",
            "seed-portal");
    private static final String WORLD_SETTINGS_PATH = "world-settings";

    private final TNexus plugin;
    private final ConfigManager.ResourceWorldSettings settings;
    private final SecureRandom secureRandom;

    /**
     * Creates a new file manager.
     *
     * @param plugin owner plugin
     */
    public ResourceWorldFileManager(TNexus plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.settings = this.plugin.getConfigManager().getResourceWorldSettings();
        this.secureRandom = new SecureRandom();
    }

    /**
     * Creates a rotated backup for the given world folder.
     *
     * @param worldName world name
     */
    public void backupWorld(String worldName) {
        Path worldFolder = getWorldFolder(worldName);
        if (!Files.isDirectory(worldFolder)) {
            throw new IllegalStateException("World folder does not exist: " + worldFolder);
        }

        Path backupRoot = getBackupWorldRoot(worldName);
        rotateBackups(backupRoot, Math.max(1, this.settings.backupGenerations()));
        copyDirectory(worldFolder, backupRoot.resolve("1"));
    }

    /**
     * Restores the latest backup generation into the world folder.
     *
     * @param worldName world name
     */
    public void restoreLatestBackup(String worldName) {
        Path latestBackup = getBackupWorldRoot(worldName).resolve("1");
        if (!Files.isDirectory(latestBackup)) {
            throw new IllegalStateException("Latest backup does not exist for " + worldName);
        }

        Path worldFolder = getWorldFolder(worldName);
        deleteDirectoryIfExists(worldFolder);
        copyDirectory(latestBackup, worldFolder);
    }

    /**
     * Deletes the world folder if it exists.
     *
     * @param worldName world name
     */
    public void deleteWorldFolder(String worldName) {
        deleteDirectoryIfExists(getWorldFolder(worldName));
    }

    /**
     * Returns whether the latest backup exists.
     *
     * @param worldName world name
     * @return {@code true} when generation 1 exists
     */
    public boolean hasLatestBackup(String worldName) {
        return Files.isDirectory(getBackupWorldRoot(worldName).resolve("1"));
    }

    /**
     * Updates per-world structure seeds in spigot.yml.
     *
     * @param worldName world name
     * @return base random seed used for regeneration
     */
    public long randomizeStructureSeeds(String worldName) {
        long baseSeed = this.secureRandom.nextLong();
        Path spigotConfigPath = this.plugin.getServer().getWorldContainer().toPath().resolve("spigot.yml");
        try {
            updateStructureSeeds(spigotConfigPath, worldName);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to update spigot.yml structure seeds", exception);
        }
        return baseSeed;
    }

    /**
     * Returns the resource-world schematic path for the given world.
     *
     * @param worldName world name
     * @return schematic path
     */
    public Path getSpawnSchematicPath(String worldName) {
        return this.plugin.getDataFolder().toPath()
                .resolve("schematics")
                .resolve(worldName)
                .resolve("spawn.schem");
    }

    private Path getWorldFolder(String worldName) {
        return resolveWorldFolder(worldName).toPath();
    }

    private File resolveWorldFolder(String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            throw new IllegalStateException("World not loaded: " + worldName);
        }

        File worldFolder = world.getWorldFolder();
        if (!worldFolder.exists()) {
            throw new IllegalStateException("World folder does not exist: " + worldFolder.getAbsolutePath());
        }
        return worldFolder;
    }

    @SuppressWarnings("unchecked")
    private void updateStructureSeeds(Path spigotConfigPath, String worldName) throws IOException {
        String content = Files.exists(spigotConfigPath) ? Files.readString(spigotConfigPath) : "";
        Yaml yaml = new Yaml(createDumperOptions());
        Object loaded = content.isBlank() ? null : yaml.load(content);
        java.util.Map<String, Object> root = loaded instanceof java.util.Map<?, ?> map
                ? (java.util.Map<String, Object>) map
                : new java.util.LinkedHashMap<>();
        java.util.Map<String, Object> worldSettings = getOrCreateMap(root, WORLD_SETTINGS_PATH);
        java.util.Map<String, Object> worldSection = getOrCreateMap(worldSettings, worldName);
        for (String key : STRUCTURE_SEED_KEYS) {
            worldSection.put(key, this.secureRandom.nextLong());
        }
        Files.writeString(spigotConfigPath, yaml.dump(root));
    }

    @SuppressWarnings("unchecked")
    private java.util.Map<String, Object> getOrCreateMap(java.util.Map<String, Object> parent, String key) {
        Object existing = parent.get(key);
        if (existing instanceof java.util.Map<?, ?> map) {
            return (java.util.Map<String, Object>) map;
        }

        java.util.Map<String, Object> created = new java.util.LinkedHashMap<>();
        parent.put(key, created);
        return created;
    }

    private DumperOptions createDumperOptions() {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        return options;
    }

    private Path getBackupWorldRoot(String worldName) {
        return this.plugin.getServer().getWorldContainer().toPath()
                .resolve(this.settings.backupPath())
                .resolve(worldName);
    }

    private void rotateBackups(Path backupRoot, int generations) {
        deleteDirectoryIfExists(backupRoot.resolve(String.valueOf(generations)));
        for (int generation = generations - 1; generation >= 1; generation--) {
            Path source = backupRoot.resolve(String.valueOf(generation));
            if (!Files.exists(source)) {
                continue;
            }

            Path target = backupRoot.resolve(String.valueOf(generation + 1));
            deleteDirectoryIfExists(target);
            try {
                Files.createDirectories(target.getParent());
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to rotate resource world backups", exception);
            }
        }
    }

    private void copyDirectory(Path source, Path target) {
        deleteDirectoryIfExists(target);
        try {
            Files.walkFileTree(source, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    Files.createDirectories(target.resolve(source.relativize(dir)));
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.copy(
                            file,
                            target.resolve(source.relativize(file)),
                            StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.COPY_ATTRIBUTES);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to copy resource world directory", exception);
        }
    }

    private void deleteDirectoryIfExists(Path path) {
        if (!Files.exists(path)) {
            return;
        }

        try {
            Files.walkFileTree(path, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exception) throws IOException {
                    if (exception != null) {
                        throw exception;
                    }
                    Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to delete resource world directory " + path, exception);
        }
    }
}
