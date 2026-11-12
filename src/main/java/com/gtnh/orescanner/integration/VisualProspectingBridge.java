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
    private static Method putOreVeinsMethod = null;
    private static Method putOreVeinMethod = null;
    private static Method putProspectionResultsMethod = null;
    private static Method getVeinTypeStringMethod = null;
    private static Method getVeinTypeIdMethod = null;
    private static Method getVeinTypeShortMethod = null;

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

        try {
            // Strategy 1: ClientCache.instance.putOreVeins(Collection/List)
            if (clientCacheInstance != null && putOreVeinsMethod != null) {
                putOreVeinsMethod.invoke(clientCacheInstance, positions);
                GTNHOreScannerMod.LOG.debug("Injected " + positions.size() + " veins via ClientCache.putOreVeins");
                return;
            }

            // Strategy 2: ClientCache.instance.putOreVein(single) in a loop
            if (clientCacheInstance != null && putOreVeinMethod != null) {
                for (Object pos : positions) {
                    putOreVeinMethod.invoke(clientCacheInstance, pos);
                }
                GTNHOreScannerMod.LOG.debug("Injected " + positions.size() + " veins via ClientCache.putOreVein");
                return;
            }

            // Strategy 3: ClientCache.instance.putProspectionResults(positions, Collections.emptyList())
            if (clientCacheInstance != null && putProspectionResultsMethod != null) {
                putProspectionResultsMethod.invoke(clientCacheInstance, positions, Collections.emptyList());
                GTNHOreScannerMod.LOG.debug("Injected " + positions.size() + " veins via putProspectionResults");
                return;
            }

            // Strategy 4: LogicalClientCache static methods
            if (logicalClientCacheClass != null) {
                Method mPutVeins = findMethod(logicalClientCacheClass, "putOreVeins", List.class, Collection.class);
                if (mPutVeins != null && Modifier.isStatic(mPutVeins.getModifiers())) {
                    mPutVeins.invoke(null, positions);
                    GTNHOreScannerMod.LOG.debug("Injected " + positions.size() + " veins via LogicalClientCache.putOreVeins");
                    return;
                }

                Method mPutVein = findMethod(logicalClientCacheClass, "putOreVein", oreVeinPositionClass, Object.class);
                if (mPutVein != null && Modifier.isStatic(mPutVein.getModifiers())) {
                    for (Object pos : positions) {
                        mPutVein.invoke(null, pos);
                    }
                    GTNHOreScannerMod.LOG.debug("Injected " + positions.size() + " veins via LogicalClientCache.putOreVein");
                    return;
                }
            }

            // Strategy 5: VisualProspecting_API.LogicalClient
            if (vpApiClass != null) {
                Method mApi = findMethod(vpApiClass, "putProspectionResults", List.class, List.class);
                if (mApi != null && Modifier.isStatic(mApi.getModifiers())) {
                    mApi.invoke(null, positions, Collections.emptyList());
                    GTNHOreScannerMod.LOG.debug("Injected " + positions.size() + " veins via VisualProspecting_API");
                    return;
                }
            }

            GTNHOreScannerMod.LOG.warn("No suitable injection method found on VisualProspecting cache.");
        } catch (Throwable t) {
            GTNHOreScannerMod.LOG.error("Failed to inject ore veins into VisualProspecting: " + t.getMessage(), t);
        }
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

        // Try VeinTypeCaching.getVeinType(String)
        if (getVeinTypeStringMethod != null) {
            for (String name : candidateNames) {
                try {
                    Object result = getVeinTypeStringMethod.invoke(null, name);
                    if (result != null) {
                        return result;
                    }
                } catch (Throwable ignored) {}
            }
        }

        // Try VeinTypeCaching.getVeinTypeId(String) -> getVeinType(short)
        if (getVeinTypeIdMethod != null && getVeinTypeShortMethod != null) {
            for (String name : candidateNames) {
                try {
                    Object idObj = getVeinTypeIdMethod.invoke(null, name);
                    if (idObj instanceof Number) {
                        short id = ((Number) idObj).shortValue();
                        Object result = getVeinTypeShortMethod.invoke(null, id);
                        if (result != null) {
                            return result;
                        }
                    }
                } catch (Throwable ignored) {}
            }
        }

        // Try VeinType static lookup or enum values
        if (veinTypeClass != null) {
            try {
                Method mGet = findMethod(veinTypeClass, "getVeinType", String.class);
                if (mGet != null && Modifier.isStatic(mGet.getModifiers())) {
                    for (String name : candidateNames) {
                        Object result = mGet.invoke(null, name);
                        if (result != null) return result;
                    }
                }

                // If VeinType is an Enum, scan constants
                if (veinTypeClass.isEnum()) {
                    for (Object constant : veinTypeClass.getEnumConstants()) {
                        String constName = constant.toString();
                        for (String name : candidateNames) {
                            if (constName.equalsIgnoreCase(name) ||
                                    constName.replace("_", ".").equalsIgnoreCase(name)) {
                                return constant;
                            }
                        }
                    }
                }

                // Fallback to static field NO_VEIN if present
                Field fNoVein = findField(veinTypeClass, "NO_VEIN", "noVein");
                if (fNoVein != null && Modifier.isStatic(fNoVein.getModifiers())) {
                    return fNoVein.get(null);
                }
            } catch (Throwable ignored) {}
        }

        return null;
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
                    // VisualProspecting trust levels: API (3) > RESCAN (2) > GENERATED (1) > UNKNOWN (0)
                    String[] preferredNames = new String[] { "API", "RESCAN", "PROSPECTION", "GENERATED" };
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
                    Field fProsp = findField(veinSourceClass, "API", "rescan", "PROSPECTION", "prospection");
                    if (fProsp != null && Modifier.isStatic(fProsp.getModifiers())) {
                        veinSourceProspection = fProsp.get(null);
                    }
                }
            } catch (Throwable ignored) {}
        }
    }

    private static void resolveMethods() {
        if (clientCacheClass != null) {
            putOreVeinsMethod = findMethod(clientCacheClass, "putOreVeins", List.class, Collection.class);
            if (putOreVeinsMethod == null) {
                putOreVeinsMethod = findMethodByName(clientCacheClass, "putOreVeins");
            }

            putOreVeinMethod = findMethod(clientCacheClass, "putOreVein", oreVeinPositionClass, Object.class);
            if (putOreVeinMethod == null) {
                putOreVeinMethod = findMethodByName(clientCacheClass, "putOreVein");
            }

            putProspectionResultsMethod = findMethod(clientCacheClass, "putProspectionResults", List.class, List.class);
        }

        if (veinTypeCachingClass != null) {
            getVeinTypeStringMethod = findMethod(veinTypeCachingClass, "getVeinType", String.class);
            getVeinTypeIdMethod = findMethod(veinTypeCachingClass, "getVeinTypeId", String.class);
            getVeinTypeShortMethod = findMethod(veinTypeCachingClass, "getVeinType", short.class);
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
