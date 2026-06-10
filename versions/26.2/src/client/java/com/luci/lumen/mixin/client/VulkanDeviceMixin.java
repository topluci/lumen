package com.luci.lumen.mixin.client;

import com.luci.lumen.vk.VulkanDeviceInterceptor;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.mojang.blaze3d.vulkan.VulkanInstance;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.lwjgl.vulkan.VkDevice;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(VulkanDevice.class)
public class VulkanDeviceMixin {
    @Shadow private VulkanInstance instance;
    @Shadow private VkDevice vkDevice;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onVulkanDeviceConstructed(CallbackInfo ci) {
        if (this.instance != null && this.vkDevice != null) {
            VulkanDeviceInterceptor.onVulkanDeviceCreated(
                this.instance.vkInstance().address(),
                this.vkDevice.address()
            );
        }
    }
}
