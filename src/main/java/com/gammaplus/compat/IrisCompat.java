package com.gammaplus.compat;

import com.gammaplus.GammaMod;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Method;

/**
 * IrisCompat — Soft dependency wrapper for Iris Shaders.
 * Uses pure reflection to detect Iris and check shader state,
 * so no compile-time dependency on Iris is needed.
 */
public class IrisCompat {

    private static final boolean IRIS_LOADED;
    private static Method isShaderPackInUseMethod;
    private static Object irisApiInstance;

    /** How long a shader-state reading stays valid. A shader pack cannot be swapped this fast. */
    private static final long CACHE_MILLIS = 200L;

    private static volatile boolean cachedActive = false;
    private static volatile long lastCheckMillis = 0L;

    static {
        IRIS_LOADED = FabricLoader.getInstance().isModLoaded("iris");
        if (IRIS_LOADED) {
            try {
                // Resolve Iris API via reflection:
                // net.irisshaders.iris.api.v0.IrisApi.getInstance().isShaderPackInUse()
                Class<?> irisApiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                Method getInstanceMethod = irisApiClass.getMethod("getInstance");
                irisApiInstance = getInstanceMethod.invoke(null);
                isShaderPackInUseMethod = irisApiClass.getMethod("isShaderPackInUse");
                GammaMod.LOGGER.info("[Gamma Plus] Iris Shaders detected — Night Vision shader compatibility enabled.");
            } catch (Throwable e) {
                GammaMod.LOGGER.warn("[Gamma Plus] Iris detected but API reflection failed — shader detection disabled.", e);
                irisApiInstance = null;
                isShaderPackInUseMethod = null;
            }
        }
    }

    /**
     * Returns true if Iris is installed AND a shader pack is currently active.
     * Returns false if Iris isn't installed, or shaders are disabled, or reflection fails.
     *
     * <p>The reading is cached for {@value #CACHE_MILLIS}ms. Caching lives here rather than at
     * the call sites because this is reached from the per-frame lightmap path <em>and</em> twice
     * per client tick, and each of those was a fresh reflective invoke. The races are benign:
     * the worst case is two threads refreshing to the same value.
     */
    public static boolean areShadersActive() {
        if (!IRIS_LOADED || isShaderPackInUseMethod == null || irisApiInstance == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now - lastCheckMillis >= CACHE_MILLIS) {
            lastCheckMillis = now;
            try {
                cachedActive = (boolean) isShaderPackInUseMethod.invoke(irisApiInstance);
            } catch (Throwable e) {
                // Iris API call failed — fail gracefully
                cachedActive = false;
            }
        }
        return cachedActive;
    }

    /**
     * Returns true if Iris is installed (regardless of shader state).
     */
    public static boolean isIrisInstalled() {
        return IRIS_LOADED;
    }
}
