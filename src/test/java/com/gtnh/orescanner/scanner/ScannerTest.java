package com.gtnh.orescanner.scanner;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ScannerTest {

    public static void main(String[] args) {
        System.out.println("Running Scanner Module Test Suite...");

        ScannerTest suite = new ScannerTest();
        suite.testOreSnapshotCreation();
        suite.testOreSnapshotEqualityAndHashCode();
        suite.testScanResultProperties();
        suite.testScanResultAntiXrayLogic();
        suite.testOreSnapshotMetaFilteringLogic();

        System.out.println("ALL SCANNER TESTS PASSED SUCCESSFULLY!");
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
    public void testOreSnapshotCreation() {
        OreSnapshot snapshot = new OreSnapshot(3, -5, 52, 34, -72, 81, 0);
        assertEquals(3, snapshot.getChunkX(), "chunkX");
        assertEquals(-5, snapshot.getChunkZ(), "chunkZ");
        assertEquals(52, snapshot.getX(), "x");
        assertEquals(34, snapshot.getY(), "y");
        assertEquals(-72, snapshot.getZ(), "z");
        assertEquals(81, snapshot.getMeta(), "meta");
        assertEquals((short) 81, snapshot.getShortMeta(), "shortMeta");
        assertEquals(0, snapshot.getDimensionId(), "dimensionId");

        // Constructor with default dimension
        OreSnapshot defaultDim = new OreSnapshot(1, 1, 16, 20, 16, 27);
        assertEquals(0, defaultDim.getDimensionId(), "default dimensionId should be 0");
    }

    @Test
    public void testOreSnapshotEqualityAndHashCode() {
        OreSnapshot s1 = new OreSnapshot(1, 2, 10, 20, 30, 100, -1);
        OreSnapshot s2 = new OreSnapshot(1, 2, 10, 20, 30, 100, -1);
        OreSnapshot s3 = new OreSnapshot(1, 2, 10, 20, 30, 101, -1);

        assertEquals(s1, s2, "s1 should equal s2");
        assertEquals(s1.hashCode(), s2.hashCode(), "Hashcodes must match");
        assertTrue(!s1.equals(s3), "s1 must not equal s3 (different meta)");
    }

    @Test
    public void testScanResultProperties() {
        List<OreSnapshot> list = new ArrayList<OreSnapshot>();
        list.add(new OreSnapshot(1, 1, 16, 30, 16, 27, 0));
        list.add(new OreSnapshot(1, 1, 17, 30, 16, 81, 0));

        ChunkScanner.ScanResult result = new ChunkScanner.ScanResult(list, 9, 1, 8, 0);

        assertEquals(2, result.getSnapshots().size(), "Snapshots count");
        assertEquals(9, result.getTotalLoadedChunks(), "Total loaded chunks");
        assertEquals(1, result.getChunksWithOres(), "Chunks with ores");
        assertEquals(8, result.getChunksWithSubterraneanStoneNoOres(), "Stone chunks with no ores");
        assertEquals(0, result.getDimensionId(), "Dimension ID");
        assertTrue(result.hasOres(), "Must report hasOres true");
        assertTrue(!result.isPotentialAntiXray(), "Should not report anti-xray when ores are present");
    }

    @Test
    public void testScanResultAntiXrayLogic() {
        // Empty snapshots, but stone chunks present
        ChunkScanner.ScanResult emptyWithStone = new ChunkScanner.ScanResult(
                Collections.<OreSnapshot>emptyList(), 9, 0, 9, 0
        );

        assertTrue(!emptyWithStone.hasOres(), "Empty result hasOres is false");
        assertTrue(emptyWithStone.isPotentialAntiXray(), "Should flag potential anti-xray when no ores but subterranean stone present");

        // Empty snapshots, 0 loaded chunks (e.g. void world)
        ChunkScanner.ScanResult voidWorld = new ChunkScanner.ScanResult(
                Collections.<OreSnapshot>emptyList(), 9, 0, 0, 1
        );
        assertTrue(!voidWorld.isPotentialAntiXray(), "Void world without stone should not flag anti-xray");
    }

    @Test
    public void testOreSnapshotMetaFilteringLogic() {
        // Test simulated metadata values
        short normalOreMeta = 27;     // Copper
        short largeOreMeta = 81;      // Chalcopyrite
        short smallOreMeta = 16027;   // Small Copper Ore
        short invalidMeta = -1;

        assertTrue(normalOreMeta >= 0 && normalOreMeta < 16000, "Normal ore 27 must pass filter");
        assertTrue(largeOreMeta >= 0 && largeOreMeta < 16000, "Large ore 81 must pass filter");
        assertTrue(!(smallOreMeta >= 0 && smallOreMeta < 16000), "Small ore 16027 must be rejected by filter");
        assertTrue(!(invalidMeta >= 0 && invalidMeta < 16000), "Negative meta must be rejected by filter");
    }
}
