package com.gtnh.orescanner.vein;

/**
 * Coordinate utility for GregTech 5 Unofficial ore vein generation grid.
 *
 * In GT5U, ore veins generate in 3x3 chunk areas centered at chunks where:
 *   Math.abs(chunkX) % 3 == 1 && Math.abs(chunkZ) % 3 == 1
 *
 * Across the origin (chunk 0), chunk coordinates follow the symmetrical normalization:
 *   chunkCoord >= 0 ? (chunkCoord / 3) * 3 + 1 : -((-chunkCoord / 3) * 3 + 1)
 */
public final class VeinCoordinateHelper {

    private VeinCoordinateHelper() {
        // Utility class
    }

    /**
     * Normalizes an arbitrary chunk coordinate to the center chunk of its GT5U ore vein.
     *
     * Positive coordinates:
     *   Chunks 0, 1, 2  -> Center 1
     *   Chunks 3, 4, 5  -> Center 4
     *   Chunks 6, 7, 8  -> Center 7
     *
     * Negative coordinates:
     *   Chunks -1, -2    -> Center -1
     *   Chunks -3, -4, -5 -> Center -4
     *   Chunks -6, -7, -8 -> Center -7
     *
     * @param chunkCoord Chunk X or Z coordinate
     * @return Normalized vein center chunk coordinate
     */
    public static int getVeinCenterChunk(int chunkCoord) {
        try {
            return com.sinthoras.visualprospecting.Utils.mapToCenterOreChunkCoord(chunkCoord);
        } catch (Throwable ignored) {
            if (chunkCoord >= 0) {
                return (chunkCoord / 3) * 3 + 1;
            } else {
                return -((-chunkCoord / 3) * 3 + 1);
            }
        }
    }

    /**
     * Checks if a single chunk coordinate is exactly a vein center.
     *
     * @param chunkCoord Chunk X or Z coordinate
     * @return true if Math.abs(chunkCoord) % 3 == 1
     */
    public static boolean isVeinCenter(int chunkCoord) {
        return Math.abs(chunkCoord) % 3 == 1;
    }

    /**
     * Checks if a (chunkX, chunkZ) coordinate pair is the center of an ore vein.
     *
     * @param chunkX Chunk X coordinate
     * @param chunkZ Chunk Z coordinate
     * @return true if both X and Z are vein centers
     */
    public static boolean isVeinCenter(int chunkX, int chunkZ) {
        return isVeinCenter(chunkX) && isVeinCenter(chunkZ);
    }

    /**
     * Converts a world block coordinate to its corresponding chunk coordinate.
     *
     * @param blockCoord Block X or Z coordinate
     * @return Chunk coordinate (blockCoord >> 4)
     */
    public static int blockToChunkCoord(int blockCoord) {
        return blockCoord >> 4;
    }

    /**
     * Converts a vein center chunk coordinate to the world block coordinate at the center of that chunk.
     * In Minecraft, a chunk spans [chunkCoord * 16, chunkCoord * 16 + 15]. The center block is at +8.
     *
     * @param chunkCoord Chunk coordinate
     * @return World block coordinate at the center of the chunk
     */
    public static int chunkToCenterBlockCoord(int chunkCoord) {
        return (chunkCoord << 4) + 8;
    }

    /**
     * Converts a world block coordinate directly to its vein center chunk coordinate.
     *
     * @param blockCoord Block coordinate
     * @return Vein center chunk coordinate
     */
    public static int blockToVeinCenterChunk(int blockCoord) {
        return getVeinCenterChunk(blockToChunkCoord(blockCoord));
    }

    /**
     * Checks if a chunk is within the 3x3 footprint of the vein centered at (centerChunkX, centerChunkZ).
     *
     * @param chunkX Chunk X coordinate to test
     * @param chunkZ Chunk Z coordinate to test
     * @param centerChunkX Vein center chunk X
     * @param centerChunkZ Vein center chunk Z
     * @return true if the chunk belongs to this vein
     */
    public static boolean isChunkInVein(int chunkX, int chunkZ, int centerChunkX, int centerChunkZ) {
        return getVeinCenterChunk(chunkX) == centerChunkX && getVeinCenterChunk(chunkZ) == centerChunkZ;
    }

    /**
     * Computes a 64-bit unique key for a vein center chunk pair (centerChunkX, centerChunkZ).
     *
     * @param centerChunkX Vein center chunk X
     * @param centerChunkZ Vein center chunk Z
     * @return Packed 64-bit long key
     */
    public static long getVeinKey(int centerChunkX, int centerChunkZ) {
        return (((long) centerChunkX) << 32) | (centerChunkZ & 0xFFFFFFFFL);
    }

    /**
     * Computes the squared distance from a world block position to the center of a vein.
     *
     * @param centerChunkX Vein center chunk X
     * @param centerChunkZ Vein center chunk Z
     * @param blockX World block X
     * @param blockZ World block Z
     * @return Squared horizontal Euclidean distance
     */
    public static double getDistanceSqToVeinCenter(int centerChunkX, int centerChunkZ, double blockX, double blockZ) {
        double cx = chunkToCenterBlockCoord(centerChunkX);
        double cz = chunkToCenterBlockCoord(centerChunkZ);
        double dx = cx - blockX;
        double dz = cz - blockZ;
        return dx * dx + dz * dz;
    }
}
