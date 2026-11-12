package com.gtnh.orescanner.integration;

import com.gtnh.orescanner.config.OreScannerConfig;
import com.gtnh.orescanner.scanner.ChunkScanner;
import com.gtnh.orescanner.scanner.OreSnapshot;
import com.gtnh.orescanner.vein.DisambiguatedVein;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class IntegrationTest {

    public static void main(String[] args) {
        System.out.println("Running Integration Module Test Suite...");

        IntegrationTest suite = new IntegrationTest();
        suite.testAntiXrayConfigDisabledByDefault();
        suite.testAntiXrayDetectionWithNoOresAndStone();
        suite.testAntiXrayNotTriggeredWhenOresFound();
        suite.testAntiXrayNotTriggeredWhenNoStone();
        suite.testAntiXrayPrimitiveOverload();
        suite.testAntiXrayReportGeneration();
        suite.testVisualProspectingBridgeNullAndEmpty();
        suite.testVisualProspectingBridgeAvailabilitySafe();
        suite.testVisualProspectingBridgeScheduleFallback();

        System.out.println("ALL INTEGRATION TESTS PASSED SUCCESSFULLY!");
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("Test failed: " + message);
        }
    }

    private static void assertFalse(boolean condition, String message) {
        if (condition) {
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
    public void testAntiXrayConfigDisabledByDefault() {
        OreScannerConfig.checkAntiXray = false;

        ChunkScanner.ScanResult result = new ChunkScanner.ScanResult(
                Collections.<OreSnapshot>emptyList(),
                25,
                0,
                20,
                0
        );

        assertFalse(AntiXrayDetector.isAntiXraySuspected(result),
                "Anti-xray should be disabled by default via config");
    }

    @Test
    public void testAntiXrayDetectionWithNoOresAndStone() {
        OreScannerConfig.checkAntiXray = true;

        ChunkScanner.ScanResult result = new ChunkScanner.ScanResult(
                Collections.<OreSnapshot>emptyList(),
                25,
                0,
                15,
                0
        );

        assertTrue(AntiXrayDetector.isAntiXraySuspected(result),
                "Should detect anti-xray when subterranean stone is present with 0 ores");

        assertTrue(AntiXrayDetector.isAntiXraySuspected(result, true),
                "Should detect anti-xray when ignoreConfig is true");

        OreScannerConfig.checkAntiXray = false;
    }

    @Test
    public void testAntiXrayNotTriggeredWhenOresFound() {
        OreScannerConfig.checkAntiXray = true;

        List<OreSnapshot> snapshots = new ArrayList<OreSnapshot>();
        snapshots.add(new OreSnapshot(1, 1, 18, 30, 20, 27, 0)); // Copper ore

        ChunkScanner.ScanResult result = new ChunkScanner.ScanResult(
                snapshots,
                25,
                1,
                10,
                0
        );

        assertFalse(AntiXrayDetector.isAntiXraySuspected(result),
                "Should NOT detect anti-xray when ores are found");

        OreScannerConfig.checkAntiXray = false;
    }

    @Test
    public void testAntiXrayNotTriggeredWhenNoStone() {
        OreScannerConfig.checkAntiXray = true;

        ChunkScanner.ScanResult result = new ChunkScanner.ScanResult(
                Collections.<OreSnapshot>emptyList(),
                25,
                0,
                0, // 0 chunks with subterranean stone (e.g. void world)
                0
        );

        assertFalse(AntiXrayDetector.isAntiXraySuspected(result),
                "Should NOT detect anti-xray when no subterranean stone exists in scanned chunks");

        OreScannerConfig.checkAntiXray = false;
    }

    @Test
    public void testAntiXrayPrimitiveOverload() {
        OreScannerConfig.checkAntiXray = true;
        assertTrue(AntiXrayDetector.isAntiXraySuspected(10, 0, 5), "Primitive: stone with 0 ores");
        assertFalse(AntiXrayDetector.isAntiXraySuspected(10, 5, 5), "Primitive: ores present");
        assertFalse(AntiXrayDetector.isAntiXraySuspected(10, 0, 0), "Primitive: no stone");

        OreScannerConfig.checkAntiXray = false;
        assertFalse(AntiXrayDetector.isAntiXraySuspected(10, 0, 5), "Primitive: config disabled");
    }

    @Test
    public void testAntiXrayReportGeneration() {
        ChunkScanner.ScanResult result = new ChunkScanner.ScanResult(
                Collections.<OreSnapshot>emptyList(),
                20,
                0,
                10,
                0
        );

        AntiXrayDetector.AntiXrayReport report = AntiXrayDetector.generateReport(result);
        assertEquals(20, report.getTotalChunks(), "Report total chunks");
        assertEquals(0, report.getOreCount(), "Report ore count");
        assertEquals(10, report.getStoneNoOreChunks(), "Report stone chunks");
        assertTrue(report.isAntiXraySuspected(), "Report suspected flag");
        assertEquals(0.50, report.getConfidence(), "Report confidence (10/20)");
        assertEquals(AntiXrayDetector.WARNING_MESSAGE, report.getMessage(), "Report warning message");
    }

    @Test
    public void testVisualProspectingBridgeNullAndEmpty() {
        assertEquals(0, VisualProspectingBridge.injectVeins(null), "Null veins injection");
        assertEquals(0, VisualProspectingBridge.injectVeins(Collections.<DisambiguatedVein>emptyList()), "Empty veins injection");
        assertFalse(VisualProspectingBridge.injectVein(null), "Null single vein injection");
    }

    @Test
    public void testVisualProspectingBridgeAvailabilitySafe() {
        // Must execute cleanly without throwing NoClassDefFoundError or ClassNotFoundException
        boolean available = VisualProspectingBridge.isVisualProspectingAvailable();
        System.out.println("VisualProspecting runtime availability: " + available);
    }

    @Test
    public void testVisualProspectingBridgeScheduleFallback() {
        final AtomicBoolean executed = new AtomicBoolean(false);
        VisualProspectingBridge.scheduleOnClientThread(new Runnable() {
            @Override
            public void run() {
                executed.set(true);
            }
        });

        assertTrue(executed.get(), "scheduleOnClientThread should execute task via fallback in offline/test harness");
    }
}
