package com.gtnh.orescanner.integration;

import com.gtnh.orescanner.GTNHOreScannerMod;
import com.gtnh.orescanner.vein.DisambiguatedVein;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.FutureTask;

/**
 * Bridges detected GregTech ore veins directly into VisualProspecting's client cache
 * for JourneyMap / Xaero's World Map visualization.
 *
 * Employs defensive reflection and dynamic class/method resolution to ensure full
 * runtime compatibility with VisualProspecting without causing NoClassDefFoundError
 * if package paths or versions differ.
 *
 * All cache modifications are safely dispatched to Minecraft's client scheduled task
 * queue via Minecraft.getMinecraft().addScheduledTask(...) so that JourneyMap renderers
 * are updated on the render thread.
 */
@SideOnly(Side.CLIENT)
public class VisualProspectingBridge {

    private static volatile boolean initialized = false;
    private static final Object INIT_LOCK = new Object();

    // Dynamically resolved VisualProspecting classes
    private static Class<?> clientCacheClass = null;
    private static Class<?> logicalClientCacheClass = null;
    private static Class<?> oreVeinPositionClass = null;
    private static Class<?> veinTypeCachingClass = null;
    private static Class<?> veinTypeClass = null;
    private static Class<?> veinSourceClass = null;
    private static Class<?> vpApiClass = null;

    // Resolved instances and enum constants
    private static Object clientCacheInstance = null;
    private static Object veinSourceProspection = null;

    // Cached methods
    private static Method putOreVeinsSingleArgMethod = null;
    private static Method putOreVeinsTwoArgMethod = null;
    private static Method putOreVeinMethod = null;
    private static Method putProspectionResultsMethod = null;
    private static Method getVeinTypeStringMethod = null;
    private static Method getVeinTypeIdMethod = null;
    private static Method getVeinTypeShortMethod = null;
    private static Method getVeinTypesMethod = null;
    private static Field noVeinField = null;

    // Cached constructors
    private static Constructor<?> oreVeinPositionCtor = null;
    private static int ctorParamCount = 0;

    /**
     * Checks if VisualProspecting classes are present in the current runtime environment.
     */
    public static boolean isVisualProspectingAvailable() {
        ensureInitialized();
        return (clientCacheClass != null || logicalClientCacheClass != null || vpApiClass != null) &&
                oreVeinPositionClass != null;
    }

    /**
     * Injects a list of disambiguated ore veins into VisualProspecting's client cache.
     * Safely constructs OreVeinPosition objects and dispatches updates to Minecraft's client
     * scheduled task queue.
     *
     * @param veins List of DisambiguatedVein instances
     * @return Number of veins successfully prepared and scheduled for injection
     */
    public static int injectVeins(final List<DisambiguatedVein> veins) {
        if (veins == null || veins.isEmpty()) {
            return 0;
        }

        if (!isVisualProspectingAvailable()) {
            GTNHOreScannerMod.LOG.debug("VisualProspecting is not available; skipping cache injection.");
            return 0;
        }

        final List<Object> positions = new ArrayList<Object>();
        for (DisambiguatedVein vein : veins) {
            if (vein == null) continue;

            Object pos = createOreVeinPosition(
                    vein.getDimensionId(),
                    vein.getCenterChunkX(),
                    vein.getCenterChunkZ(),
                    vein.getVeinName()
            );

            if (pos != null) {
                positions.add(pos);
            }
        }

        if (positions.isEmpty()) {
            return 0;
        }

        // Dispatch cache updates safely to Minecraft's client scheduled task queue
        scheduleOnClientThread(new Runnable() {
            @Override
            public void run() {
                injectPositionsDirect(positions);
            }
        });

        return positions.size();
    }

    /**
     * Injects a single disambiguated vein into VisualProspecting's client cache.
     */
    public static boolean injectVein(final DisambiguatedVein vein) {
        if (vein == null) {
            return false;
        }
        return injectVeins(Collections.singletonList(vein)) > 0;
    }

