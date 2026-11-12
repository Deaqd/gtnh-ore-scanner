package com.gtnh.orescanner.vein;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Represents the resolved GregTech 5 Unofficial ore vein for a 3x3 chunk grid cluster.
 * Contains disambiguation results, confidence metrics, and matching metadata.
 */
public class DisambiguatedVein {

    private final int centerChunkX;
    private final int centerChunkZ;
    private final int dimensionId;
    private final String veinName;
    private final VeinDefinition veinDefinition;
    private final double confidence;
    private final int observedMinY;
    private final int observedMaxY;
    private final Set<Integer> matchedMetas;
    private final int oreCount;
    private final int totalSnapshots;
    private final Map<Integer, Integer> metadataFrequencies;

    public DisambiguatedVein(
            int centerChunkX,
            int centerChunkZ,
            int dimensionId,
            String veinName,
            VeinDefinition veinDefinition,
            double confidence,
            int observedMinY,
            int observedMaxY,
            Set<Integer> matchedMetas,
            int oreCount,
            int totalSnapshots,
            Map<Integer, Integer> metadataFrequencies) {
        this.centerChunkX = centerChunkX;
        this.centerChunkZ = centerChunkZ;
        this.dimensionId = dimensionId;
        this.veinName = veinName != null ? veinName : "Unknown Vein";
        this.veinDefinition = veinDefinition;
        this.confidence = Math.max(0.0, Math.min(1.0, confidence));
        this.observedMinY = observedMinY;
        this.observedMaxY = observedMaxY;
        this.matchedMetas = matchedMetas != null ? Collections.unmodifiableSet(matchedMetas) : Collections.emptySet();
        this.oreCount = oreCount;
        this.totalSnapshots = totalSnapshots;
        this.metadataFrequencies = metadataFrequencies != null ? Collections.unmodifiableMap(metadataFrequencies) : Collections.emptyMap();
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

    public String getVeinName() {
        return veinName;
    }

    public VeinDefinition getVeinDefinition() {
        return veinDefinition;
    }

    public double getConfidence() {
        return confidence;
    }

    public int getConfidencePercent() {
        return (int) Math.round(confidence * 100.0);
    }

    public int getObservedMinY() {
        return observedMinY;
    }

    public int getObservedMaxY() {
        return observedMaxY;
    }

    public int getMinY() {
        if (observedMinY != Integer.MAX_VALUE) {
            return observedMinY;
        }
        return veinDefinition != null ? veinDefinition.getMinY() : 0;
    }

    public int getMaxY() {
        if (observedMaxY != Integer.MIN_VALUE) {
            return observedMaxY;
        }
        return veinDefinition != null ? veinDefinition.getMaxY() : 255;
    }

    public Set<Integer> getMatchedMetas() {
        return matchedMetas;
    }

    public int getOreCount() {
        return oreCount;
    }

    public int getTotalSnapshots() {
        return totalSnapshots;
    }

    public Map<Integer, Integer> getMetadataFrequencies() {
        return metadataFrequencies;
    }

    public Object getRawVeinLayer() {
        return veinDefinition != null ? veinDefinition.getRawObject() : null;
    }

    public boolean isConfident() {
        return confidence >= 0.35;
    }

    public int getCenterBlockX() {
        return VeinCoordinateHelper.chunkToCenterBlockCoord(centerChunkX);
    }

    public int getCenterBlockZ() {
        return VeinCoordinateHelper.chunkToCenterBlockCoord(centerChunkZ);
    }

    @Override
    public String toString() {
        return "DisambiguatedVein{" +
                "name='" + veinName + '\'' +
                ", centerChunk=[" + centerChunkX + "," + centerChunkZ + "]" +
                ", dim=" + dimensionId +
                ", confidence=" + String.format("%.1f%%", confidence * 100.0) +
                ", Y=[" + getMinY() + "-" + getMaxY() + "]" +
                ", matchingOres=" + oreCount + "/" + totalSnapshots +
                '}';
    }
}
