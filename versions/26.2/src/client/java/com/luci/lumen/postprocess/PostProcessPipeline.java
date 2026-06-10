package com.luci.lumen.postprocess;

import com.luci.lumen.LumenInit;
import com.luci.lumen.config.LumenConfig;
import com.luci.lumen.vk.LumenNativeBridge;

public class PostProcessPipeline {
    private static boolean initialized = false;

    public static boolean isActive() { return initialized; }

    public static boolean initialize(long vkDevicePtr) {
        if (initialized) return true;
        LumenInit.LOGGER.info("[Lumen] Initializing post-process pipeline");
        initialized = LumenNativeBridge.nativeInitPostProcess(vkDevicePtr);
        if (initialized) {
            LumenInit.LOGGER.info("[Lumen] Post-process pipeline ready");
        } else {
            LumenInit.LOGGER.error("[Lumen] Failed to initialize post-process pipeline");
        }
        return initialized;
    }

    public static boolean dispatch(long vkCommandBufferPtr, long inputImagePtr, long outputImagePtr) {
        if (!initialized) return false;
        var cfg = LumenConfig.get();
        return LumenNativeBridge.nativeDispatchPostProcess(
                vkCommandBufferPtr, inputImagePtr, outputImagePtr,
                cfg.brightness, cfg.contrast, cfg.saturation,
                cfg.vibrance, cfg.temperature, cfg.tint,
                cfg.sharpness, cfg.filmGrain, cfg.vignette,
                cfg.exposure, cfg.shadows, cfg.highlights,
                cfg.whites, cfg.blacks, cfg.clarity, cfg.dehaze,
                cfg.tonemapCurve);
    }

    public static void shutdown() {
        if (initialized) {
            LumenNativeBridge.nativeShutdownPostProcess();
            initialized = false;
        }
    }
}
