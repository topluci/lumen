package com.luci.lumen.vk;

import com.luci.lumen.LumenInit;
import com.luci.lumen.config.LumenConfig;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

public class RtOverlayRenderer {
    private static DynamicTexture rtTexture;
    private static Identifier rtTextureId;
    private static NativeImage currentImage;
    private static int lastW, lastH;

    private static final int RT_WIDTH = 800;
    private static final int RT_HEIGHT = 600;

    public static void captureAndUpdate() {
        if (!LumenNativeBridge.isAvailable()) return;
        int[] pixels = LumenNativeBridge.nativeReadbackPixels();
        if (pixels == null || pixels.length == 0) return;

        if (LumenConfig.get().denoiserEnabled) {
            LumenNativeBridge.nativeDenoiseImage(pixels, RT_WIDTH, RT_HEIGHT);
        }

        if (currentImage == null || lastW != RT_WIDTH || lastH != RT_HEIGHT) {
            if (currentImage != null) currentImage.close();
            currentImage = new NativeImage(RT_WIDTH, RT_HEIGHT, false);
            lastW = RT_WIDTH;
            lastH = RT_HEIGHT;
        }

        for (int i = 0; i < pixels.length; i++) {
            int p = pixels[i];
            int r = (p >> 16) & 0xFF;
            int b = (p >> 0) & 0xFF;
            int swapped = (p & 0xFF00FF00) | (r & 0xFF) | (b << 16);
            currentImage.setPixel(i % RT_WIDTH, i / RT_WIDTH, swapped);
        }

        if (rtTexture == null) {
            rtTexture = new DynamicTexture(() -> "lumen_rt_output", currentImage);
            rtTextureId = Identifier.fromNamespaceAndPath("lumen", "rt_output");
            Minecraft.getInstance().getTextureManager().register(rtTextureId, rtTexture);
            LumenInit.LOGGER.info("[Lumen] RT overlay texture registered");
        } else {
            rtTexture.upload();
        }
    }

    public static Identifier getTextureId() {
        return rtTextureId;
    }

    public static void cleanup() {
        if (rtTexture != null) { rtTexture.close(); rtTexture = null; }
        if (currentImage != null) { currentImage.close(); currentImage = null; }
        rtTextureId = null;
    }
}
