package com.luci.lumen.mixin.client;

import com.luci.lumen.gui.screen.HubScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VideoSettingsScreen.class)
public abstract class VideoSettingsScreenMixin {

    @Inject(method = "addOptions", at = @At("RETURN"))
    private void addLumenButton(CallbackInfo ci) {
        var list = ((OptionsSubScreenAccessor) this).getList();
        list.addHeader(Component.literal("Lumen"));
        list.addSmall(
                Button.builder(Component.literal("\u00a7bLumen Settings... \u00a77\u00bb"), btn -> {
                    Minecraft.getInstance().setScreen(new HubScreen((VideoSettingsScreen) (Object) this));
                }).width(310).build(),
                null
        );
    }
}
