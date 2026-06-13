package com.luci.lumen.gui.screen;

import com.luci.lumen.config.LumenConfig;
import com.luci.lumen.gui.widget.GlassDropdown;
import com.luci.lumen.gui.widget.GlassSlider;
import com.luci.lumen.gui.widget.GlassToggle;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class PerformanceScreen extends LumenScreen {
    private GlassDropdown presetDropdown;
    private GlassSlider renderScaleSlider;
    private GlassSlider rtQualitySlider;
    private GlassToggle adaptiveToggle;
    private GlassToggle skipPausedToggle;
    private final List<GlassSlider> sliders = new ArrayList<>();

    public PerformanceScreen(Screen parent) {
        super(parent, "Performance", "perf");
    }

    @Override
    protected void init() {
        super.init();
        var cfg = LumenConfig.get();
        int x = CONTENT_LEFT;
        int y = CONTENT_TOP + 45;

        presetDropdown = new GlassDropdown(x, y, 200, Component.literal("Preset"),
                List.of("Custom", "Battery", "Balanced", "Quality", "Extreme"));
        y += 40;

        renderScaleSlider = new GlassSlider(x, y, 200, Component.literal("Render Scale"), 0.25, 1.0, 0.05,
                v -> { cfg.renderScale = v.floatValue(); applyPreset(); },
                () -> (double) cfg.renderScale);
        y += 30;

        rtQualitySlider = new GlassSlider(x, y, 200, Component.literal("RT Quality"), 0, 2, 1,
                v -> { cfg.rtQuality = v.intValue(); applyPreset(); },
                () -> (double) cfg.rtQuality);
        y += 30;

        sliders.add(new GlassSlider(x, y, 200, Component.literal("Target FPS"), 0, 240, 10,
                v -> cfg.targetFps = v.intValue(), () -> (double) cfg.targetFps));
        y += 30;

        adaptiveToggle = new GlassToggle(x, y, Component.literal("Adaptive Performance"), cfg.adaptivePerf,
                () -> { cfg.adaptivePerf = adaptiveToggle.getState(); LumenConfig.save(); });
        y += 30;

        skipPausedToggle = new GlassToggle(x, y, Component.literal("Skip When Paused"), cfg.skipWhenPaused,
                () -> { cfg.skipWhenPaused = skipPausedToggle.getState(); LumenConfig.save(); });
    }

    private void applyPreset() {
        LumenConfig.save();
    }

    @Override
    protected void renderContent(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
        var font = minecraft.font;
        ctx.text(font, Component.literal("\u00a77Performance \u00a78| \u00a7fPresets & Scaling"),
                CONTENT_LEFT, CONTENT_TOP + 35, 0xFFFFFFFF);
        presetDropdown.render(ctx);
        renderScaleSlider.render(ctx);
        rtQualitySlider.render(ctx);
        for (var slider : sliders) slider.render(ctx);
        adaptiveToggle.render(ctx);
        skipPausedToggle.render(ctx);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        if (event.button() == 0) {
            double mx = event.x();
            double my = event.y();
            if (presetDropdown.mouseClicked(mx, my)) return true;
            if (renderScaleSlider.mouseClicked(mx, my)) return true;
            if (rtQualitySlider.mouseClicked(mx, my)) return true;
            if (adaptiveToggle.mouseClicked(mx, my)) return true;
            if (skipPausedToggle.mouseClicked(mx, my)) return true;
            for (var slider : sliders) {
                if (slider.mouseClicked(mx, my)) return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dx, double dy) {
        if (super.mouseDragged(event, dx, dy)) return true;
        if (event.button() == 0) {
            double mx = event.x();
            if (renderScaleSlider.isDragging()) {
                renderScaleSlider.mouseDragged(mx);
                return true;
            }
            if (rtQualitySlider.isDragging()) {
                rtQualitySlider.mouseDragged(mx);
                return true;
            }
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
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        if (super.mouseReleased(event)) return true;
        if (event.button() == 0) {
            if (renderScaleSlider.isDragging()) {
                renderScaleSlider.mouseReleased();
                renderScaleSlider.setDragging(false);
                return true;
            }
            if (rtQualitySlider.isDragging()) {
                rtQualitySlider.mouseReleased();
                rtQualitySlider.setDragging(false);
                return true;
            }
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
