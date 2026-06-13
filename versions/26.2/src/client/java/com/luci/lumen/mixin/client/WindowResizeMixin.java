package com.luci.lumen.mixin.client;

import com.luci.lumen.vk.LumenNativeBridge;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.platform.Window;

@Mixin(Window.class)
public abstract class WindowResizeMixin {

    @Inject(method = "onFramebufferResize", at = @At("TAIL"))
    private void onResize(long handle, int framebufferWidth, int framebufferHeight, CallbackInfo ci) {
        if (LumenNativeBridge.isAvailable()) {
            LumenNativeBridge.nativeResize(framebufferWidth, framebufferHeight);
        }
    }
}
