package com.luci.lumen;

import com.luci.lumen.config.LumenConfig;
import com.luci.lumen.config.LumenVulkanModIntegration;
import com.luci.lumen.gui.ImageAdjustmentOverlay;
import com.luci.lumen.vk.VulkanDeviceInterceptor;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public class LumenClientInit implements ClientModInitializer {
    private static KeyMapping toggleOverlayKey;

    @Override
    public void onInitializeClient() {
        LumenInit.LOGGER.info("[Lumen] Client initializing...");

        LumenConfig.get();

        VulkanDeviceInterceptor.detectRendererFallback();
        LumenInit.LOGGER.info("[Lumen] Renderer: {}", VulkanDeviceInterceptor.getStatusMessage());

        LumenVulkanModIntegration.register();

        toggleOverlayKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.lumen.toggleOverlay",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_F6,
                KeyMapping.Category.MISC
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleOverlayKey.consumeClick()) {
                ImageAdjustmentOverlay.toggle();
            }
        });

        LumenInit.LOGGER.info("[Lumen] [EXPERIMENTAL] Press F6 to open image adjustment overlay");
        if (LumenConfig.get().showExperimentalWarning) {
            LumenInit.LOGGER.warn("[Lumen] ********************************************");
            LumenInit.LOGGER.warn("[Lumen] * Lumen uses EXPERIMENTAL features        *");
            LumenInit.LOGGER.warn("[Lumen] * Frame Gen, HDR, and Upscaling are      *");
            LumenInit.LOGGER.warn("[Lumen] * marked [Experimental] in settings.      *");
            LumenInit.LOGGER.warn("[Lumen] * Enable only if you understand the risk. *");
            LumenInit.LOGGER.warn("[Lumen] ********************************************");
        }
    }
}
