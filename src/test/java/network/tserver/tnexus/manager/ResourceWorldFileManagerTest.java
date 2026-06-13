package network.tserver.tnexus.manager;

import java.nio.file.Files;
import java.nio.file.Path;
import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.TestPluginSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

        Path worldFolder = this.server.getWorldContainer().toPath().resolve("resource");
        Files.createDirectories(worldFolder);
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
}
