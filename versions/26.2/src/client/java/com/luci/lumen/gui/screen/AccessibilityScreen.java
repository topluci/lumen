package com.luci.lumen.gui.screen;

import com.luci.lumen.config.LumenConfig;
import com.luci.lumen.gui.widget.GlassSlider;
import com.luci.lumen.gui.widget.GlassToggle;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class AccessibilityScreen extends LumenScreen {
    private GlassToggle experimentalToggle;
    private final List<GlassSlider> sliders = new ArrayList<>();

    public AccessibilityScreen(Screen parent) {
        super(parent, "Accessibility", "access");
    }

    @Override
    protected void init() {
        super.init();
        var cfg = LumenConfig.get();
        int x = CONTENT_LEFT;
        int y = CONTENT_TOP + 45;

        experimentalToggle = new GlassToggle(x, y, Component.literal("Show Experimental Warning"), cfg.showExperimentalWarning,
                () -> { cfg.showExperimentalWarning = experimentalToggle.getState(); LumenConfig.save(); });
        y += 30;

        sliders.add(new GlassSlider(x, y, 200, Component.literal("Overlay Opacity"), 0.1, 1.0, 0.05,
                v -> cfg.overlayOpacity = v.floatValue(), () -> (double) cfg.overlayOpacity));
    }

    @Override
    protected void renderContent(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
        var font = minecraft.font;
        ctx.text(font, Component.literal("\u00a77Accessibility \u00a78| \u00a7fDisplay Options"),
                CONTENT_LEFT, CONTENT_TOP + 35, 0xFFFFFFFF);
        experimentalToggle.render(ctx);
        for (var slider : sliders) slider.render(ctx);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        if (event.button() == 0) {
            double mx = event.x();
            double my = event.y();
            if (experimentalToggle.mouseClicked(mx, my)) return true;
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
