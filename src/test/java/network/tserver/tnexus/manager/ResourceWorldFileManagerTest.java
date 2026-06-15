package network.tserver.tnexus.manager;

import java.nio.file.Files;
import java.nio.file.Path;
import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.TestPluginSupport;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceWorldFileManagerTest {

    private ServerMock server;

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void shouldRotateBackupGenerations() throws Exception {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        TNexus plugin = TestPluginSupport.loadPlugin(this.server, TestPluginSupport.TestTNexus.class);
        ResourceWorldFileManager fileManager = new ResourceWorldFileManager(plugin);

        World world = this.server.addSimpleWorld("resource");
        Path worldFolder = world.getWorldFolder().toPath();
        Path marker = worldFolder.resolve("marker.txt");

        Files.writeString(marker, "one");
        fileManager.backupWorld("resource");

        Files.writeString(marker, "two");
        fileManager.backupWorld("resource");

        Files.writeString(marker, "three");
        fileManager.backupWorld("resource");

        Path backupRoot = this.server.getWorldContainer().toPath()
                .resolve("plugins")
                .resolve("T-Nexus")
                .resolve("backups")
                .resolve("resource");
        assertEquals("three", Files.readString(backupRoot.resolve("1").resolve("marker.txt")));
        assertEquals("two", Files.readString(backupRoot.resolve("2").resolve("marker.txt")));
        assertEquals("one", Files.readString(backupRoot.resolve("3").resolve("marker.txt")));
    }

    @Test
    void shouldResolveWorldFolderFromWorldContainerWhenWorldIsUnloaded() throws Exception {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        TNexus plugin = TestPluginSupport.loadPlugin(this.server, TestPluginSupport.TestTNexus.class);
        ResourceWorldFileManager fileManager = new ResourceWorldFileManager(plugin);
        Path worldFolder = this.server.getWorldContainer().toPath().resolve("resource");
        Files.createDirectories(worldFolder);
        Files.writeString(worldFolder.resolve("marker.txt"), "resource");

        assertEquals(worldFolder.toFile(), fileManager.getLoadedWorldFolder("resource"));
        fileManager.backupWorld("resource");
        assertTrue(Files.exists(this.server.getWorldContainer().toPath()
                .resolve("plugins")
                .resolve("T-Nexus")
                .resolve("backups")
                .resolve("resource")
                .resolve("1")
                .resolve("marker.txt")));
    }

    @Test
    void shouldRandomizeSpigotStructureSeedsPerWorld() throws Exception {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        TNexus plugin = TestPluginSupport.loadPlugin(this.server, TestPluginSupport.TestTNexus.class);
        ResourceWorldFileManager fileManager = new ResourceWorldFileManager(plugin);
        Path spigotConfigPath = this.server.getWorldContainer().toPath().resolve("spigot.yml");
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("world-settings.resource.seed-village", 1L);
        configuration.set("world-settings.resource.seed-desert", 2L);
        configuration.set("world-settings.other.seed-village", 3L);
        configuration.save(spigotConfigPath.toFile());

        fileManager.randomizeStructureSeeds("resource");

        YamlConfiguration updated = YamlConfiguration.loadConfiguration(spigotConfigPath.toFile());
        assertNotEquals(1L, updated.getLong("world-settings.resource.seed-village"));
        assertNotEquals(2L, updated.getLong("world-settings.resource.seed-desert"));
        assertEquals(3L, updated.getLong("world-settings.other.seed-village"));
        assertNotEquals(updated.getLong("world-settings.resource.seed-village"),
                updated.getLong("world-settings.resource.seed-desert"));
    }
}
