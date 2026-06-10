package com.luci.lumen.mixin.client;

import com.luci.lumen.compat.CompatibilityGuard;
import com.luci.lumen.config.LumenConfig;
import com.luci.lumen.gui.ImageAdjustmentOverlay;
import com.luci.lumen.vk.ChunkGeometryCapture;
import com.luci.lumen.vk.LumenNativeBridge;
import com.luci.lumen.vk.VulkanDeviceInterceptor;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.GameRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Accessor("gameRenderState")
    abstract GameRenderState lumen$getGameRenderState();

    private static boolean lumenCompatChecked = false;
    private static boolean lumenVulkanRetried = false;
    private static boolean lumenGeometryEnabled = false;

    @Inject(method = "extract", at = @At("TAIL"))
    private void afterExtract(DeltaTracker deltaTracker, boolean bl, CallbackInfo ci) {
        if (!lumenCompatChecked) {
            CompatibilityGuard.check();
            lumenCompatChecked = true;
        }

        if (!lumenVulkanRetried) {
            lumenVulkanRetried = true;
            if (!VulkanDeviceInterceptor.isVulkanAvailable()) {
                VulkanDeviceInterceptor.retryVulkanMod();
            }
        }

        if (!LumenConfig.get().enabled) return;
        if (VulkanDeviceInterceptor.shouldSkipRender()) return;

        if (LumenNativeBridge.isAvailable()) {
            // Enable geometry capture on first frame
            if (!lumenGeometryEnabled) {
                ChunkGeometryCapture.setEnabled(true);
                lumenGeometryEnabled = true;
            }

            // Update camera position and direction
            var renderState = lumen$getGameRenderState();
            if (renderState.levelRenderState != null) {
                var cam = renderState.levelRenderState.cameraRenderState;
                if (cam != null && cam.initialized) {
                    float yawRad = cam.yRot * (float) Math.PI / 180.0f;
                    float pitchRad = cam.xRot * (float) Math.PI / 180.0f;
                    float dirX = (float) (-Math.sin(yawRad) * Math.cos(pitchRad));
                    float dirY = (float) (-Math.sin(pitchRad));
                    float dirZ = (float) (Math.cos(yawRad) * Math.cos(pitchRad));
                    LumenNativeBridge.nativeUpdateCamera(
                            (float) cam.pos.x, (float) cam.pos.y, (float) cam.pos.z,
                            dirX, dirY, dirZ);
                }
            }

            // Upload any newly captured geometry
            ChunkGeometryCapture.uploadScene();

            LumenNativeBridge.renderFrame();
        }

        if (ImageAdjustmentOverlay.isVisible() && !CompatibilityGuard.shouldDisablePostProcess()) {
            var mc = Minecraft.getInstance();
            var ctx = new GuiGraphicsExtractor(mc,
                    lumen$getGameRenderState().guiRenderState,
                    mc.getWindow().getGuiScaledWidth(),
                    mc.getWindow().getGuiScaledHeight());
            ImageAdjustmentOverlay.extractOverlay(ctx);
        }
    }
}
