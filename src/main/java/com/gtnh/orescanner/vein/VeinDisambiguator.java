package com.gtnh.orescanner.vein;

import com.gtnh.orescanner.scanner.OreSnapshot;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Disambiguates detected ore clusters into known GregTech 5 Unofficial ore veins.
 *
 * Groups OreSnapshots by normalized 3x3 vein center chunks (centerChunkX, centerChunkZ).
 * Compares detected material metadata against GT_Worldgen_GT_Ore_Layer.sList definitions,
 * accounting for primary/secondary/between/sporadic ores, spawn Y ranges, and dimension constraints.
 *
 * Implements a dynamic reflection loader for runtime GT5U environments and a rich
 * static fallback registry when reflection is unavailable or obfuscated.
 */
public class VeinDisambiguator {

    public static final double DEFAULT_MIN_CONFIDENCE = 0.20;

    private static volatile List<VeinDefinition> cachedDefinitions = null;
    private static final Object LOCK = new Object();

    /**
     * Fallback definitions containing standard GregTech 5 Unofficial ore veins.
     */
    private static final List<VeinDefinition> FALLBACK_VEINS = new ArrayList<VeinDefinition>();

    static {
        // Overworld Veins
        addFallback("ore.mix.copper", 10, 30, (short) 27, (short) 81, (short) 60, (short) 32, true, false, false);
        addFallback("ore.mix.tin", 40, 100, (short) 24, (short) 83, (short) 24, (short) 83, true, false, false);
        addFallback("ore.mix.cassiterite", 40, 120, (short) 24, (short) 83, (short) 25, (short) 24, true, false, false);
        addFallback("ore.mix.iron", 10, 40, (short) 20, (short) 88, (short) 18, (short) 48, true, false, false);
        addFallback("ore.mix.magnetite", 50, 120, (short) 354, (short) 32, (short) 355, (short) 86, true, false, false);
        addFallback("ore.mix.coal", 50, 80, (short) 535, (short) 535, (short) 538, (short) 535, true, false, false);
        addFallback("ore.mix.tetrahedrite", 80, 120, (short) 78, (short) 81, (short) 74, (short) 78, true, false, false);
        addFallback("ore.mix.bauxite", 50, 90, (short) 19, (short) 36, (short) 11, (short) 36, true, false, false);
        addFallback("ore.mix.nickel", 10, 40, (short) 31, (short) 52, (short) 28, (short) 32, true, false, false);
        addFallback("ore.mix.redstone", 10, 40, (short) 66, (short) 67, (short) 26, (short) 66, true, false, false);
        addFallback("ore.mix.lapis", 20, 50, (short) 43, (short) 44, (short) 72, (short) 22, true, false, false);
        addFallback("ore.mix.gold", 60, 80, (short) 354, (short) 86, (short) 355, (short) 86, true, false, false);
        addFallback("ore.mix.manganese", 20, 30, (short) 33, (short) 72, (short) 61, (short) 77, true, false, false);
        addFallback("ore.mix.salts", 50, 70, (short) 65, (short) 68, (short) 46, (short) 73, true, false, false);
        addFallback("ore.mix.apatite", 40, 60, (short) 14, (short) 57, (short) 57, (short) 14, true, false, false);
        addFallback("ore.mix.lead", 40, 60, (short) 30, (short) 45, (short) 70, (short) 30, true, false, false);
        addFallback("ore.mix.sphalerite", 10, 40, (short) 73, (short) 89, (short) 75, (short) 73, true, false, false);
        addFallback("ore.mix.pitchblende", 10, 40, (short) 58, (short) 85, (short) 58, (short) 85, true, false, false);
        addFallback("ore.mix.uranium", 20, 40, (short) 85, (short) 84, (short) 85, (short) 84, true, false, false);
        addFallback("ore.mix.molybdenum", 20, 50, (short) 50, (short) 51, (short) 59, (short) 50, true, false, false);
        addFallback("ore.mix.mica", 10, 40, (short) 42, (short) 49, (short) 24, (short) 49, true, false, false);
        addFallback("ore.mix.monazite", 20, 40, (short) 16, (short) 49, (short) 450, (short) 16, true, false, false);
        addFallback("ore.mix.quartz", 40, 80, (short) 64, (short) 17, (short) 516, (short) 64, true, false, false);
        addFallback("ore.mix.olivine", 10, 40, (short) 13, (short) 47, (short) 55, (short) 34, true, false, false);
        addFallback("ore.mix.soapstone", 10, 40, (short) 71, (short) 76, (short) 34, (short) 56, true, false, false);

        // Nether Veins
        addFallback("ore.mix.nether_quartz", 40, 80, (short) 522, (short) 522, (short) 522, (short) 522, false, true, false);
        addFallback("ore.mix.sulfur", 10, 30, (short) 75, (short) 60, (short) 73, (short) 75, false, true, false);
        addFallback("ore.mix.nether_tetrahedrite", 80, 120, (short) 78, (short) 81, (short) 74, (short) 78, false, true, false);

        // End Veins
        addFallback("ore.mix.tungstate", 20, 60, (short) 69, (short) 82, (short) 47, (short) 69, false, false, true);
        addFallback("ore.mix.beryllium", 10, 30, (short) 15, (short) 29, (short) 15, (short) 29, false, false, true);
    }

