package com.gtnh.orescanner.scanner;

import java.util.Objects;

/**
 * Immutable snapshot of a detected ore tile entity in a chunk.
 */
public final class OreSnapshot {

    private final int chunkX;
    private final int chunkZ;
    private final int x;
    private final int y;
    private final int z;
    private final int meta;
    private final int dimensionId;

    public OreSnapshot(int chunkX, int chunkZ, int x, int y, int z, int meta, int dimensionId) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.x = x;
        this.y = y;
        this.z = z;
        this.meta = meta;
        this.dimensionId = dimensionId;
    }

    public OreSnapshot(int chunkX, int chunkZ, int x, int y, int z, int meta) {
        this(chunkX, chunkZ, x, y, z, meta, 0);
    }

    public int getChunkX() {
        return chunkX;
    }

    public int getChunkZ() {
        return chunkZ;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public int getMeta() {
        return meta;
    }

    public short getShortMeta() {
        return (short) meta;
    }

    public int getDimensionId() {
        return dimensionId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OreSnapshot that = (OreSnapshot) o;
        return chunkX == that.chunkX &&
               chunkZ == that.chunkZ &&
               x == that.x &&
               y == that.y &&
               z == that.z &&
               meta == that.meta &&
               dimensionId == that.dimensionId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(chunkX, chunkZ, x, y, z, meta, dimensionId);
    }

    @Override
    public String toString() {
        return "OreSnapshot{" +
                "chunk=[" + chunkX + "," + chunkZ + "]" +
                ", pos=[" + x + "," + y + "," + z + "]" +
                ", meta=" + meta +
                ", dim=" + dimensionId +
                '}';
    }
}
