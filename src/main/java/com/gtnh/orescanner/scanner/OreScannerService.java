package com.gtnh.orescanner.scanner;

import com.gtnh.orescanner.GTNHOreScannerMod;
import com.gtnh.orescanner.config.OreScannerConfig;
import com.gtnh.orescanner.integration.AntiXrayDetector;
import com.gtnh.orescanner.integration.VisualProspectingBridge;
import com.gtnh.orescanner.vein.DisambiguatedVein;
import com.gtnh.orescanner.vein.VeinDisambiguator;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Service orchestrating client ore scanning, background vein disambiguation,
 * VisualProspecting cache injection, audio feedback, and client chat reporting.
 */
@SideOnly(Side.CLIENT)
public class OreScannerService {

    private static final ExecutorService SCAN_EXECUTOR = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "GTNH-OreScanner-Worker");
            t.setDaemon(true);
            return t;
        }
    });

    private static final AtomicBoolean isScanning = new AtomicBoolean(false);

    /**
     * Executes an ore scan cycle.
     * Must be triggered from the client thread (e.g. KeyInputHandler).
     */
    public static void executeScan() {
        final Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.theWorld == null || mc.thePlayer == null) {
            return;
        }

        // Debounce / avoid concurrent scanning passes
        if (!isScanning.compareAndSet(false, true)) {
            sendClientChat(mc, EnumChatFormatting.YELLOW + "[Ore Scanner] " +
                    EnumChatFormatting.GRAY + "A scan is already in progress...");
            return;
        }

        try {
            // 1. Thread-safe snapshot phase on the client thread
            final ChunkScanner.ScanResult scanResult = ChunkScanner.scanAroundPlayer();

            // 2. Offload vein disambiguation and database injection to background thread
            SCAN_EXECUTOR.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        processScanInBackground(mc, scanResult);
                    } catch (Throwable t) {
                        GTNHOreScannerMod.LOG.error("Failed to process ore scan in background", t);
                        isScanning.set(false);
                    }
                }
            });
        } catch (Throwable t) {
            GTNHOreScannerMod.LOG.error("Error initiating ore scan snapshot", t);
            isScanning.set(false);
        }
    }

    /**
     * Background worker task: performs vein disambiguation, anti-xray detection,
     * VisualProspecting registration, and schedules client effects.
     */
    private static void processScanInBackground(final Minecraft mc, final ChunkScanner.ScanResult scanResult) {
        try {
            // 1. Check for server-side anti-xray / Orebfuscator obfuscation (if enabled in config)
            final boolean antiXraySuspected = OreScannerConfig.checkAntiXray && AntiXrayDetector.isAntiXraySuspected(scanResult);

            // 2. Disambiguate ore veins if any ores were detected
            List<DisambiguatedVein> resolvedVeins = Collections.emptyList();
            int injectedCount = 0;

            if (scanResult.hasOres()) {
                resolvedVeins = VeinDisambiguator.disambiguate(scanResult.getSnapshots());

                // 3. Register veins into VisualProspecting cache
                if (!resolvedVeins.isEmpty()) {
                    injectedCount = VisualProspectingBridge.injectVeins(resolvedVeins);
                }
            }

            final List<DisambiguatedVein> finalResolvedVeins = resolvedVeins;
            final int finalInjectedCount = injectedCount;

            // 4. Dispatch audio feedback and chat output back to client thread
            scheduleOnClientThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        EntityClientPlayerMP player = mc.thePlayer;
                        if (player == null) {
                            return;
                        }

                        // Play audio feedback if configured
                        if (OreScannerConfig.soundFeedback) {
                            player.playSound("random.orb", 0.5F, 1.0F);
                        }

                        // Anti-Xray / Orebfuscator notice
                        if (antiXraySuspected) {
                            player.addChatMessage(new ChatComponentText(
                                    EnumChatFormatting.YELLOW + AntiXrayDetector.getWarningMessage()
                            ));
                        } else if (scanResult.hasOres()) {
                            // Scan summary
                            int oreCount = scanResult.getSnapshots().size();
                            int veinCount = finalResolvedVeins.size();
                            int chunkCount = scanResult.getTotalLoadedChunks();

                            ChatComponentText summary = new ChatComponentText(
                                    EnumChatFormatting.GOLD + "[Ore Scanner] " +
                                    EnumChatFormatting.GREEN + "Scan complete! " +
                                    EnumChatFormatting.GRAY + "Found " +
                                    EnumChatFormatting.WHITE + oreCount +
                                    EnumChatFormatting.GRAY + " ores across " +
                                    EnumChatFormatting.WHITE + chunkCount +
                                    EnumChatFormatting.GRAY + " chunks (" +
                                    EnumChatFormatting.AQUA + veinCount +
                                    EnumChatFormatting.GRAY + " veins mapped, " +
                                    EnumChatFormatting.GREEN + finalInjectedCount +
                                    EnumChatFormatting.GRAY + " registered to map)."
                            );
                            player.addChatMessage(summary);
                        } else {
                            // No ores found
                            int chunkCount = scanResult.getTotalLoadedChunks();
                            ChatComponentText emptyNotice = new ChatComponentText(
                                    EnumChatFormatting.GOLD + "[Ore Scanner] " +
                                    EnumChatFormatting.GRAY + "Scanned " +
                                    EnumChatFormatting.WHITE + chunkCount +
                                    EnumChatFormatting.GRAY + " chunks. No ores detected."
                            );
                            player.addChatMessage(emptyNotice);
                        }
                    } finally {
                        isScanning.set(false);
                    }
                }
            });
        } catch (Throwable t) {
            GTNHOreScannerMod.LOG.error("Unexpected error in background ore scan processing", t);
            isScanning.set(false);
        }
    }

    /**
     * Dispatches a task safely to the client main thread.
     */
    public static void scheduleOnClientThread(final Runnable runnable) {
        if (runnable == null) {
            return;
        }

        try {
            final Minecraft mc = Minecraft.getMinecraft();
            if (mc != null) {
                try {
                    mc.func_152344_a(runnable);
                    return;
                } catch (Throwable ignored) {
                }

                try {
                    Method method = mc.getClass().getMethod("addScheduledTask", Runnable.class);
                    method.invoke(mc, runnable);
                    return;
                } catch (Throwable ignored) {
                }

                try {
                    Method method = mc.getClass().getMethod("func_152344_a", FutureTask.class);
                    method.invoke(mc, new FutureTask<Void>(runnable, null));
                    return;
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
            // Headless / test / offline harness fallback
        }

        // Direct execution fallback if scheduler is not accessible
        runnable.run();
    }

    private static void sendClientChat(Minecraft mc, String message) {
        if (mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(new ChatComponentText(message));
        }
    }

    public static boolean isScanInProgress() {
        return isScanning.get();
    }

    /**
     * Shuts down the background scanning executor.
     */
    public static void shutdown() {
        SCAN_EXECUTOR.shutdownNow();
    }
}