    private static void addFallback(String name, int minY, int maxY, short p, short s, short b, short sp,
                                    boolean ow, boolean nether, boolean end) {
        FALLBACK_VEINS.add(new VeinDefinition(name, minY, maxY, p, s, b, sp, ow, nether, end));
    }

    /**
     * Groups snapshots into 3x3 vein clusters and disambiguates each cluster.
     *
     * @param snapshots List of raw OreSnapshots from ChunkScanner
     * @return List of DisambiguatedVein objects meeting the default confidence threshold
     */
    public static List<DisambiguatedVein> disambiguate(List<OreSnapshot> snapshots) {
        return disambiguate(snapshots, DEFAULT_MIN_CONFIDENCE);
    }

    /**
     * Groups snapshots into 3x3 vein clusters and disambiguates each cluster with custom confidence threshold.
     *
     * @param snapshots List of raw OreSnapshots from ChunkScanner
     * @param minConfidence Minimum required confidence (0.0 to 1.0)
     * @return List of DisambiguatedVein objects
     */
    public static List<DisambiguatedVein> disambiguate(List<OreSnapshot> snapshots, double minConfidence) {
        if (snapshots == null || snapshots.isEmpty()) {
            return Collections.emptyList();
        }

        // 1. Group snapshots by normalized vein center chunk and dimension
        Map<VeinChunkPos, List<OreSnapshot>> clusters = new LinkedHashMap<VeinChunkPos, List<OreSnapshot>>();

        for (OreSnapshot snapshot : snapshots) {
            if (snapshot == null) continue;

            int meta = snapshot.getMeta();
            // Filter out invalid metas and small ores (meta >= 16000)
            if (meta < 0 || meta >= 16000) {
                continue;
            }

            int centerChunkX = VeinCoordinateHelper.getVeinCenterChunk(snapshot.getChunkX());
            int centerChunkZ = VeinCoordinateHelper.getVeinCenterChunk(snapshot.getChunkZ());
            int dimensionId = snapshot.getDimensionId();

            VeinChunkPos key = new VeinChunkPos(centerChunkX, centerChunkZ, dimensionId);
            List<OreSnapshot> list = clusters.get(key);
            if (list == null) {
                list = new ArrayList<OreSnapshot>();
                clusters.put(key, list);
            }
            list.add(snapshot);
        }

        // 2. Disambiguate each cluster
        List<DisambiguatedVein> results = new ArrayList<DisambiguatedVein>();
        for (Map.Entry<VeinChunkPos, List<OreSnapshot>> entry : clusters.entrySet()) {
            VeinChunkPos pos = entry.getKey();
            List<OreSnapshot> cluster = entry.getValue();

            DisambiguatedVein resolved = disambiguateCluster(
                    pos.getCenterChunkX(),
                    pos.getCenterChunkZ(),
                    pos.getDimensionId(),
                    cluster
            );

            if (resolved != null && resolved.getConfidence() >= minConfidence) {
                results.add(resolved);
            }
        }

        return results;
    }

