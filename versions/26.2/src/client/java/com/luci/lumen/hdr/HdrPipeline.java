package com.luci.lumen.hdr;

import com.luci.lumen.LumenInit;
import com.luci.lumen.config.LumenConfig;
import com.luci.lumen.vk.LumenNativeBridge;

public class HdrPipeline {
    private static boolean initialized = false;
    private static boolean hdrCapable = false;

    public static boolean isAvailable() { return hdrCapable; }
    public static boolean isActive() { return initialized && hdrCapable && LumenConfig.get().hdrEnabled; }

    public static boolean detect(long vkPhysicalDevicePtr, long vkSurfacePtr) {
        LumenInit.LOGGER.info("[Lumen] Checking HDR display support...");
        hdrCapable = LumenNativeBridge.nativeDetectHdr(vkPhysicalDevicePtr, vkSurfacePtr);
        if (hdrCapable) {
            LumenInit.LOGGER.info("[Lumen] [EXPERIMENTAL] HDR-capable display detected");
        } else {
            LumenInit.LOGGER.info("[Lumen] No HDR-capable display detected");
        }
        return hdrCapable;
    }

    public static boolean initialize(long vkDevicePtr, long vkSwapchainPtr, int width, int height) {
        if (!hdrCapable || !LumenConfig.get().hdrEnabled) return false;
        if (initialized) return true;
        LumenInit.LOGGER.info("[Lumen] [EXPERIMENTAL] Initializing HDR pipeline ({}x{} @ {} nits max)",
                width, height, (int) LumenConfig.get().hdrMaxLuminance);
        initialized = LumenNativeBridge.nativeInitHdr(vkDevicePtr, vkSwapchainPtr, width, height,
                LumenConfig.get().hdrPaperWhiteNits, LumenConfig.get().hdrMaxLuminance);
        return initialized;
    }

    public static void shutdown() {
        if (initialized) {
            LumenNativeBridge.nativeShutdownHdr();
            initialized = false;
        }
    }
}
