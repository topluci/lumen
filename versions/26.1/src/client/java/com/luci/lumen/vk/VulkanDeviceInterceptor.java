package com.luci.lumen.vk;

import com.luci.lumen.LumenInit;
import com.luci.lumen.config.LumenConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Field;

@Environment(EnvType.CLIENT)
public class VulkanDeviceInterceptor {
    public enum RendererType {
        OPENGL, VULKAN_BUILTIN, VULKAN_MOD, UNKNOWN
    }

    private static volatile boolean initialized = false;
    private static long instanceHandle;
    private static long deviceHandle;
    private static boolean vulkanModDetected = false;
    private static RendererType rendererType = RendererType.UNKNOWN;

    public static void onVulkanDeviceCreated(long instancePtr, long devicePtr) {
        instanceHandle = instancePtr;
        deviceHandle = devicePtr;

        LumenInit.LOGGER.info("[Lumen] Vulkan device intercepted: instance=0x{}, device=0x{}",
                Long.toHexString(instancePtr), Long.toHexString(devicePtr));

        if (!initialized && instancePtr != 0 && devicePtr != 0) {
            initialized = true;
            rendererType = vulkanModDetected ? RendererType.VULKAN_MOD : RendererType.VULKAN_BUILTIN;
            LumenInit.LOGGER.info("[Lumen] Renderer: {} ({})", rendererType,
                    rendererType == RendererType.VULKAN_MOD ? "VulkanMod" : "built-in Vulkan");
            initNativeLibrary();
        }
    }

    private static boolean tryVulkanMod() {
        if (vulkanModDetected) return true;
        try {
            Class<?> vulkanClass = Class.forName("net.vulkanmod.vulkan.Vulkan");
            Object vkDevice = vulkanClass.getMethod("getVkDevice").invoke(null);
            long dev = (long) vkDevice.getClass().getMethod("address").invoke(vkDevice);

            Field instanceField = vulkanClass.getDeclaredField("instance");
            instanceField.setAccessible(true);
            Object vkInstance = instanceField.get(null);
            long inst = (long) vkInstance.getClass().getMethod("address").invoke(vkInstance);

            if (inst != 0 && dev != 0) {
                vulkanModDetected = true;
                LumenInit.LOGGER.info("[Lumen] VulkanMod detected: instance=0x{}, device=0x{}",
                        Long.toHexString(inst), Long.toHexString(dev));
                onVulkanDeviceCreated(inst, dev);
                return true;
            }
        } catch (Exception e) {
            LumenInit.LOGGER.debug("[Lumen] VulkanMod not available: {}", e.getMessage());
        }
        return false;
    }

    public static void detectRendererFallback() {
        if (rendererType != RendererType.UNKNOWN) return;
        if (FabricLoader.getInstance().isModLoaded("vulkanmod") && tryVulkanMod()) return;
        rendererType = RendererType.OPENGL;
        LumenInit.LOGGER.warn("[Lumen] ********************************************");
        LumenInit.LOGGER.warn("[Lumen] * No Vulkan device detected                 *");
        LumenInit.LOGGER.warn("[Lumen] * Lumen RT features require Vulkan.         *");
        LumenInit.LOGGER.warn("[Lumen] * Switch to Vulkan renderer or install      *");
        LumenInit.LOGGER.warn("[Lumen] * VulkanMod for your Minecraft version.     *");
        LumenInit.LOGGER.warn("[Lumen] ********************************************");
    }

    public static RendererType getRendererType() {
        if (rendererType == RendererType.UNKNOWN) detectRendererFallback();
        return rendererType;
    }

    public static String getStatusMessage() {
        return switch (getRendererType()) {
            case VULKAN_BUILTIN -> "\u00a7aVulkan (built-in)";
            case VULKAN_MOD -> "\u00a7aVulkan (VulkanMod)";
            case OPENGL -> "\u00a7cOpenGL \u00a7e- RT disabled, switch to Vulkan";
            case UNKNOWN -> "\u00a77Detecting...";
        };
    }

    public static boolean isVulkanModActive() { return vulkanModDetected; }

    private static void initNativeLibrary() {
        if (!LumenNativeBridge.loadNative()) {
            LumenInit.LOGGER.warn("[Lumen] Native library not found, RT disabled");
            return;
        }
        LumenInit.LOGGER.info("[Lumen] Native library loaded successfully");

        boolean result = LumenNativeBridge.init(instanceHandle, deviceHandle);
        LumenInit.LOGGER.info("[Lumen] Native bridge init result: {}", result);

        if (result) {
            LumenNativeBridge.markInitialized();
            LumenNativeBridge.updatePerf(LumenConfig.get());
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                LumenInit.LOGGER.info("[Lumen] Shutting down native bridge...");
                LumenNativeBridge.shutdown();
            }, "Lumen Shutdown"));
        }
    }

    public static boolean shouldSkipRender() {
        if (!initialized) return true;
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc == null) return true;
        if (LumenConfig.get().skipWhenPaused && mc.isPaused()) return true;
        return false;
    }

    public static boolean retryVulkanMod() {
        if (isVulkanAvailable()) return true;
        boolean found = tryVulkanMod();
        if (found) {
            LumenInit.LOGGER.info("[Lumen] VulkanMod detected on retry — renderer updated to {}", getStatusMessage());
        } else {
            LumenInit.LOGGER.info("[Lumen] VulkanMod still not available — running on OpenGL");
        }
        return found;
    }

    public static long getDeviceHandle() { return deviceHandle; }
    public static long getInstanceHandle() { return instanceHandle; }
    public static boolean isVulkanAvailable() { return deviceHandle != 0; }
}
