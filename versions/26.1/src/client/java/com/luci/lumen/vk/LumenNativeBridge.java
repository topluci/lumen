package com.luci.lumen.vk;

import com.luci.lumen.LumenInit;
import com.luci.lumen.config.LumenConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Environment(EnvType.CLIENT)
public final class LumenNativeBridge {
    private static boolean nativeLoaded = false;
    private static boolean nativeInitialized = false;
    private static Path extractedDllPath;

    public static boolean loadNative() {
        if (nativeLoaded) return true;

        // Try extracting from JAR resource first (production deployment)
        if (extractAndLoad()) {
            nativeLoaded = true;
            return true;
        }

        // Fallback: load from java.library.path (development runClient)
        try {
            System.loadLibrary("lumen_native_rt");
            nativeLoaded = true;
            return true;
        } catch (UnsatisfiedLinkError e) {
            LumenInit.LOGGER.warn("[Lumen] Native library not found: {}", e.getMessage());
            nativeLoaded = false;
            return false;
        }
    }

    private static boolean extractAndLoad() {
        String dllName = "liblumen_native_rt.dll";
        String resourcePath = "/META-INF/natives/" + dllName;

        try (InputStream in = LumenNativeBridge.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                LumenInit.LOGGER.debug("[Lumen] Native DLL not found in JAR at {}", resourcePath);
                return false;
            }

            extractedDllPath = Files.createTempFile("lumen_", ".dll");
            try (OutputStream out = new FileOutputStream(extractedDllPath.toFile())) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) != -1) {
                    out.write(buf, 0, len);
                }
            }

            extractedDllPath.toFile().deleteOnExit();
            System.load(extractedDllPath.toAbsolutePath().toString());
            LumenInit.LOGGER.info("[Lumen] Native DLL extracted and loaded from {}", extractedDllPath);
            return true;
        } catch (IOException | UnsatisfiedLinkError e) {
            LumenInit.LOGGER.warn("[Lumen] Failed to extract/load native DLL: {}", e.getMessage());
            return false;
        }
    }

    // --- RT Pipeline ---
    public static native boolean init(long vkInstanceHandle, long vkDeviceHandle);
    public static native boolean renderFrame();
    public static native boolean shutdown();

    // --- Post-Process ---
    public static native boolean nativeInitPostProcess(long vkDevicePtr);
    public static native boolean nativeDispatchPostProcess(
            long vkCommandBufferPtr, long inputImagePtr, long outputImagePtr,
            float brightness, float contrast, float saturation,
            float vibrance, float temperature, float tint,
            float sharpness, float filmGrain, float vignette,
            float exposure, float shadows, float highlights,
            float whites, float blacks, float clarity, float dehaze,
            int tonemapCurve);
    public static native void nativeShutdownPostProcess();

    // --- Performance ---
    public static native void nativeSetPerfParams(int targetFps, int frameSkip, int quality,
                                                   boolean adaptive, float renderScale);

    // --- Scene Upload ---
    public static native boolean nativeUploadScene(float[] vertices, int[] indices, int vertexCount, int primitiveCount);
    public static native void nativeUpdateCamera(float x, float y, float z, float dirX, float dirY, float dirZ);

    // --- Shader Packs ---
    public static native boolean nativeLoadShaderPack(String shaderPackPath);

    // --- HDR ---
    public static native boolean nativeDetectHdr(long vkPhysicalDevicePtr, long vkSurfacePtr);
    public static native boolean nativeInitHdr(long vkDevicePtr, long vkSwapchainPtr,
                                                int width, int height,
                                                float paperWhiteNits, float maxLuminance);
    public static native void nativeShutdownHdr();

    public static void markInitialized() {
        nativeInitialized = true;
    }

    public static boolean isAvailable() {
        return nativeLoaded && nativeInitialized;
    }

    public static void updatePerf(LumenConfig cfg) {
        if (isAvailable()) {
            nativeSetPerfParams(cfg.targetFps, cfg.frameSkip, cfg.rtQuality,
                                cfg.adaptivePerf, cfg.renderScale);
        }
    }
}
