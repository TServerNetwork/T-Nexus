package network.tserver.tnexus.manager;

import network.tserver.tnexus.TestPluginSupport;
import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FaweResourceWorldEditServiceTest {

    private ServerMock server;

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void shouldResolveNamespacedMarkerMaterial() {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        assertEquals(
                Material.MAGENTA_CONCRETE,
                FaweResourceWorldEditService.resolveConfiguredBlockMaterial("minecraft:magenta_concrete"));
    }

    @Test
    void shouldResolveLegacyStyleMarkerMaterialName() {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        assertEquals(
                Material.MAGENTA_CONCRETE,
                FaweResourceWorldEditService.resolveConfiguredBlockMaterial("MAGENTA_CONCRETE"));
    }

    @Test
    void shouldReturnNullForUnknownMarkerMaterial() {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        assertNull(FaweResourceWorldEditService.resolveConfiguredBlockMaterial("minecraft:not_a_real_block"));
    }
}
