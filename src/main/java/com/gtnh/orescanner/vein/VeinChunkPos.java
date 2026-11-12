package com.gtnh.orescanner.vein;

import java.util.Objects;

/**
 * Immutable key representing a normalized vein center chunk position in a specific dimension.
 */
public final class VeinChunkPos {

    private final int centerChunkX;
    private final int centerChunkZ;
    private final int dimensionId;

    public VeinChunkPos(int centerChunkX, int centerChunkZ, int dimensionId) {
        this.centerChunkX = centerChunkX;
        this.centerChunkZ = centerChunkZ;
        this.dimensionId = dimensionId;
    }

    public VeinChunkPos(int centerChunkX, int centerChunkZ) {
        this(centerChunkX, centerChunkZ, 0);
    }

    public static VeinChunkPos fromChunk(int chunkX, int chunkZ, int dimensionId) {
        return new VeinChunkPos(
            VeinCoordinateHelper.getVeinCenterChunk(chunkX),
            VeinCoordinateHelper.getVeinCenterChunk(chunkZ),
            dimensionId
        );
    }

    public int getCenterChunkX() {
        return centerChunkX;
    }

    public int getCenterChunkZ() {
        return centerChunkZ;
    }

    public int getDimensionId() {
        return dimensionId;
    }

    public int getCenterBlockX() {
        return VeinCoordinateHelper.chunkToCenterBlockCoord(centerChunkX);
    }

    public int getCenterBlockZ() {
        return VeinCoordinateHelper.chunkToCenterBlockCoord(centerChunkZ);
    }

    public long getVeinKey() {
        return VeinCoordinateHelper.getVeinKey(centerChunkX, centerChunkZ);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VeinChunkPos that = (VeinChunkPos) o;
        return centerChunkX == that.centerChunkX &&
               centerChunkZ == that.centerChunkZ &&
               dimensionId == that.dimensionId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(centerChunkX, centerChunkZ, dimensionId);
    }

    @Override
    public String toString() {
        return "VeinChunkPos{" +
               "centerChunk=[" + centerChunkX + "," + centerChunkZ + "]" +
               ", dim=" + dimensionId +
               '}';
    }
}
