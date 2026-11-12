package com.gtnh.orescanner.integration;

import com.gtnh.orescanner.config.OreScannerConfig;
import com.gtnh.orescanner.scanner.ChunkScanner;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.chunk.Chunk;

/**
 * Analyzes subterranean scan results in loaded chunks (e.g. subterranean levels with stone
 * but zero ores) to flag potential server-side anti-xray / Orebfuscator obfuscation.
 *
 * Anti-xray warnings are disabled by default via OreScannerConfig.checkAntiXray to prevent
 * intrusive chat notifications for players on servers without anti-xray.
 */
@SideOnly(Side.CLIENT)
public class AntiXrayDetector {

    public static final String WARNING_MESSAGE =
            "[Ore Scanner] No ores visible in loaded chunks. Server anti-xray may be obfuscating underground blocks.";

    private static long lastNotificationTime = 0L;
    private static final long NOTIFICATION_COOLDOWN_MS = 10000L;

    /**
     * Checks if server-side anti-xray obfuscation is suspected based on chunk scan results.
     * Respects OreScannerConfig.checkAntiXray (disabled by default).
     *
     * @param result ChunkScanner.ScanResult to analyze
     * @return true if anti-xray is enabled in config and results indicate obfuscation
     */
    public static boolean isAntiXraySuspected(ChunkScanner.ScanResult result) {
        if (!OreScannerConfig.checkAntiXray) {
            return false;
        }
        return isAntiXraySuspected(result, false);
    }

    /**
     * Checks if anti-xray obfuscation is suspected, with optional bypass of the config check.
     *
     * @param result ChunkScanner.ScanResult to analyze
     * @param ignoreConfig If true, ignores OreScannerConfig.checkAntiXray and performs pure data analysis
     * @return true if loaded chunks have subterranean stone but zero ores were found
     */
    public static boolean isAntiXraySuspected(ChunkScanner.ScanResult result, boolean ignoreConfig) {
        if (!ignoreConfig && !OreScannerConfig.checkAntiXray) {
            return false;
        }
        if (result == null) {
            return false;
        }

        return result.getTotalLoadedChunks() > 0 &&
                !result.hasOres() &&
                result.getChunksWithSubterraneanStoneNoOres() > 0;
    }

    /**
     * Primitive overload for checking anti-xray obfuscation suspicion.
     *
     * @param totalLoadedChunks Total count of chunks scanned
     * @param oreCount Total number of ores found
     * @param chunksWithSubterraneanStoneNoOres Chunks with deep stone but 0 ores
     * @return true if conditions indicate anti-xray obfuscation
     */
    public static boolean isAntiXraySuspected(int totalLoadedChunks, int oreCount, int chunksWithSubterraneanStoneNoOres) {
        if (!OreScannerConfig.checkAntiXray) {
            return false;
        }
        return totalLoadedChunks > 0 && oreCount == 0 && chunksWithSubterraneanStoneNoOres > 0;
    }

    /**
     * Returns the standardized anti-xray warning message string.
     */
    public static String getWarningMessage() {
        return WARNING_MESSAGE;
    }

    /**
     * Inspects a single chunk's subterranean layers (Y = 12, 20, 30, 40, 50)
     * to determine if solid stone/rock is present.
     *
     * @param chunk The loaded Minecraft chunk
     * @return true if subterranean stone/rock layers exist
     */
    public static boolean hasSubterraneanStone(Chunk chunk) {
        if (chunk == null) {
            return false;
        }
        try {
            int[] testY = new int[]{12, 20, 30, 40, 50};
            for (int y : testY) {
                Block block = chunk.getBlock(8, y, 8);
                if (block != null && block != Blocks.air) {
                    if (block == Blocks.stone || block == Blocks.netherrack || block == Blocks.end_stone
                            || block.getMaterial() == Material.rock) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    /**
     * Checks scan results and posts a formatted notification to the local player's chat
     * if anti-xray is suspected, subject to debounce cooldowns and config settings.
     *
     * @param result ChunkScanner.ScanResult to check
     */
    public static void checkAndNotify(final ChunkScanner.ScanResult result) {
        if (!isAntiXraySuspected(result)) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastNotificationTime < NOTIFICATION_COOLDOWN_MS) {
            return;
        }
        lastNotificationTime = now;

        VisualProspectingBridge.scheduleOnClientThread(new Runnable() {
            @Override
            public void run() {
                Minecraft mc = Minecraft.getMinecraft();
                if (mc != null && mc.thePlayer != null) {
                    ChatComponentText msg = new ChatComponentText(
                            EnumChatFormatting.GOLD + "[Ore Scanner] " +
                            EnumChatFormatting.YELLOW + "No ores visible in loaded chunks. Server anti-xray may be obfuscating underground blocks."
                    );
                    mc.thePlayer.addChatMessage(msg);
                }
            }
        });
    }

    /**
     * Generates a diagnostic report containing obfuscation metrics and confidence.
     *
     * @param result The ChunkScanner.ScanResult to evaluate
     * @return Detailed AntiXrayReport
     */
    public static AntiXrayReport generateReport(ChunkScanner.ScanResult result) {
        if (result == null) {
            return new AntiXrayReport(0, 0, 0, false, 0.0, "No scan result provided.");
        }

        int totalChunks = result.getTotalLoadedChunks();
        int oreCount = result.getSnapshots().size();
        int stoneChunks = result.getChunksWithSubterraneanStoneNoOres();
        boolean suspected = isAntiXraySuspected(result, true);

        double confidence = 0.0;
        if (totalChunks > 0 && oreCount == 0 && stoneChunks > 0) {
            confidence = Math.min(1.0, (double) stoneChunks / totalChunks);
        }

        String msg = suspected ? WARNING_MESSAGE : "Normal chunk state (no obfuscation detected).";
        return new AntiXrayReport(totalChunks, oreCount, stoneChunks, suspected, confidence, msg);
    }

    /**
     * Diagnostic report holding scan details and anti-xray suspicion metrics.
     */
    public static final class AntiXrayReport {
        private final int totalChunks;
        private final int oreCount;
        private final int stoneNoOreChunks;
        private final boolean antiXraySuspected;
        private final double confidence;
        private final String message;

        public AntiXrayReport(int totalChunks, int oreCount, int stoneNoOreChunks,
                              boolean antiXraySuspected, double confidence, String message) {
            this.totalChunks = totalChunks;
            this.oreCount = oreCount;
            this.stoneNoOreChunks = stoneNoOreChunks;
            this.antiXraySuspected = antiXraySuspected;
            this.confidence = confidence;
            this.message = message;
        }

        public int getTotalChunks() { return totalChunks; }
        public int getOreCount() { return oreCount; }
        public int getStoneNoOreChunks() { return stoneNoOreChunks; }
        public boolean isAntiXraySuspected() { return antiXraySuspected; }
        public double getConfidence() { return confidence; }
        public String getMessage() { return message; }

        @Override
        public String toString() {
            return "AntiXrayReport{" +
                    "totalChunks=" + totalChunks +
                    ", oreCount=" + oreCount +
                    ", stoneNoOreChunks=" + stoneNoOreChunks +
                    ", suspected=" + antiXraySuspected +
                    ", confidence=" + String.format("%.1f%%", confidence * 100.0) +
                    '}';
        }
    }
}
