package com.gtnh.orescanner.vein;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Encapsulates a GregTech 5 Unofficial ore vein generation definition.
 * Holds layer boundaries, component material metadata, and dimension suitability flags.
 */
public class VeinDefinition {

    private final String worldGenName;
    private final int minY;
    private final int maxY;
    private final short primaryMeta;
    private final short secondaryMeta;
    private final short betweenMeta;
    private final short sporadicMeta;
    private final boolean overworld;
    private final boolean nether;
    private final boolean end;
    private final boolean moon;
    private final boolean mars;
    private final boolean asteroid;
    private final String restrictBiome;
    private final boolean enabled;
    private final Object rawObject;
    private final Set<Integer> metaSet;

    public VeinDefinition(
            String worldGenName,
            int minY,
            int maxY,
            short primaryMeta,
            short secondaryMeta,
            short betweenMeta,
            short sporadicMeta,
            boolean overworld,
            boolean nether,
            boolean end,
            boolean moon,
            boolean mars,
            boolean asteroid,
            String restrictBiome,
            boolean enabled,
            Object rawObject) {
        this.worldGenName = worldGenName;
        this.minY = minY;
        this.maxY = maxY;
        this.primaryMeta = primaryMeta;
        this.secondaryMeta = secondaryMeta;
        this.betweenMeta = betweenMeta;
        this.sporadicMeta = sporadicMeta;
        this.overworld = overworld;
        this.nether = nether;
        this.end = end;
        this.moon = moon;
        this.mars = mars;
        this.asteroid = asteroid;
        this.restrictBiome = restrictBiome;
        this.enabled = enabled;
        this.rawObject = rawObject;

        Set<Integer> set = new HashSet<>();
        if (primaryMeta > 0) set.add((int) primaryMeta);
        if (secondaryMeta > 0) set.add((int) secondaryMeta);
        if (betweenMeta > 0) set.add((int) betweenMeta);
        if (sporadicMeta > 0) set.add((int) sporadicMeta);
        this.metaSet = Collections.unmodifiableSet(set);
    }

    public VeinDefinition(
            String worldGenName,
            int minY,
            int maxY,
            short primaryMeta,
            short secondaryMeta,
            short betweenMeta,
            short sporadicMeta,
            boolean overworld,
            boolean nether,
            boolean end) {
        this(worldGenName, minY, maxY, primaryMeta, secondaryMeta, betweenMeta, sporadicMeta,
             overworld, nether, end, false, false, false, "None", true, null);
    }

    public String getWorldGenName() {
        return worldGenName;
    }

    public int getMinY() {
        return minY;
    }

    public int getMaxY() {
        return maxY;
    }

    public short getPrimaryMeta() {
        return primaryMeta;
    }

    public short getSecondaryMeta() {
        return secondaryMeta;
    }

    public short getBetweenMeta() {
        return betweenMeta;
    }

    public short getSporadicMeta() {
        return sporadicMeta;
    }

    public boolean isOverworld() {
        return overworld;
    }

    public boolean isNether() {
        return nether;
    }

    public boolean isEnd() {
        return end;
    }

    public boolean isMoon() {
        return moon;
    }

    public boolean isMars() {
        return mars;
    }

    public boolean isAsteroid() {
        return asteroid;
    }

    public String getRestrictBiome() {
        return restrictBiome;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Object getRawObject() {
        return rawObject;
    }

    public Set<Integer> getMetaSet() {
        return metaSet;
    }

    /**
     * Checks if this vein definition includes the specified ore metadata.
     *
     * @param meta Detected ore metadata ID
     * @return true if meta is primary, secondary, between, or sporadic ore
     */
    public boolean containsMeta(int meta) {
        return metaSet.contains(meta);
    }

    public boolean isPrimary(int meta) {
        return primaryMeta > 0 && meta == primaryMeta;
    }

    public boolean isSecondary(int meta) {
        return secondaryMeta > 0 && meta == secondaryMeta;
    }

    public boolean isBetween(int meta) {
        return betweenMeta > 0 && meta == betweenMeta;
    }

    public boolean isSporadic(int meta) {
        return sporadicMeta > 0 && meta == sporadicMeta;
    }

    /**
     * Tests if this vein can legally generate in the specified dimension ID.
     *
     * @param dimensionId Dimension ID (0 = Overworld, -1 = Nether, 1 = End)
     * @return true if allowed in that dimension
     */
    public boolean isDimensionAllowed(int dimensionId) {
        if (dimensionId == 0) {
            return overworld;
        } else if (dimensionId == -1) {
            return nether;
        } else if (dimensionId == 1) {
            return end;
        }
        // Custom / space dimensions default to overworld unless space flags match
        return overworld;
    }

    /**
     * Checks if a given Y level falls within this vein's spawn boundaries with tolerance.
     *
     * @param y Observed Y level
     * @param tolerance Vertical leeway in blocks
     * @return true if within [minY - tolerance, maxY + tolerance]
     */
    public boolean isYInRange(int y, int tolerance) {
        return y >= (minY - tolerance) && y <= (maxY + tolerance);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VeinDefinition that = (VeinDefinition) o;
        return Objects.equals(worldGenName, that.worldGenName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(worldGenName);
    }

    @Override
    public String toString() {
        return "VeinDefinition{" +
                "name='" + worldGenName + '\'' +
                ", Y=[" + minY + "-" + maxY + "]" +
                ", metas=[" + primaryMeta + "," + secondaryMeta + "," + betweenMeta + "," + sporadicMeta + "]" +
                '}';
    }
}