    /**
     * Disambiguates a single ore cluster located at (centerChunkX, centerChunkZ).
     *
     * @param centerChunkX Normalized vein center chunk X
     * @param centerChunkZ Normalized vein center chunk Z
     * @param dimensionId Dimension ID of the cluster
     * @param cluster List of OreSnapshots belonging to this vein
     * @return DisambiguatedVein or null if no ores present
     */
    public static DisambiguatedVein disambiguateCluster(
            int centerChunkX,
            int centerChunkZ,
            int dimensionId,
            List<OreSnapshot> cluster) {
        if (cluster == null || cluster.isEmpty()) {
            return null;
        }

        Map<Integer, Integer> metaCounts = new HashMap<Integer, Integer>();
        int minObsY = Integer.MAX_VALUE;
        int maxObsY = Integer.MIN_VALUE;

        for (OreSnapshot s : cluster) {
            int meta = s.getMeta();
            Integer count = metaCounts.get(meta);
            metaCounts.put(meta, count == null ? 1 : count + 1);

            int y = s.getY();
            if (y < minObsY) minObsY = y;
            if (y > maxObsY) maxObsY = y;
        }

        int totalBlocks = cluster.size();
        Set<Integer> detectedMetas = metaCounts.keySet();

        List<VeinDefinition> definitions = getVeinDefinitions();
        VeinDefinition bestDef = null;
        double bestScore = -1.0;
        Set<Integer> bestMatchedMetas = Collections.emptySet();
        int bestMatchingBlocks = 0;

        for (VeinDefinition def : definitions) {
            if (def == null) continue;

            Set<Integer> matched = new HashSet<Integer>();
            int matchingBlocks = 0;

            for (Integer meta : detectedMetas) {
                if (def.containsMeta(meta)) {
                    matched.add(meta);
                    matchingBlocks += metaCounts.get(meta);
                }
            }

            if (matched.isEmpty() || matchingBlocks == 0) {
                continue;
            }

            // Material matching ratios
            double blockMatchRatio = (double) matchingBlocks / totalBlocks;
            double metaMatchRatio = (double) matched.size() / detectedMetas.size();
            double veinCoverageRatio = (double) matched.size() / Math.max(1, def.getMetaSet().size());

            // Role bonuses
            double roleBonus = 0.0;
            for (Integer meta : matched) {
                if (def.isPrimary(meta)) roleBonus += 0.10;
                else if (def.isSecondary(meta)) roleBonus += 0.05;
                else if (def.isBetween(meta)) roleBonus += 0.03;
                else if (def.isSporadic(meta)) roleBonus += 0.02;
            }
            if (roleBonus > 0.15) roleBonus = 0.15;

            // Y-level bounds check (with ±6 tolerance)
            double yFactor = 1.0;
            int tolMinY = def.getMinY() - 6;
            int tolMaxY = def.getMaxY() + 6;

            if (minObsY >= tolMinY && maxObsY <= tolMaxY) {
                yFactor = 1.0;
            } else {
                int overlap = Math.max(0, Math.min(maxObsY, tolMaxY) - Math.max(minObsY, tolMinY));
                int span = Math.max(1, maxObsY - minObsY);
                if (overlap > 0) {
                    yFactor = 0.50 + 0.50 * ((double) overlap / span);
                } else {
                    int dist = Math.min(Math.abs(minObsY - tolMaxY), Math.abs(maxObsY - tolMinY));
                    yFactor = Math.max(0.05, 1.0 - (dist / 32.0));
                }
            }

            // Dimension constraint check
            double dimFactor = def.isDimensionAllowed(dimensionId) ? 1.0 : 0.20;

            // Combined weighted confidence score
            double baseScore = (0.40 * blockMatchRatio) + (0.30 * metaMatchRatio) + (0.15 * veinCoverageRatio) + roleBonus;
            double score = baseScore * yFactor * dimFactor;
            score = Math.max(0.0, Math.min(1.0, score));

            if (score > bestScore) {
                bestScore = score;
                bestDef = def;
                bestMatchedMetas = matched;
                bestMatchingBlocks = matchingBlocks;
            }
        }

        if (bestDef == null) {
            return new DisambiguatedVein(
                    centerChunkX,
                    centerChunkZ,
                    dimensionId,
                    "Unknown Vein",
                    null,
                    0.0,
                    minObsY,
                    maxObsY,
                    Collections.<Integer>emptySet(),
                    0,
                    totalBlocks,
                    metaCounts
            );
        }

        return new DisambiguatedVein(
                centerChunkX,
                centerChunkZ,
                dimensionId,
                bestDef.getWorldGenName(),
                bestDef,
                bestScore,
                minObsY,
                maxObsY,
                bestMatchedMetas,
                bestMatchingBlocks,
                totalBlocks,
                metaCounts
        );
    }

    /**
     * Retrieves the active vein definitions, dynamically extracting them from
     * GregTech 5 Unofficial via reflection if available, or falling back to pre-defined layers.
     */
    public static List<VeinDefinition> getVeinDefinitions() {
        if (cachedDefinitions != null) {
            return cachedDefinitions;
        }

        synchronized (LOCK) {
            if (cachedDefinitions != null) {
                return cachedDefinitions;
            }

            List<VeinDefinition> reflected = loadVeinsFromReflection();
            if (reflected != null && !reflected.isEmpty()) {
                cachedDefinitions = Collections.unmodifiableList(reflected);
                logInfo("Successfully loaded " + reflected.size() +
                        " GT ore layer definitions from GregTech 5 Unofficial via reflection.");
            } else {
                cachedDefinitions = Collections.unmodifiableList(new ArrayList<VeinDefinition>(FALLBACK_VEINS));
                logInfo("Using " + FALLBACK_VEINS.size() +
                        " built-in GT5U fallback ore vein definitions.");
            }

            return cachedDefinitions;
        }
    }