    /**
     * Injects a single vein by its raw dimension, center chunk coordinates, and vein name.
     */
    public static boolean injectVein(final int dimensionId, final int centerChunkX, final int centerChunkZ, final String veinName) {
        if (!isVisualProspectingAvailable()) {
            return false;
        }

        final Object pos = createOreVeinPosition(dimensionId, centerChunkX, centerChunkZ, veinName);
        if (pos == null) {
            return false;
        }

        scheduleOnClientThread(new Runnable() {
            @Override
            public void run() {
                injectPositionsDirect(Collections.singletonList(pos));
            }
        });

        return true;
    }

    /**
     * Directly injects prepared OreVeinPosition objects into the resolved VisualProspecting cache.
     * Should be executed on the Minecraft client thread.
     */
    public static void injectPositionsDirect(List<Object> positions) {
        if (positions == null || positions.isEmpty()) {
            return;
        }

        ensureInitialized();

        boolean injected = false;

        // Strategy 1: ClientCache.instance.putOreVeins(List) [single-argument overload]
        if (!injected && clientCacheInstance != null && putOreVeinsSingleArgMethod != null) {
            try {
                putOreVeinsSingleArgMethod.invoke(clientCacheInstance, positions);
                GTNHOreScannerMod.LOG.info("Injected " + positions.size() + " veins via ClientCache.putOreVeins(List)");
                injected = true;
            } catch (Throwable t) {
                GTNHOreScannerMod.LOG.warn("Failed 1-arg putOreVeins: " + t.getMessage(), t);
            }
        }

        // Strategy 2: ClientCache.instance.putOreVeins(List, boolean) [two-argument overload]
        if (!injected && clientCacheInstance != null && putOreVeinsTwoArgMethod != null) {
            try {
                putOreVeinsTwoArgMethod.invoke(clientCacheInstance, positions, false);
                GTNHOreScannerMod.LOG.info("Injected " + positions.size() + " veins via ClientCache.putOreVeins(List, boolean)");
                injected = true;
            } catch (Throwable t) {
                GTNHOreScannerMod.LOG.warn("Failed 2-arg putOreVeins: " + t.getMessage(), t);
            }
        }

        // Strategy 3: Dynamic reflection fallback across all methods on ClientCache instance
        if (!injected && clientCacheInstance != null) {
            for (Method m : clientCacheInstance.getClass().getMethods()) {
                if ("putOreVeins".equals(m.getName())) {
                    Class<?>[] pTypes = m.getParameterTypes();
                    try {
                        m.setAccessible(true);
                        if (pTypes.length == 1 && pTypes[0].isAssignableFrom(List.class)) {
                            m.invoke(clientCacheInstance, positions);
                            GTNHOreScannerMod.LOG.info("Injected " + positions.size() + " veins via dynamic putOreVeins(List)");
                            injected = true;
                            break;
                        } else if (pTypes.length == 2 && pTypes[0].isAssignableFrom(List.class) && (pTypes[1] == boolean.class || pTypes[1] == Boolean.class)) {
                            m.invoke(clientCacheInstance, positions, false);
                            GTNHOreScannerMod.LOG.info("Injected " + positions.size() + " veins via dynamic putOreVeins(List, boolean)");
                            injected = true;
                            break;
                        }
                    } catch (Throwable t) {
                        GTNHOreScannerMod.LOG.warn("Failed dynamic putOreVeins: " + t.getMessage(), t);
                    }
                }
            }
        }

        // Strategy 4: ClientCache.instance.putOreVein(single) in a loop
        if (!injected && clientCacheInstance != null && putOreVeinMethod != null) {
            try {
                int count = 0;
                for (Object pos : positions) {
                    putOreVeinMethod.invoke(clientCacheInstance, pos);
                    count++;
                }
                GTNHOreScannerMod.LOG.info("Injected " + count + " veins via ClientCache.putOreVein loop");
                injected = true;
            } catch (Throwable t) {
                GTNHOreScannerMod.LOG.warn("Failed single putOreVein loop: " + t.getMessage(), t);
            }
        }

        // Strategy 5: ClientCache.instance.putProspectionResults(positions, Collections.emptyList())
        if (!injected && clientCacheInstance != null && putProspectionResultsMethod != null) {
            try {
                putProspectionResultsMethod.invoke(clientCacheInstance, positions, Collections.emptyList());
                GTNHOreScannerMod.LOG.info("Injected " + positions.size() + " veins via putProspectionResults");
                injected = true;
            } catch (Throwable t) {
                GTNHOreScannerMod.LOG.warn("Failed putProspectionResults: " + t.getMessage(), t);
            }
        }

        // Strategy 6: LogicalClientCache static methods
        if (!injected && logicalClientCacheClass != null) {
            try {
                Method mPutVeins = findMethod(logicalClientCacheClass, "putOreVeins", List.class);
                if (mPutVeins != null && Modifier.isStatic(mPutVeins.getModifiers())) {
                    mPutVeins.invoke(null, positions);
                    GTNHOreScannerMod.LOG.info("Injected " + positions.size() + " veins via LogicalClientCache.putOreVeins");
                    injected = true;
                }
            } catch (Throwable t) {
                GTNHOreScannerMod.LOG.warn("Failed LogicalClientCache: " + t.getMessage(), t);
            }
        }

        // Strategy 7: VisualProspecting_API.LogicalClient
        if (!injected && vpApiClass != null) {
            try {
                Method mApi = findMethod(vpApiClass, "putProspectionResults", List.class, List.class);
                if (mApi != null && Modifier.isStatic(mApi.getModifiers())) {
                    mApi.invoke(null, positions, Collections.emptyList());
                    GTNHOreScannerMod.LOG.info("Injected " + positions.size() + " veins via VisualProspecting_API");
                    injected = true;
                }
            } catch (Throwable t) {
                GTNHOreScannerMod.LOG.warn("Failed VisualProspecting_API: " + t.getMessage(), t);
            }
        }

        if (injected) {
            refreshMapLayers();
        } else {
            GTNHOreScannerMod.LOG.warn("No suitable injection method succeeded for VisualProspecting cache.");
        }
    }

