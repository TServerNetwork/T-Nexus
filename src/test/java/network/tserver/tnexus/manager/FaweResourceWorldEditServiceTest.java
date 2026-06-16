package network.tserver.tnexus.manager;

import network.tserver.tnexus.TestPluginSupport;
import org.bukkit.Material;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void shouldTreatWaterAsLiquidSurfaceOnlyWhenAllowed() {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        World world = this.server.addSimpleWorld("resource");
        world.getBlockAt(0, 62, 0).setType(Material.STONE);
        world.getBlockAt(0, 63, 0).setType(Material.WATER);

        assertEquals(62, FaweResourceWorldEditService.sampleTerrainSurfaceY(world, 0, 0, world.getMinHeight() + 1, 100));
        assertEquals(
                63,
                FaweResourceWorldEditService.sampleTerrainSurfaceY(
                        world,
                        0,
                        0,
                        world.getMinHeight() + 1,
                        100,
                        true));
    }

    @Test
    void shouldIgnoreLeavesAndLogsWhenSamplingTerrainSurface() {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        World world = this.server.addSimpleWorld("resource");
        world.getBlockAt(0, 60, 0).setType(Material.DIRT);
        world.getBlockAt(0, 61, 0).setType(Material.OAK_LOG);
        world.getBlockAt(0, 62, 0).setType(Material.OAK_LEAVES);

        assertEquals(60, FaweResourceWorldEditService.sampleTerrainSurfaceY(world, 0, 0, world.getMinHeight() + 1, 100));
    }

    @Test
    void shouldExposePerimeterColumnsWithoutCenterDuplicates() {
        assertEquals(8, FaweResourceWorldEditService.perimeterColumns(1).size());
        assertFalse(FaweResourceWorldEditService.perimeterColumns(1).contains(new FaweResourceWorldEditService.ColumnKey(0, 0)));
        assertTrue(FaweResourceWorldEditService.perimeterColumns(2).contains(new FaweResourceWorldEditService.ColumnKey(2, 0)));
        assertTrue(FaweResourceWorldEditService.perimeterColumns(2).contains(new FaweResourceWorldEditService.ColumnKey(0, -2)));
    }

    @Test
    void shouldNotRaiseLiquidAboveItsOriginalTopWhenRestoringColumns() {
        assertEquals(74, FaweResourceWorldEditService.resolveRestoredLiquidTopY(63, 74, 319));
        assertEquals(90, FaweResourceWorldEditService.resolveRestoredLiquidTopY(90, 74, 319));
        assertEquals(319, FaweResourceWorldEditService.resolveRestoredLiquidTopY(400, 74, 319));
    }
}