    /**
     * Clears cached vein definitions to force a reload on the next query.
     */
    public static void reloadVeinDefinitions() {
        synchronized (LOCK) {
            cachedDefinitions = null;
        }
    }

    /**
     * Inspects GregTech 5 Unofficial runtime classes via reflection to extract live ore layer definitions.
     */
    private static List<VeinDefinition> loadVeinsFromReflection() {
        List<VeinDefinition> list = new ArrayList<VeinDefinition>();

        String[] classCandidates = new String[] {
                "gregtech.common.WorldgenGTOreLayer",
                "gregtech.common.GT_Worldgen_GT_Ore_Layer",
                "gregtech.api.world.WorldgenGTOreLayer",
                "gregtech.common.GT_Worldgenerator"
        };

        Class<?> layerClass = null;
        for (String className : classCandidates) {
            try {
                layerClass = Class.forName(className);
                if (layerClass != null) {
                    break;
                }
            } catch (Throwable ignored) {
            }
        }

        if (layerClass == null) {
            return Collections.emptyList();
        }

        Field sListField = null;
        String[] fieldCandidates = new String[] { "sList", "mList", "oreLayers" };
        for (String fieldName : fieldCandidates) {
            try {
                Field f = layerClass.getDeclaredField(fieldName);
                if (f != null && Modifier.isStatic(f.getModifiers())) {
                    sListField = f;
                    break;
                }
            } catch (Throwable ignored) {
            }
        }

        if (sListField == null) {
            for (Field f : layerClass.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) && Collection.class.isAssignableFrom(f.getType())) {
                    sListField = f;
                    break;
                }
            }
        }

        if (sListField == null) {
            return Collections.emptyList();
        }

        try {
            sListField.setAccessible(true);
            Object rawList = sListField.get(null);
            if (!(rawList instanceof Collection<?>)) {
                return Collections.emptyList();
            }

            Collection<?> collection = (Collection<?>) rawList;
            for (Object item : collection) {
                if (item == null) continue;
                VeinDefinition def = parseVeinObject(item);
                if (def != null && def.isEnabled()) {
                    list.add(def);
                }
            }
        } catch (Throwable t) {
            logWarn("Failed while extracting GT ore layers via reflection: " + t.getMessage());
        }

        return list;
    }

    private static void logInfo(String message) {
        try {
            Class<?> modClass = Class.forName("com.gtnh.orescanner.GTNHOreScannerMod");
            Field logField = modClass.getField("LOG");
            Object logger = logField.get(null);
            if (logger != null) {
                logger.getClass().getMethod("info", Object.class).invoke(logger, message);
                return;
            }
        } catch (Throwable ignored) {
        }
        System.out.println("[GTNH Ore Vein Scanner] " + message);
    }

    private static void logWarn(String message) {
        try {
            Class<?> modClass = Class.forName("com.gtnh.orescanner.GTNHOreScannerMod");
            Field logField = modClass.getField("LOG");
            Object logger = logField.get(null);
            if (logger != null) {
                logger.getClass().getMethod("warn", Object.class).invoke(logger, message);
                return;
            }
        } catch (Throwable ignored) {
        }
        System.err.println("[GTNH Ore Vein Scanner] " + message);
    }

    private static VeinDefinition parseVeinObject(Object layerObj) {
        Class<?> clazz = layerObj.getClass();

        String name = getFieldString(clazz, layerObj, "mWorldGenName", "worldGenName", "mName", "name");
        if (name == null || name.trim().isEmpty()) {
            return null;
        }

        int minY = getFieldInt(clazz, layerObj, 0, "mMinY", "minY");
        int maxY = getFieldInt(clazz, layerObj, 255, "mMaxY", "maxY");

        short pMeta = getOreMaterialMeta(clazz, layerObj, "mPrimary", "mPrimaryMeta", "primaryMeta", "primary");
        short sMeta = getOreMaterialMeta(clazz, layerObj, "mSecondary", "mSecondaryMeta", "secondaryMeta", "secondary");
        short bMeta = getOreMaterialMeta(clazz, layerObj, "mBetween", "mBetweenMeta", "betweenMeta", "between");
        short spMeta = getOreMaterialMeta(clazz, layerObj, "mSporadic", "mSporadicMeta", "sporadicMeta", "sporadic");

        boolean ow = getFieldBool(clazz, layerObj, true, "mOverworld", "overworld");
        boolean nether = getFieldBool(clazz, layerObj, false, "mNether", "nether");
        boolean end = getFieldBool(clazz, layerObj, false, "mEnd", "end");
        boolean moon = getFieldBool(clazz, layerObj, false, "mMoon", "moon");
        boolean mars = getFieldBool(clazz, layerObj, false, "mMars", "mars");
        boolean asteroid = getFieldBool(clazz, layerObj, false, "mAsteroid", "asteroid");

        // Inspect dynamic dimension sets if present in modern GT5U
        Set<?> allowedDims = null;
        try {
            Method mDims = clazz.getMethod("getAllowedDimensions");
            Object res = mDims.invoke(layerObj);
            if (res instanceof Set<?>) {
                allowedDims = (Set<?>) res;
            }
        } catch (Throwable ignored) {}

        if (allowedDims == null) {
            Field fDims = findField(clazz, "mAllowedDimensions");
            if (fDims != null) {
                try {
                    fDims.setAccessible(true);
                    Object res = fDims.get(layerObj);
                    if (res instanceof Set<?>) {
                        allowedDims = (Set<?>) res;
                    }
                } catch (Throwable ignored) {}
            }
        }

        if (allowedDims != null && !allowedDims.isEmpty()) {
            ow = false;
            nether = false;
            end = false;
            moon = false;
            mars = false;
            asteroid = false;
            for (Object d : allowedDims) {
                if (d == null) continue;
                String dName = d.toString().toLowerCase();
                if (dName.contains("overworld") || dName.contains("world") || dName.contains("earth") || dName.equals("0")) ow = true;
                if (dName.contains("nether") || dName.contains("hell") || dName.equals("-1")) nether = true;
                if (dName.contains("end") || dName.equals("1")) end = true;
                if (dName.contains("moon")) moon = true;
                if (dName.contains("mars")) mars = true;
                if (dName.contains("asteroid")) asteroid = true;
            }
        }
        String restrictBiome = getFieldString(clazz, layerObj, "mRestrictBiome", "restrictBiome");
        boolean enabled = getFieldBool(clazz, layerObj, true, "mEnabled", "enabled");

        return new VeinDefinition(
                name,
                minY,
                maxY,
                pMeta,
                sMeta,
                bMeta,
                spMeta,
                ow,
                nether,
                end,
                moon,
                mars,
                asteroid,
                restrictBiome != null ? restrictBiome : "None",
                enabled,
                layerObj
        );
    }

    private static String getFieldString(Class<?> clazz, Object obj, String... names) {
        for (String name : names) {
            Field f = findField(clazz, name);
            if (f != null) {
                try {
                    f.setAccessible(true);
                    Object val = f.get(obj);
                    if (val != null) return val.toString();
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    private static int getFieldInt(Class<?> clazz, Object obj, int defaultValue, String... names) {
        for (String name : names) {
            Field f = findField(clazz, name);
            if (f != null) {
                try {
                    f.setAccessible(true);
                    Object val = f.get(obj);
                    if (val instanceof Number) {
                        return ((Number) val).intValue();
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        return defaultValue;
    }

    private static boolean getFieldBool(Class<?> clazz, Object obj, boolean defaultValue, String... names) {
        for (String name : names) {
            Field f = findField(clazz, name);
            if (f != null) {
                try {
                    f.setAccessible(true);
                    Object val = f.get(obj);
                    if (val instanceof Boolean) {
                        return (Boolean) val;
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        return defaultValue;
    }

    private static short getOreMaterialMeta(Class<?> clazz, Object obj, String... names) {
        for (String name : names) {
            Field f = findField(clazz, name);
            if (f != null) {
                try {
                    f.setAccessible(true);
                    Object val = f.get(obj);
                    if (val instanceof Number) {
                        return ((Number) val).shortValue();
                    }
                    if (val != null) {
                        // Check for IOreMaterial / Materials method getId()
                        try {
                            Method mGetId = val.getClass().getMethod("getId");
                            Object id = mGetId.invoke(val);
                            if (id instanceof Number) {
                                return ((Number) id).shortValue();
                            }
                        } catch (Throwable ignored) {}

                        // Check for field mMetaItemSubID
                        Field fMeta = findField(val.getClass(), "mMetaItemSubID");
                        if (fMeta != null) {
                            fMeta.setAccessible(true);
                            Object subId = fMeta.get(val);
                            if (subId instanceof Number) {
                                return ((Number) subId).shortValue();
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            }
        }
        return 0;
    }

    private static Field findField(Class<?> clazz, String name) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }
}
