package com.gtnh.orescanner.vein;

import com.gtnh.orescanner.scanner.OreSnapshot;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class VeinTest {

    public static void main(String[] args) {
        System.out.println("Running Vein Module Verification Suite...");

        VeinTest suite = new VeinTest();
        suite.testCoordinateHelperPositive();
        suite.testCoordinateHelperNegative();
        suite.testCoordinateHelperEdgeCases();
        suite.testIsVeinCenter();
        suite.testChunkToBlockConversion();
        suite.testVeinDisambiguatorGrouping();
        suite.testVeinDisambiguatorCopper();
        suite.testVeinDisambiguatorCassiterite();
        suite.testVeinDisambiguatorYFiltering();
        suite.testVeinDisambiguatorSmallOreIgnored();
        suite.testVeinDisambiguatorReflectionFallback();

        System.out.println("ALL 11 TESTS PASSED SUCCESSFULLY!");
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("Test failed: " + message);
        }
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + " - Expected: " + expected + ", Got: " + actual);
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null && actual == null) return;
        if (expected == null || !expected.equals(actual)) {
            throw new AssertionError(message + " - Expected: " + expected + ", Got: " + actual);
        }
    }

    @Test
    public void testCoordinateHelperPositive() {
        // Chunks 0, 1, 2 -> 1
        assertEquals(1, VeinCoordinateHelper.getVeinCenterChunk(0), "Positive chunk 0");
        assertEquals(1, VeinCoordinateHelper.getVeinCenterChunk(1), "Positive chunk 1");
        assertEquals(1, VeinCoordinateHelper.getVeinCenterChunk(2), "Positive chunk 2");

        // Chunks 3, 4, 5 -> 4
        assertEquals(4, VeinCoordinateHelper.getVeinCenterChunk(3), "Positive chunk 3");
        assertEquals(4, VeinCoordinateHelper.getVeinCenterChunk(4), "Positive chunk 4");
        assertEquals(4, VeinCoordinateHelper.getVeinCenterChunk(5), "Positive chunk 5");

        // Chunks 6, 7, 8 -> 7
        assertEquals(7, VeinCoordinateHelper.getVeinCenterChunk(6), "Positive chunk 6");
        assertEquals(7, VeinCoordinateHelper.getVeinCenterChunk(7), "Positive chunk 7");
        assertEquals(7, VeinCoordinateHelper.getVeinCenterChunk(8), "Positive chunk 8");

        System.out.println("✓ testCoordinateHelperPositive passed");
    }

    @Test
    public void testCoordinateHelperNegative() {
        // Chunks -1, -2 -> -1
        assertEquals(-1, VeinCoordinateHelper.getVeinCenterChunk(-1), "Negative chunk -1");
        assertEquals(-1, VeinCoordinateHelper.getVeinCenterChunk(-2), "Negative chunk -2");

        // Chunks -3, -4, -5 -> -4
        assertEquals(-4, VeinCoordinateHelper.getVeinCenterChunk(-3), "Negative chunk -3");
        assertEquals(-4, VeinCoordinateHelper.getVeinCenterChunk(-4), "Negative chunk -4");
        assertEquals(-4, VeinCoordinateHelper.getVeinCenterChunk(-5), "Negative chunk -5");

        // Chunks -6, -7, -8 -> -7
        assertEquals(-7, VeinCoordinateHelper.getVeinCenterChunk(-6), "Negative chunk -6");
        assertEquals(-7, VeinCoordinateHelper.getVeinCenterChunk(-7), "Negative chunk -7");
        assertEquals(-7, VeinCoordinateHelper.getVeinCenterChunk(-8), "Negative chunk -8");

        System.out.println("✓ testCoordinateHelperNegative passed");
    }

    @Test
    public void testCoordinateHelperEdgeCases() {
        assertEquals(-10, VeinCoordinateHelper.getVeinCenterChunk(-9), "Negative chunk -9");
        assertEquals(-10, VeinCoordinateHelper.getVeinCenterChunk(-10), "Negative chunk -10");
        assertEquals(-10, VeinCoordinateHelper.getVeinCenterChunk(-11), "Negative chunk -11");

        assertEquals(10, VeinCoordinateHelper.getVeinCenterChunk(9), "Positive chunk 9");
        assertEquals(10, VeinCoordinateHelper.getVeinCenterChunk(10), "Positive chunk 10");
        assertEquals(10, VeinCoordinateHelper.getVeinCenterChunk(11), "Positive chunk 11");

        assertTrue(VeinCoordinateHelper.isChunkInVein(0, 0, 1, 1), "Chunk (0,0) in vein (1,1)");
        assertTrue(VeinCoordinateHelper.isChunkInVein(2, 2, 1, 1), "Chunk (2,2) in vein (1,1)");
        assertTrue(VeinCoordinateHelper.isChunkInVein(-3, -5, -4, -4), "Chunk (-3,-5) in vein (-4,-4)");

        System.out.println("✓ testCoordinateHelperEdgeCases passed");
    }

    @Test
    public void testIsVeinCenter() {
        assertTrue(VeinCoordinateHelper.isVeinCenter(1), "1 is center");
        assertTrue(VeinCoordinateHelper.isVeinCenter(4), "4 is center");
        assertTrue(VeinCoordinateHelper.isVeinCenter(7), "7 is center");
        assertTrue(VeinCoordinateHelper.isVeinCenter(-1), "-1 is center");
        assertTrue(VeinCoordinateHelper.isVeinCenter(-4), "-4 is center");
        assertTrue(VeinCoordinateHelper.isVeinCenter(-7), "-7 is center");

        assertTrue(!VeinCoordinateHelper.isVeinCenter(0), "0 is not center");
        assertTrue(!VeinCoordinateHelper.isVeinCenter(2), "2 is not center");
        assertTrue(!VeinCoordinateHelper.isVeinCenter(3), "3 is not center");
        assertTrue(!VeinCoordinateHelper.isVeinCenter(-2), "-2 is not center");
        assertTrue(!VeinCoordinateHelper.isVeinCenter(-3), "-3 is not center");

        assertTrue(VeinCoordinateHelper.isVeinCenter(1, 4), "(1, 4) is vein center");
        assertTrue(!VeinCoordinateHelper.isVeinCenter(1, 3), "(1, 3) is not vein center");

        System.out.println("✓ testIsVeinCenter passed");
    }

    @Test
    public void testChunkToBlockConversion() {
        assertEquals(24, VeinCoordinateHelper.chunkToCenterBlockCoord(1), "Chunk 1 center block is 24");
        assertEquals(-8, VeinCoordinateHelper.chunkToCenterBlockCoord(-1), "Chunk -1 center block is -8");
        assertEquals(1, VeinCoordinateHelper.blockToVeinCenterChunk(24), "Block 24 is in vein chunk 1");

        System.out.println("✓ testChunkToBlockConversion passed");
    }

    @Test
    public void testVeinDisambiguatorGrouping() {
        List<OreSnapshot> snapshots = new ArrayList<OreSnapshot>();
        // Vein 1: chunks (0,0), (1,1), (2,1) -> all center (1,1)
        snapshots.add(new OreSnapshot(0, 0, 5, 20, 5, 81, 0));
        snapshots.add(new OreSnapshot(1, 1, 20, 22, 20, 27, 0));
        snapshots.add(new OreSnapshot(2, 1, 35, 21, 22, 81, 0));

        // Vein 2: chunks (3,3), (4,5) -> all center (4,4)
        snapshots.add(new OreSnapshot(3, 3, 50, 60, 50, 24, 0));
        snapshots.add(new OreSnapshot(4, 5, 70, 62, 85, 83, 0));

        List<DisambiguatedVein> veins = VeinDisambiguator.disambiguate(snapshots);
        assertEquals(2, veins.size(), "Disambiguated two distinct vein clusters");

        System.out.println("✓ testVeinDisambiguatorGrouping passed");
    }

    @Test
    public void testVeinDisambiguatorCopper() {
        List<OreSnapshot> snapshots = new ArrayList<OreSnapshot>();
        // Copper Vein (ore.mix.copper): Chalcopyrite (27), Copper (81), Pyrite (60), Iron (32) at Y: 10-30
        for (int i = 0; i < 20; i++) {
            snapshots.add(new OreSnapshot(1, 1, 20 + i, 22, 20, 27, 0)); // Chalcopyrite
        }
        for (int i = 0; i < 30; i++) {
            snapshots.add(new OreSnapshot(1, 1, 20 + i, 20, 21, 81, 0)); // Copper
        }

        List<DisambiguatedVein> veins = VeinDisambiguator.disambiguate(snapshots);
        assertEquals(1, veins.size(), "Expected 1 vein match");

        DisambiguatedVein v = veins.get(0);
        assertEquals("ore.mix.copper", v.getVeinName(), "Expected copper vein");
        assertTrue(v.getConfidence() > 0.70, "Confidence should be high (>70%): was " + v.getConfidence());
        assertTrue(v.isConfident(), "Vein should be marked confident");
        assertEquals(1, v.getCenterChunkX(), "Center chunk X");
        assertEquals(1, v.getCenterChunkZ(), "Center chunk Z");

        System.out.println("✓ testVeinDisambiguatorCopper passed (confidence: " + v.getConfidencePercent() + "%)");
    }

    @Test
    public void testVeinDisambiguatorCassiterite() {
        List<OreSnapshot> snapshots = new ArrayList<OreSnapshot>();
        // Cassiterite Vein: Cassiterite (24), Tin (83), Cassiterite Sand (25) at Y: 40-120
        for (int i = 0; i < 25; i++) {
            snapshots.add(new OreSnapshot(4, 4, 68 + i, 65, 68, 24, 0));
        }
        for (int i = 0; i < 15; i++) {
            snapshots.add(new OreSnapshot(4, 4, 68 + i, 66, 69, 83, 0));
        }

        List<DisambiguatedVein> veins = VeinDisambiguator.disambiguate(snapshots);
        assertEquals(1, veins.size(), "Expected 1 vein match");

        DisambiguatedVein v = veins.get(0);
        assertTrue(v.getVeinName().contains("cassiterite") || v.getVeinName().contains("tin"),
                "Expected tin or cassiterite vein, got: " + v.getVeinName());
        assertTrue(v.getConfidence() > 0.60, "Confidence should be > 60%: was " + v.getConfidence());

        System.out.println("✓ testVeinDisambiguatorCassiterite passed (" + v.getVeinName() + " with " + v.getConfidencePercent() + "%)");
    }

    @Test
    public void testVeinDisambiguatorYFiltering() {
        // Test that high Y level penalizes copper vein (Y: 10-30) when found at Y=110
        List<OreSnapshot> snapshotsNormal = new ArrayList<OreSnapshot>();
        snapshotsNormal.add(new OreSnapshot(1, 1, 20, 20, 20, 27, 0)); // Chalcopyrite at Y=20

        List<OreSnapshot> snapshotsWrongY = new ArrayList<OreSnapshot>();
        snapshotsWrongY.add(new OreSnapshot(1, 1, 20, 110, 20, 27, 0)); // Chalcopyrite at Y=110

        DisambiguatedVein normal = VeinDisambiguator.disambiguate(snapshotsNormal, 0.05).get(0);
        DisambiguatedVein wrongY = VeinDisambiguator.disambiguate(snapshotsWrongY, 0.01).get(0);

        assertTrue(normal.getConfidence() > wrongY.getConfidence(),
                "Normal Y should have higher confidence (" + normal.getConfidence() + ") than wrong Y (" + wrongY.getConfidence() + ")");

        System.out.println("✓ testVeinDisambiguatorYFiltering passed");
    }

    @Test
    public void testVeinDisambiguatorSmallOreIgnored() {
        List<OreSnapshot> snapshots = new ArrayList<OreSnapshot>();
        // Small ore: meta >= 16000
        snapshots.add(new OreSnapshot(1, 1, 20, 20, 20, 16081, 0));
        snapshots.add(new OreSnapshot(1, 1, 21, 20, 20, 16027, 0));

        List<DisambiguatedVein> veins = VeinDisambiguator.disambiguate(snapshots);
        assertEquals(0, veins.size(), "Small ores must be filtered out, yielding 0 disambiguated veins");

        System.out.println("✓ testVeinDisambiguatorSmallOreIgnored passed");
    }

    @Test
    public void testVeinDisambiguatorReflectionFallback() {
        List<VeinDefinition> defs = VeinDisambiguator.getVeinDefinitions();
        assertTrue(!defs.isEmpty(), "Vein definitions must not be empty");
        assertTrue(defs.size() >= 20, "Should have loaded at least 20 fallback veins: was " + defs.size());

        System.out.println("✓ testVeinDisambiguatorReflectionFallback passed (loaded " + defs.size() + " veins)");
    }
}
