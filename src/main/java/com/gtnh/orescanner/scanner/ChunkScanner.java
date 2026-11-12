package com.gtnh.orescanner.scanner;

import com.gtnh.orescanner.config.OreScannerConfig;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.chunk.Chunk;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Scans loaded client chunks safely on the Minecraft client thread
 * and extracts OreSnapshots of GT ore blocks.
 */
@SideOnly(Side.CLIENT)
public class ChunkScanner {

    private static Class<?> gtTileEntityOresClass = null;
    private static Field metaDataField = null;
    private static boolean reflectionChecked = false;

    /**
     * Scans loaded chunks around the local player and returns a ScanResult.
     * Must be invoked on the Minecraft client thread.
     */
    public static ScanResult scanAroundPlayer() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) {
            return new ScanResult(Collections.<OreSnapshot>emptyList(), 0, 0, 0, 0);
        }

        int renderDist = mc.gameSettings != null ? mc.gameSettings.renderDistanceChunks : 8;
        int configRadius = OreScannerConfig.scanRadiusChunks;
        int effectiveRadius = Math.min(configRadius, renderDist > 0 ? renderDist : configRadius);
        if (effectiveRadius <= 0) {
            effectiveRadius = 1;
        }

        return scan(mc.theWorld, mc.thePlayer.chunkCoordX, mc.thePlayer.chunkCoordZ, effectiveRadius);
    }

    /**
     * Scans loaded chunks within radius of (centerChunkX, centerChunkZ) in the given world.
     */
    public static ScanResult scan(WorldClient world, int centerChunkX, int centerChunkZ, int radius) {
        if (world == null) {
            return new ScanResult(Collections.<OreSnapshot>emptyList(), 0, 0, 0, 0);
        }

        int dimensionId = world.provider != null ? world.provider.dimensionId : 0;
        List<OreSnapshot> snapshots = new ArrayList<OreSnapshot>();
        int totalLoadedChunks = 0;
        int chunksWithOres = 0;
        int chunksWithSubterraneanStoneNoOres = 0;

        for (int cx = centerChunkX - radius; cx <= centerChunkX + radius; cx++) {
            for (int cz = centerChunkZ - radius; cz <= centerChunkZ + radius; cz++) {
                if (world.getChunkProvider() == null || !world.getChunkProvider().chunkExists(cx, cz)) {
                    continue;
                }

                Chunk chunk = world.getChunkFromChunkCoords(cx, cz);
                if (chunk == null || chunk.isEmpty()) {
                    continue;
                }

                totalLoadedChunks++;

                // Safely copy tile entities to avoid ConcurrentModificationException
                List<TileEntity> tileEntities = extractTileEntitiesSafely(chunk);
                int oresFoundInChunk = 0;

                for (TileEntity te : tileEntities) {
                    if (te == null) continue;

                    if (isGTOreTileEntity(te)) {
                        short meta = getGTOreMeta(te);
                        // Filter out invalid metas and small ores (meta >= 16000)
                        if (meta >= 0 && meta < 16000) {
                            snapshots.add(new OreSnapshot(
                                    chunk.xPosition,
                                    chunk.zPosition,
                                    te.xCoord,
                                    te.yCoord,
                                    te.zCoord,
                                    meta,
                                    dimensionId
                            ));
                            oresFoundInChunk++;
                        }
                    }
                }

                if (oresFoundInChunk > 0) {
                    chunksWithOres++;
                } else {
                    if (hasSubterraneanStone(chunk)) {
                        chunksWithSubterraneanStoneNoOres++;
                    }
                }
            }
        }

        return new ScanResult(snapshots, totalLoadedChunks, chunksWithOres, chunksWithSubterraneanStoneNoOres, dimensionId);
    }

    /**
     * Thread-safely extracts tile entity references from a chunk's chunkTileEntityMap.
     */
    @SuppressWarnings("unchecked")
    private static List<TileEntity> extractTileEntitiesSafely(Chunk chunk) {
        List<TileEntity> list = new ArrayList<TileEntity>();
        if (chunk == null || chunk.chunkTileEntityMap == null) {
            return list;
        }

        try {
            synchronized (chunk.chunkTileEntityMap) {
                for (Object obj : chunk.chunkTileEntityMap.values()) {
                    if (obj instanceof TileEntity) {
                        list.add((TileEntity) obj);
                    }
                }
            }
        } catch (Throwable t) {
            // Fallback in case chunkTileEntityMap is modified during copy
            try {
                Object[] array = chunk.chunkTileEntityMap.values().toArray();
                for (Object obj : array) {
                    if (obj instanceof TileEntity) {
                        list.add((TileEntity) obj);
                    }
                }
            } catch (Throwable ignored) {}
        }

        return list;
    }

    /**
     * Checks if a subterranean layer in the chunk contains stone or solid subterranean blocks.
     * Used to detect whether server anti-xray might be hiding ores in subterranean terrain.
     */
    private static boolean hasSubterraneanStone(Chunk chunk) {
        try {
            // Sample subterranean Y coordinates (e.g., Y=15, 25, 35, 45, 55)
            int[] testY = new int[]{15, 25, 35, 45, 55};
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
     * Checks whether the tile entity is an instance of GregTech's TileEntityOres.
     */
    public static boolean isGTOreTileEntity(TileEntity te) {
        if (te == null) return false;

        // Direct class check if class is loaded
        if (te instanceof gregtech.common.blocks.TileEntityOres) {
            return true;
        }

        // Reflection check for safety if loaded under different class loader or subclass
        Class<?> clazz = te.getClass();
        while (clazz != null && clazz != Object.class) {
            String name = clazz.getName();
            if ("gregtech.common.blocks.TileEntityOres".equals(name) ||
                "gregtech.common.blocks.GT_TileEntity_Ores".equals(name)) {
                return true;
            }
            clazz = clazz.getSuperclass();
        }
        return false;
    }

    /**
     * Extracts mMetaData from TileEntityOres.
     */
    public static short getGTOreMeta(TileEntity te) {
        if (te instanceof gregtech.common.blocks.TileEntityOres) {
            return ((gregtech.common.blocks.TileEntityOres) te).mMetaData;
        }

        initReflection();
        if (metaDataField != null) {
            try {
                return metaDataField.getShort(te);
            } catch (Throwable ignored) {}
        }

        try {
            Field f = te.getClass().getField("mMetaData");
            return f.getShort(te);
        } catch (Throwable t) {
            try {
                Field f = te.getClass().getDeclaredField("mMetaData");
                f.setAccessible(true);
                return f.getShort(te);
            } catch (Throwable ignored) {
                return -1;
            }
        }
    }

    private static synchronized void initReflection() {
        if (reflectionChecked) return;
        reflectionChecked = true;
        String[] candidateClasses = new String[] {
                "gregtech.common.blocks.TileEntityOres",
                "gregtech.common.blocks.GT_TileEntity_Ores"
        };
        for (String clsName : candidateClasses) {
            try {
                gtTileEntityOresClass = Class.forName(clsName);
                if (gtTileEntityOresClass != null) break;
            } catch (Throwable ignored) {}
        }
        if (gtTileEntityOresClass != null) {
            try {
                metaDataField = gtTileEntityOresClass.getField("mMetaData");
            } catch (NoSuchFieldException e) {
                try {
                    metaDataField = gtTileEntityOresClass.getDeclaredField("mMetaData");
                    metaDataField.setAccessible(true);
                } catch (Throwable ignored) {}
            }
        }
    }

    /**
     * Encapsulates the results of a client chunk scan.
     */
    public static final class ScanResult {
        private final List<OreSnapshot> snapshots;
        private final int totalLoadedChunks;
        private final int chunksWithOres;
        private final int chunksWithSubterraneanStoneNoOres;
        private final int dimensionId;

        public ScanResult(List<OreSnapshot> snapshots, int totalLoadedChunks, int chunksWithOres,
                          int chunksWithSubterraneanStoneNoOres, int dimensionId) {
            this.snapshots = snapshots != null ? Collections.unmodifiableList(snapshots) : Collections.<OreSnapshot>emptyList();
            this.totalLoadedChunks = totalLoadedChunks;
            this.chunksWithOres = chunksWithOres;
            this.chunksWithSubterraneanStoneNoOres = chunksWithSubterraneanStoneNoOres;
            this.dimensionId = dimensionId;
        }

        public List<OreSnapshot> getSnapshots() {
            return snapshots;
        }

        public int getTotalLoadedChunks() {
            return totalLoadedChunks;
        }

        public int getChunksWithOres() {
            return chunksWithOres;
        }

        public int getChunksWithSubterraneanStoneNoOres() {
            return chunksWithSubterraneanStoneNoOres;
        }

        public int getDimensionId() {
            return dimensionId;
        }

        public boolean hasOres() {
            return !snapshots.isEmpty();
        }

        /**
         * Returns true if loaded chunks have subterranean stone but zero ores were found,
         * indicating potential server-side anti-xray or Orebfuscator obfuscation.
         */
        public boolean isPotentialAntiXray() {
            return snapshots.isEmpty() && chunksWithSubterraneanStoneNoOres > 0;
        }
    }
}
