package com.luci.lumen.gui.screen;

import com.luci.lumen.config.LumenConfig;
import com.luci.lumen.gui.widget.GlassSlider;
import com.luci.lumen.gui.widget.GlassToggle;
import com.luci.lumen.gui.widget.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class GraphicsScreen extends LumenScreen {
    private final List<GlassSlider> sliders = new ArrayList<>();
    private GlassToggle hdrToggle;
    private GlassToggle overlayToggle;

    public GraphicsScreen(Screen parent) {
        super(parent, "Graphics", "graphics");
    }

    @Override
    protected void init() {
        super.init();
        sliders.clear();
        int x = CONTENT_LEFT;
        int y = CONTENT_TOP + 45;
        var cfg = LumenConfig.get();

        sliders.add(new GlassSlider(x, y, 200, Component.literal("Brightness"), -1, 1, 0.01,
                v -> cfg.brightness = v.floatValue(), () -> (double) cfg.brightness)); y += 30;
        sliders.add(new GlassSlider(x, y, 200, Component.literal("Contrast"), -1, 1, 0.01,
                v -> cfg.contrast = v.floatValue(), () -> (double) cfg.contrast)); y += 30;
        sliders.add(new GlassSlider(x, y, 200, Component.literal("Saturation"), 0, 2, 0.01,
                v -> cfg.saturation = v.floatValue(), () -> (double) cfg.saturation)); y += 30;
        sliders.add(new GlassSlider(x, y, 200, Component.literal("Sharpness"), 0, 2, 0.01,
                v -> cfg.sharpness = v.floatValue(), () -> (double) cfg.sharpness)); y += 30;
        sliders.add(new GlassSlider(x, y, 200, Component.literal("Film Grain"), 0, 1, 0.01,
                v -> cfg.filmGrain = v.floatValue(), () -> (double) cfg.filmGrain)); y += 30;
        sliders.add(new GlassSlider(x, y, 200, Component.literal("Vignette"), 0, 1, 0.01,
                v -> cfg.vignette = v.floatValue(), () -> (double) cfg.vignette)); y += 30;

        y = CONTENT_TOP + 45;
        int x2 = x + 300;
        sliders.add(new GlassSlider(x2, y, 200, Component.literal("Exposure"), -2, 2, 0.1,
                v -> cfg.exposure = v.floatValue(), () -> (double) cfg.exposure)); y += 30;
        sliders.add(new GlassSlider(x2, y, 200, Component.literal("Shadows"), -1, 1, 0.01,
                v -> cfg.shadows = v.floatValue(), () -> (double) cfg.shadows)); y += 30;
        sliders.add(new GlassSlider(x2, y, 200, Component.literal("Highlights"), -1, 1, 0.01,
                v -> cfg.highlights = v.floatValue(), () -> (double) cfg.highlights)); y += 30;
        sliders.add(new GlassSlider(x2, y, 200, Component.literal("Whites"), -1, 1, 0.01,
                v -> cfg.whites = v.floatValue(), () -> (double) cfg.whites)); y += 30;
        sliders.add(new GlassSlider(x2, y, 200, Component.literal("Blacks"), -1, 1, 0.01,
                v -> cfg.blacks = v.floatValue(), () -> (double) cfg.blacks)); y += 30;
        sliders.add(new GlassSlider(x2, y, 200, Component.literal("Clarity"), -1, 1, 0.01,
                v -> cfg.clarity = v.floatValue(), () -> (double) cfg.clarity)); y += 30;

        int ty = height - 60;
        hdrToggle = new GlassToggle(CONTENT_LEFT, ty, Component.literal("HDR Mode"), cfg.hdrEnabled,
                () -> { cfg.hdrEnabled = hdrToggle.getState(); LumenConfig.save(); });
        overlayToggle = new GlassToggle(CONTENT_LEFT + 220, ty, Component.literal("Overlay Advanced"),
                cfg.overlayShowAdvanced,
                () -> { cfg.overlayShowAdvanced = overlayToggle.getState(); LumenConfig.save(); });
    }

    @Override
    protected void renderContent(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
        var font = minecraft.font;
        ctx.text(font, Component.literal("\u00a77Graphics \u00a78| \u00a7fImage Adjustments"),
                CONTENT_LEFT, CONTENT_TOP + 35, 0xFFFFFFFF);

        for (var slider : sliders) slider.render(ctx);
        hdrToggle.render(ctx);
        overlayToggle.render(ctx);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        if (event.button() == 0) {
            double mx = event.x();
            double my = event.y();
            if (hdrToggle.mouseClicked(mx, my)) return true;
            if (overlayToggle.mouseClicked(mx, my)) return true;
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
