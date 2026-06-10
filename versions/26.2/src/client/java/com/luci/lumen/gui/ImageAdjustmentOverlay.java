package com.luci.lumen.gui;

import com.luci.lumen.config.LumenConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class ImageAdjustmentOverlay {
    private static boolean visible = false;

    private static final List<SliderEntry> sliders = new ArrayList<>();
    private static int selectedIndex = 0;
    private static boolean adjusting = false;

    static {
        sliders.add(new SliderEntry("Brightness",  -1, 1, () -> LumenConfig.get().brightness, v -> LumenConfig.get().brightness = v));
        sliders.add(new SliderEntry("Contrast",    -1, 1, () -> LumenConfig.get().contrast, v -> LumenConfig.get().contrast = v));
        sliders.add(new SliderEntry("Saturation",   0, 2, () -> LumenConfig.get().saturation, v -> LumenConfig.get().saturation = v));
        sliders.add(new SliderEntry("Vibrance",    -1, 1, () -> LumenConfig.get().vibrance, v -> LumenConfig.get().vibrance = v));
        sliders.add(new SliderEntry("Temperature", -1, 1, () -> LumenConfig.get().temperature, v -> LumenConfig.get().temperature = v));
        sliders.add(new SliderEntry("Tint",        -1, 1, () -> LumenConfig.get().tint, v -> LumenConfig.get().tint = v));
        sliders.add(new SliderEntry("Exposure",    -2, 2, () -> LumenConfig.get().exposure, v -> LumenConfig.get().exposure = v));
        sliders.add(new SliderEntry("Sharpness",    0, 2, () -> LumenConfig.get().sharpness, v -> LumenConfig.get().sharpness = v));
        sliders.add(new SliderEntry("Film Grain",   0, 1, () -> LumenConfig.get().filmGrain, v -> LumenConfig.get().filmGrain = v));
        sliders.add(new SliderEntry("Vignette",     0, 1, () -> LumenConfig.get().vignette, v -> LumenConfig.get().vignette = v));
        sliders.add(new SliderEntry("Shadows",     -1, 1, () -> LumenConfig.get().shadows, v -> LumenConfig.get().shadows = v));
        sliders.add(new SliderEntry("Highlights",  -1, 1, () -> LumenConfig.get().highlights, v -> LumenConfig.get().highlights = v));
        sliders.add(new SliderEntry("Whites",      -1, 1, () -> LumenConfig.get().whites, v -> LumenConfig.get().whites = v));
        sliders.add(new SliderEntry("Blacks",      -1, 1, () -> LumenConfig.get().blacks, v -> LumenConfig.get().blacks = v));
        sliders.add(new SliderEntry("Clarity",     -1, 1, () -> LumenConfig.get().clarity, v -> LumenConfig.get().clarity = v));
        sliders.add(new SliderEntry("Dehaze",       0, 1, () -> LumenConfig.get().dehaze, v -> LumenConfig.get().dehaze = v));
    }

    public static boolean isVisible() { return visible; }
    public static void toggle() { visible = !visible; }

    public static boolean handleKey(int key, int action, int modifiers) {
        if (!visible || action != GLFW.GLFW_PRESS) return false;

        if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
            int dir = (key == GLFW.GLFW_KEY_UP) ? -1 : 1;
            selectedIndex = Math.max(0, Math.min(sliders.size() - 1, selectedIndex + dir));
            adjusting = false;
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_SPACE) {
            adjusting = !adjusting;
            return true;
        }
        if (adjusting && (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT)) {
            SliderEntry s = sliders.get(selectedIndex);
            float dir = key == GLFW.GLFW_KEY_RIGHT ? 0.01f : -0.01f;
            if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0) dir *= 5;
            if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0) dir *= 0.2f;
            float newVal = Math.max(s.min, Math.min(s.max, s.get() + dir));
            s.setter.accept(newVal);
            return true;
        }
        if (key == GLFW.GLFW_KEY_R) {
            for (var s : sliders) s.setter.accept(0.0f);
            LumenConfig.get().saturation = 1.0f;
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            visible = false;
            return true;
        }
        return false;
    }

    public static void extractOverlay(GuiGraphicsExtractor ctx) {
        if (!visible) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) return;

        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        int panelW = 280;
        int panelH = sliders.size() * 18 + 40;
        int px = w - panelW - 10;
        int py = (h - panelH) / 2;

        ctx.fill(px - 2, py - 2, px + panelW + 2, py + panelH + 2, 0xCC111111);
        ctx.fill(px - 1, py - 1, px + panelW + 1, py + panelH + 1, 0xCC222222);

        ctx.text(mc.font, Component.literal("\u00a7bImage Adjustments  [\u00a77F6\u00a7b]"), px + 10, py + 5, 0xFFFFFF);
        ctx.text(mc.font, Component.literal("\u00a77\u2191\u2193 select  \u2190\u2192 adjust  Enter toggle  R reset  Esc close"), px + 10, py + 18, 0x888888);

        int sy = py + 34;
        for (int i = 0; i < sliders.size(); i++) {
            SliderEntry s = sliders.get(i);
            boolean sel = i == selectedIndex;
            int color = sel ? (adjusting ? 0xFFFFAA00 : 0xFFFFFF55) : 0xFFCCCCCC;

            float pct = (s.get() - s.min) / (s.max - s.min);
            int barX = px + 120;
            int barW = panelW - 130;
            int barY = sy + 3;

            ctx.text(mc.font, Component.literal((sel ? "\u00bb " : "  ") + s.name), px + 8, sy, color);
            ctx.fill(barX, barY, barX + barW, barY + 8, 0xFF444444);
            ctx.fill(barX, barY, barX + (int) (barW * pct), barY + 8, sel ? (adjusting ? 0xFFFFAA00 : 0xFFFFFF55) : 0xFF888888);

            String valStr = String.format("%.2f", s.get());
            ctx.text(mc.font, Component.literal(valStr), barX + barW + 4, sy, 0xFF888888);
            sy += 18;
        }

        ctx.text(mc.font,
                Component.literal("\u00a77Shift=fast  Ctrl=fine  R=reset all  Esc=close"),
                px + 10, sy + 4, 0x666666);
    }

    private static class SliderEntry {
        final String name;
        final float min, max;
        final Supplier<Float> get;
        final Consumer<Float> setter;

        SliderEntry(String name, float min, float max, Supplier<Float> get, Consumer<Float> setter) {
            this.name = name; this.min = min; this.max = max; this.get = get; this.setter = setter;
        }
        float get() { return get.get(); }
    }
}
