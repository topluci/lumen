package com.luci.lumen.gui.screen;

import com.luci.lumen.config.LumenConfig;
import com.luci.lumen.gui.widget.GlassSlider;
import com.luci.lumen.gui.widget.GlassToggle;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class LightingScreen extends LumenScreen {
    private final List<GlassSlider> sliders = new ArrayList<>();
    private GlassToggle rtToggle;

    public LightingScreen(Screen parent) {
        super(parent, "Lighting", "lighting");
    }

    @Override
    protected void init() {
        super.init();
        sliders.clear();
        int x = CONTENT_LEFT;
        int y = CONTENT_TOP + 45;
        var cfg = LumenConfig.get();

        sliders.add(new GlassSlider(x, y, 200, Component.literal("Temperature"), -1, 1, 0.01,
                v -> cfg.temperature = v.floatValue(), () -> (double) cfg.temperature)); y += 30;
        sliders.add(new GlassSlider(x, y, 200, Component.literal("Tint"), -1, 1, 0.01,
                v -> cfg.tint = v.floatValue(), () -> (double) cfg.tint)); y += 30;
        sliders.add(new GlassSlider(x, y, 200, Component.literal("Dehaze"), 0, 1, 0.01,
                v -> cfg.dehaze = v.floatValue(), () -> (double) cfg.dehaze)); y += 30;

        y = CONTENT_TOP + 45;
        int x2 = x + 300;
        sliders.add(new GlassSlider(x2, y, 200, Component.literal("Vibrance"), -1, 1, 0.01,
                v -> cfg.vibrance = v.floatValue(), () -> (double) cfg.vibrance)); y += 30;

        rtToggle = new GlassToggle(CONTENT_LEFT, height - 60, Component.literal("HDR Mode"), cfg.hdrEnabled,
                () -> { cfg.hdrEnabled = rtToggle.getState(); LumenConfig.save(); });
    }

    @Override
    protected void renderContent(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
        var font = minecraft.font;
        ctx.text(font, Component.literal("\u00a77Lighting \u00a78| \u00a7fColor & Atmosphere"),
                CONTENT_LEFT, CONTENT_TOP + 35, 0xFFFFFFFF);
        for (var slider : sliders) slider.render(ctx);
        rtToggle.render(ctx);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        if (event.button() == 0) {
            double mx = event.x();
            double my = event.y();
            if (rtToggle.mouseClicked(mx, my)) return true;
            for (var slider : sliders) {
                if (slider.mouseClicked(mx, my)) return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double deltaX, double deltaY) {
        if (super.mouseDragged(mx, my, button, deltaX, deltaY)) return true;
        if (button == 0) {
            for (var slider : sliders) {
                if (slider.isDragging()) {
                    slider.mouseDragged(mx);
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (super.mouseReleased(mx, my, button)) return true;
        if (button == 0) {
            for (var slider : sliders) {
                if (slider.isDragging()) {
                    slider.mouseReleased();
                    slider.setDragging(false);
                    return true;
                }
            }
        }
        return false;
    }
}
