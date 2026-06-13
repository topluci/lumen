package com.luci.lumen.mixin.client;

import com.luci.lumen.vk.RtOverlayRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class InGameHudMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void afterExtractRenderState(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        var texId = RtOverlayRenderer.getTextureId();
        if (texId != null) {
            var win = Minecraft.getInstance().getWindow();
            guiGraphics.blit(texId, 0, 0, win.getGuiScaledWidth(), win.getGuiScaledHeight(),
                    0.0f, 0.0f, 1.0f, 1.0f);
        }
    }
}
