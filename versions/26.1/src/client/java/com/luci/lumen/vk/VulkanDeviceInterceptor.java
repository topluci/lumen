package com.luci.lumen.vk;

import com.luci.lumen.api.renderer.RendererManager;
import com.luci.lumen.api.renderer.RendererBackend;
import com.luci.lumen.api.renderer.backend.VulkanModBackend;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class VulkanDeviceInterceptor {
    public enum RendererType {
        OPENGL, VULKAN_BUILTIN, VULKAN_MOD, UNKNOWN
    }

    public static void onVulkanDeviceCreated(long instancePtr, long devicePtr) {
        // Handled by VulkanModBackend via reflection
    }

    public static void detectRendererFallback() {
        RendererManager.get().detect();
    }

    public static RendererType getRendererType() {
        var active = RendererManager.get().getActive();
        if (active instanceof VulkanModBackend) return RendererType.VULKAN_MOD;
        return RendererType.OPENGL;
    }

    public static String getStatusMessage() {
        return RendererManager.get().getStatusMessage();
    }

    public static boolean isVulkanModActive() {
        return RendererManager.get().getActive() instanceof VulkanModBackend;
    }

    public static boolean shouldSkipRender() {
        if (!RendererManager.get().getActive().isAvailable()) return true;
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc == null) return true;
        return false;
    }

    public static boolean retryVulkanMod() {
        RendererManager.get().detect();
        return isVulkanModActive();
    }

    public static long getDeviceHandle() {
        var backend = RendererManager.get().getActive();
        if (backend instanceof VulkanModBackend vmb) return vmb.getDeviceHandle();
        return 0;
    }

    public static long getInstanceHandle() {
        var backend = RendererManager.get().getActive();
        if (backend instanceof VulkanModBackend vmb) return vmb.getInstanceHandle();
        return 0;
    }

    public static boolean isVulkanAvailable() {
        return RendererManager.get().getActive().isAvailable();
    }
}