    /**
     * Forces JourneyMap / Navigator layers to refresh so that injected ore veins
     * are immediately rendered without needing to re-open the map.
     */
    public static void refreshMapLayers() {
        try {
            Class<?> oreVeinLayerManagerClass = findClass(
                    "com.sinthoras.visualprospecting.integration.model.layers.OreVeinLayerManager"
            );
            if (oreVeinLayerManagerClass != null) {
                Field fInstance = findField(oreVeinLayerManagerClass, "instance", "INSTANCE");
                Object layerManager = fInstance != null ? fInstance.get(null) : null;
                if (layerManager != null) {
                    Method mRefresh = findMethod(layerManager.getClass(), "forceRefresh");
                    if (mRefresh != null) {
                        mRefresh.invoke(layerManager);
                        GTNHOreScannerMod.LOG.debug("Triggered OreVeinLayerManager.forceRefresh()");
                    }

                    Class<?> eventClass = findClass("com.gtnewhorizons.navigator.api.event.LayerRefreshEvent");
                    if (eventClass != null) {
                        Class<?> baseLayerClass = findClass("com.gtnewhorizons.navigator.api.model.layers.LayerManager");
                        if (baseLayerClass != null) {
                            Constructor<?> ctor = eventClass.getConstructor(baseLayerClass);
                            if (ctor != null) {
                                Object event = ctor.newInstance(layerManager);
                                cpw.mods.fml.common.eventhandler.EventBus bus = net.minecraftforge.common.MinecraftForge.EVENT_BUS;
                                if (bus != null) {
                                    bus.post((cpw.mods.fml.common.eventhandler.Event) event);
                                    GTNHOreScannerMod.LOG.debug("Posted LayerRefreshEvent to MinecraftForge.EVENT_BUS");
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            GTNHOreScannerMod.LOG.warn("Failed to trigger map layer refresh: " + t.getMessage());
        }
    }

    /**
     * Checks whether a VeinType object represents an empty / missing vein.
     */
    public static boolean isNoVein(Object vt) {
        if (vt == null) return true;
        if (noVeinField != null) {
            try {
                if (vt == noVeinField.get(null)) return true;
            } catch (Throwable ignored) {}
        }
        try {
            Field fName = findField(vt.getClass(), "name");
            if (fName != null) {
                Object n = fName.get(vt);
                if ("ore.mix.none".equals(n) || "NO_VEIN".equals(n)) return true;
            }
        } catch (Throwable ignored) {}
        String s = vt.toString();
        return "NO_VEIN".equalsIgnoreCase(s) || "ore.mix.none".equalsIgnoreCase(s);
    }

    /**
     * Creates an OreVeinPosition object using dynamic reflection based on the available constructor.
     */
    public static Object createOreVeinPosition(int dimensionId, int centerChunkX, int centerChunkZ, String veinName) {
        ensureInitialized();

        if (oreVeinPositionClass == null) {
            return null;
        }

        Object veinTypeObj = resolveVeinType(veinName);
        if (isNoVein(veinTypeObj)) {
            GTNHOreScannerMod.LOG.warn("Could not resolve valid VeinType for vein '" + veinName + "'; skipping map position creation.");
            return null;
        }

        short veinTypeId = resolveVeinTypeId(veinName, veinTypeObj);

        try {
            if (oreVeinPositionCtor != null) {
                Class<?>[] params = oreVeinPositionCtor.getParameterTypes();
                switch (ctorParamCount) {
                    case 6:
                        // (int dim, int chunkX, int chunkZ, VeinType type, boolean depleted, VeinSource source)
                        return oreVeinPositionCtor.newInstance(
                                dimensionId,
                                centerChunkX,
                                centerChunkZ,
                                veinTypeObj,
                                false,
                                veinSourceProspection
                        );
                    case 5:
                        // (int dim, int chunkX, int chunkZ, VeinType/short/String, boolean depleted)
                        if (params[3] == short.class || params[3] == Short.class) {
                            return oreVeinPositionCtor.newInstance(dimensionId, centerChunkX, centerChunkZ, veinTypeId, false);
                        } else if (params[3] == String.class) {
                            return oreVeinPositionCtor.newInstance(dimensionId, centerChunkX, centerChunkZ, veinName, false);
                        } else {
                            return oreVeinPositionCtor.newInstance(dimensionId, centerChunkX, centerChunkZ, veinTypeObj, false);
                        }
                    case 4:
                        // (int dim, int chunkX, int chunkZ, VeinType/short/String)
                        if (params[3] == short.class || params[3] == Short.class) {
                            return oreVeinPositionCtor.newInstance(dimensionId, centerChunkX, centerChunkZ, veinTypeId);
                        } else if (params[3] == String.class) {
                            return oreVeinPositionCtor.newInstance(dimensionId, centerChunkX, centerChunkZ, veinName);
                        } else {
                            return oreVeinPositionCtor.newInstance(dimensionId, centerChunkX, centerChunkZ, veinTypeObj);
                        }
                }
            }

            // Fallback: search all public constructors
            for (Constructor<?> ctor : oreVeinPositionClass.getConstructors()) {
                Class<?>[] params = ctor.getParameterTypes();
                if (params.length >= 3 && params[0] == int.class && params[1] == int.class && params[2] == int.class) {
                    if (params.length == 6) {
                        return ctor.newInstance(dimensionId, centerChunkX, centerChunkZ, veinTypeObj, false, veinSourceProspection);
                    } else if (params.length == 5) {
                        if (params[3] == short.class) {
                            return ctor.newInstance(dimensionId, centerChunkX, centerChunkZ, veinTypeId, false);
                        }
                        return ctor.newInstance(dimensionId, centerChunkX, centerChunkZ, veinTypeObj, false);
                    } else if (params.length == 4) {
                        return ctor.newInstance(dimensionId, centerChunkX, centerChunkZ, veinTypeObj);
                    }
                }
            }
        } catch (Throwable t) {
            GTNHOreScannerMod.LOG.warn("Failed to instantiate OreVeinPosition for vein '" + veinName + "': " + t.getMessage());
        }

        return null;
    }

    /**
     * Resolves a VisualProspecting VeinType object from a GregTech vein name.
     */
    public static Object resolveVeinType(String veinName) {
        if (veinName == null || veinName.trim().isEmpty()) {
            return null;
        }

        ensureInitialized();

        String[] candidateNames = generateVeinNameVariants(veinName);

        // 1. Try VeinTypeCaching.getVeinType(String)
        if (getVeinTypeStringMethod != null) {
            for (String name : candidateNames) {
                try {
                    Object result = getVeinTypeStringMethod.invoke(null, name);
                    if (result != null && !isNoVein(result)) {
                        return result;
                    }
                } catch (Throwable ignored) {}
            }
        }

        // 2. Try VeinTypeCaching.getVeinTypeId(String) -> getVeinType(short)
        if (getVeinTypeIdMethod != null && getVeinTypeShortMethod != null) {
            for (String name : candidateNames) {
                try {
                    Object idObj = getVeinTypeIdMethod.invoke(null, name);
                    if (idObj instanceof Number) {
                        short id = ((Number) idObj).shortValue();
                        if (id > 0) {
                            Object result = getVeinTypeShortMethod.invoke(null, id);
                            if (result != null && !isNoVein(result)) {
                                return result;
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            }
        }

        // 3. Scan VeinTypeCaching.getVeinTypes() directly by comparing name and localized vein name
        if (getVeinTypesMethod != null) {
            try {
                Object col = getVeinTypesMethod.invoke(null);
                if (col instanceof Collection<?>) {
                    Collection<?> types = (Collection<?>) col;
                    // First pass: exact match on name or getVeinName()
                    for (Object vt : types) {
                        if (isNoVein(vt)) continue;
                        String typeName = getVeinTypeName(vt);
                        String locName = getVeinTypeLocalizedName(vt);
                        for (String cand : candidateNames) {
                            if (cand.equalsIgnoreCase(typeName) || cand.equalsIgnoreCase(locName)) {
                                return vt;
                            }
                        }
                    }
                    // Second pass: normalized match (contains / stripped symbols)
                    String normalizedSearch = normalizeVeinName(veinName);
                    for (Object vt : types) {
                        if (isNoVein(vt)) continue;
                        String typeName = normalizeVeinName(getVeinTypeName(vt));
                        String locName = normalizeVeinName(getVeinTypeLocalizedName(vt));
                        if ((!typeName.isEmpty() && (typeName.contains(normalizedSearch) || normalizedSearch.contains(typeName))) ||
                            (!locName.isEmpty() && (locName.contains(normalizedSearch) || normalizedSearch.contains(locName)))) {
                            return vt;
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        // 4. Try VeinType static lookup or enum values
        if (veinTypeClass != null) {
            try {
                Method mGet = findMethod(veinTypeClass, "getVeinType", String.class);
                if (mGet != null && Modifier.isStatic(mGet.getModifiers())) {
                    for (String name : candidateNames) {
                        Object result = mGet.invoke(null, name);
                        if (result != null && !isNoVein(result)) return result;
                    }
                }

                // If VeinType is an Enum, scan constants
                if (veinTypeClass.isEnum()) {
                    for (Object constant : veinTypeClass.getEnumConstants()) {
                        if (isNoVein(constant)) continue;
                        String constName = constant.toString();
                        for (String name : candidateNames) {
                            if (constName.equalsIgnoreCase(name) ||
                                    constName.replace("_", ".").equalsIgnoreCase(name)) {
                                return constant;
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        return null;
    }

    private static String getVeinTypeName(Object vt) {
        if (vt == null) return "";
        try {
            Field f = findField(vt.getClass(), "name");
            if (f != null) {
                Object val = f.get(vt);
                return val != null ? val.toString() : "";
            }
        } catch (Throwable ignored) {}
        return "";
    }

    private static String getVeinTypeLocalizedName(Object vt) {
        if (vt == null) return "";
        try {
            Method m = findMethod(vt.getClass(), "getVeinName");
            if (m != null) {
                Object val = m.invoke(vt);
                return val != null ? val.toString() : "";
            }
        } catch (Throwable ignored) {}
        return "";
    }

    private static String normalizeVeinName(String s) {
        if (s == null) return "";
        return s.toLowerCase().replace("ore.mix.", "").replace(".", "").replace("_", "").replace("&", "").replace(" ", "").trim();
    }

    private static short resolveVeinTypeId(String veinName, Object veinTypeObj) {
        if (getVeinTypeIdMethod != null && veinName != null) {
            for (String name : generateVeinNameVariants(veinName)) {
                try {
                    Object idObj = getVeinTypeIdMethod.invoke(null, name);
                    if (idObj instanceof Number) {
                        return ((Number) idObj).shortValue();
                    }
                } catch (Throwable ignored) {}
            }
        }

        if (veinTypeObj != null) {
            try {
                Field fId = findField(veinTypeObj.getClass(), "veinId", "mVeinId", "id");
                if (fId != null) {
                    fId.setAccessible(true);
                    Object val = fId.get(veinTypeObj);
                    if (val instanceof Number) {
                        return ((Number) val).shortValue();
                    }
                }
            } catch (Throwable ignored) {}
        }

        return 0;
    }

    private static String[] generateVeinNameVariants(String veinName) {
        List<String> list = new ArrayList<String>();
        list.add(veinName);

        if (veinName.startsWith("ore.mix.")) {
            list.add(veinName.substring("ore.mix.".length()));
        } else {
            list.add("ore.mix." + veinName);
            list.add("ore.mix." + veinName.toLowerCase());
        }

        String simple = veinName.replace("ore.mix.", "").replace(".", "_");
        list.add(simple);
        list.add(simple.toLowerCase());
        list.add(simple.toUpperCase());

        return list.toArray(new String[list.size()]);
    }

    /**
     * Dispatches a Runnable to Minecraft's client scheduled task queue.
     * Checks Minecraft.getMinecraft().addScheduledTask(...) dynamically so it
     * executes safely on the main client / render thread.
     */
    public static void scheduleOnClientThread(final Runnable task) {
        if (task == null) {
            return;
        }

        try {
            final Minecraft mc = Minecraft.getMinecraft();
            if (mc != null) {
                // Try Minecraft 1.7.10 func_152344_a(Runnable) directly
                try {
                    mc.func_152344_a(task);
                    return;
                } catch (Throwable ignored) {}

                // Try reflection invocation of addScheduledTask(Runnable) for Cleanroom / backport environments
                try {
                    Method m = mc.getClass().getMethod("addScheduledTask", Runnable.class);
                    m.invoke(mc, task);
                    return;
                } catch (Throwable ignored) {}

                // Try func_152344_a(FutureTask)
                try {
                    Method m = mc.getClass().getMethod("func_152344_a", FutureTask.class);
                    m.invoke(mc, new FutureTask<Void>(task, null));
                    return;
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {
            // Headless / test / offline harness fallback
        }

        // Fallback: execute directly
        try {
            task.run();
        } catch (Throwable t) {
            GTNHOreScannerMod.LOG.error("Failed to execute scheduled client task: " + t.getMessage(), t);
        }
    }

    /**
     * Initializes reflection handles for VisualProspecting dynamically.
     */
    private static void ensureInitialized() {
        if (initialized) {
            return;
        }

        synchronized (INIT_LOCK) {
            if (initialized) {
                return;
            }

            resolveClasses();
            resolveInstances();
            resolveMethods();
            resolveConstructors();

            initialized = true;
        }
    }

    private static void resolveClasses() {
        // ClientCache candidates
        clientCacheClass = findClass(
                "com.sinthoras.visualprospecting.database.ClientCache",
                "visualprospecting.database.ClientCache",
                "com.sinthoras.visualprospecting.database.cache.ClientCache"
        );

        // LogicalClientCache candidates
        logicalClientCacheClass = findClass(
                "com.sinthoras.visualprospecting.database.cache.LogicalClientCache",
                "com.sinthoras.visualprospecting.database.LogicalClientCache",
                "visualprospecting.database.LogicalClientCache"
        );

        // OreVeinPosition candidates
        oreVeinPositionClass = findClass(
                "com.sinthoras.visualprospecting.database.OreVeinPosition",
                "visualprospecting.database.OreVeinPosition",
                "com.sinthoras.visualprospecting.model.OreVeinPosition"
        );

        // VeinTypeCaching candidates
        veinTypeCachingClass = findClass(
                "com.sinthoras.visualprospecting.database.veintypes.VeinTypeCaching",
                "visualprospecting.database.veintypes.VeinTypeCaching",
                "com.sinthoras.visualprospecting.database.VeinTypeCaching",
                "visualprospecting.database.VeinTypeCaching"
        );

        // VeinType candidates
        veinTypeClass = findClass(
                "com.sinthoras.visualprospecting.database.veintypes.VeinType",
                "visualprospecting.database.veintypes.VeinType",
                "com.sinthoras.visualprospecting.database.VeinType",
                "visualprospecting.database.VeinType"
        );

        // VeinSource candidates
        veinSourceClass = findClass(
                "com.sinthoras.visualprospecting.database.cache.VeinSource",
                "com.sinthoras.visualprospecting.database.VeinSource",
                "visualprospecting.database.cache.VeinSource",
                "visualprospecting.database.VeinSource"
        );

        // VisualProspecting_API candidates
        vpApiClass = findClass(
                "com.sinthoras.visualprospecting.api.VisualProspecting_API$LogicalClient",
                "visualprospecting.api.VisualProspecting_API$LogicalClient",
                "com.sinthoras.visualprospecting.api.VisualProspecting_API"
        );
    }

    private static void resolveInstances() {
        if (clientCacheClass != null) {
            try {
                Field fInstance = findField(clientCacheClass, "instance", "INSTANCE");
                if (fInstance != null && Modifier.isStatic(fInstance.getModifiers())) {
                    fInstance.setAccessible(true);
                    clientCacheInstance = fInstance.get(null);
                }
            } catch (Throwable ignored) {}

            if (clientCacheInstance == null) {
                try {
                    Method mGet = findMethod(clientCacheClass, "getInstance");
                    if (mGet == null) {
                        mGet = findMethod(clientCacheClass, "instance");
                    }
                    if (mGet != null && Modifier.isStatic(mGet.getModifiers())) {
                        clientCacheInstance = mGet.invoke(null);
                    }
                } catch (Throwable ignored) {}
            }
        }

        if (veinSourceClass != null) {
            try {
                if (veinSourceClass.isEnum()) {
                    // VisualProspecting trust levels: ADMIN (4) > API (3) > RESCAN (2) > GENERATED (1) > UNKNOWN (0)
                    String[] preferredNames = new String[] { "ADMIN", "API", "RESCAN", "GENERATED" };
                    for (String preferred : preferredNames) {
                        for (Object constant : veinSourceClass.getEnumConstants()) {
                            if (preferred.equalsIgnoreCase(constant.toString())) {
                                veinSourceProspection = constant;
                                break;
                            }
                        }
                        if (veinSourceProspection != null) {
                            break;
                        }
                    }
                    if (veinSourceProspection == null && veinSourceClass.getEnumConstants().length > 0) {
                        veinSourceProspection = veinSourceClass.getEnumConstants()[0];
                    }
                } else {
                    Field fProsp = findField(veinSourceClass, "ADMIN", "admin", "API", "api", "RESCAN", "rescan");
                    if (fProsp != null && Modifier.isStatic(fProsp.getModifiers())) {
                        veinSourceProspection = fProsp.get(null);
                    }
                }
            } catch (Throwable ignored) {}
        }
    }

    private static void resolveMethods() {
        if (clientCacheClass != null) {
            putOreVeinsSingleArgMethod = findMethod(clientCacheClass, "putOreVeins", List.class);
            if (putOreVeinsSingleArgMethod == null) {
                putOreVeinsSingleArgMethod = findMethod(clientCacheClass, "putOreVeins", Collection.class);
            }

            putOreVeinsTwoArgMethod = findMethod(clientCacheClass, "putOreVeins", List.class, boolean.class);
            if (putOreVeinsTwoArgMethod == null) {
                putOreVeinsTwoArgMethod = findMethod(clientCacheClass, "putOreVeins", Collection.class, boolean.class);
            }

            putOreVeinMethod = findMethod(clientCacheClass, "putOreVein", oreVeinPositionClass);
            if (putOreVeinMethod == null) {
                putOreVeinMethod = findMethod(clientCacheClass, "putOreVein", Object.class);
            }

            putProspectionResultsMethod = findMethod(clientCacheClass, "putProspectionResults", List.class, List.class);
        }

        if (veinTypeCachingClass != null) {
            getVeinTypeStringMethod = findMethod(veinTypeCachingClass, "getVeinType", String.class);
            getVeinTypeIdMethod = findMethod(veinTypeCachingClass, "getVeinTypeId", String.class);
            getVeinTypeShortMethod = findMethod(veinTypeCachingClass, "getVeinType", short.class);
            getVeinTypesMethod = findMethod(veinTypeCachingClass, "getVeinTypes");
        }

        if (veinTypeClass != null) {
            noVeinField = findField(veinTypeClass, "NO_VEIN", "noVein");
        }
    }

    private static void resolveConstructors() {
        if (oreVeinPositionClass == null) {
            return;
        }

        Constructor<?>[] ctors = oreVeinPositionClass.getConstructors();
        Constructor<?> bestCtor = null;
        int maxParams = 0;

        for (Constructor<?> ctor : ctors) {
            Class<?>[] p = ctor.getParameterTypes();
            if (p.length >= 3 && p[0] == int.class && p[1] == int.class && p[2] == int.class) {
                if (p.length > maxParams) {
                    maxParams = p.length;
                    bestCtor = ctor;
                }
            }
        }

        oreVeinPositionCtor = bestCtor;
        ctorParamCount = maxParams;
    }

    private static Class<?> findClass(String... candidates) {
        for (String name : candidates) {
            try {
                return Class.forName(name);
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static Field findField(Class<?> clazz, String... names) {
        Class<?> curr = clazz;
        while (curr != null && curr != Object.class) {
            for (String name : names) {
                try {
                    Field f = curr.getDeclaredField(name);
                    f.setAccessible(true);
                    return f;
                } catch (NoSuchFieldException ignored) {}
            }
            curr = curr.getSuperclass();
        }
        return null;
    }

    private static Method findMethod(Class<?> clazz, String name, Class<?>... paramTypes) {
        Class<?> curr = clazz;
        while (curr != null && curr != Object.class) {
            try {
                Method m = curr.getDeclaredMethod(name, paramTypes);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {}
            curr = curr.getSuperclass();
        }

        // Search compatible parameter types
        curr = clazz;
        while (curr != null && curr != Object.class) {
            for (Method m : curr.getDeclaredMethods()) {
                if (m.getName().equals(name)) {
                    Class<?>[] mParams = m.getParameterTypes();
                    if (mParams.length == paramTypes.length) {
                        boolean match = true;
                        for (int i = 0; i < mParams.length; i++) {
                            if (paramTypes[i] != null && !mParams[i].isAssignableFrom(paramTypes[i])) {
                                match = false;
                                break;
                            }
                        }
                        if (match) {
                            m.setAccessible(true);
                            return m;
                        }
                    }
                }
            }
            curr = curr.getSuperclass();
        }
        return null;
    }

    private static Method findMethodByName(Class<?> clazz, String name) {
        Class<?> curr = clazz;
        while (curr != null && curr != Object.class) {
            for (Method m : curr.getDeclaredMethods()) {
                if (m.getName().equals(name)) {
                    m.setAccessible(true);
                    return m;
                }
            }
            curr = curr.getSuperclass();
        }
        return null;
    }
}
