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
     */
    public static boolean areShadersActive() {
        if (!IRIS_LOADED || isShaderPackInUseMethod == null || irisApiInstance == null) {
            return false;
        }
        try {
            return (boolean) isShaderPackInUseMethod.invoke(irisApiInstance);
        } catch (Throwable e) {
            // Iris API call failed — fail gracefully
            return false;
        }
    }

    /**
     * Returns true if Iris is installed (regardless of shader state).
     */
    public static boolean isIrisInstalled() {
        return IRIS_LOADED;
    }
}
