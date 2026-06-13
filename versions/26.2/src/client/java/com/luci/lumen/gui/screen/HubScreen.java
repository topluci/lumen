package com.luci.lumen.gui.screen;

import com.luci.lumen.api.renderer.RendererManager;
import com.luci.lumen.config.LumenConfig;
import com.luci.lumen.shaderpack.ShaderPackManager;
import com.luci.lumen.gui.widget.GlassButton;
import com.luci.lumen.gui.widget.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class HubScreen extends LumenScreen {
    private static final String[] CATEGORIES = {
            "Graphics", "Lighting", "Shaders",
            "Performance", "Accessibility", "Advanced",
            "System"
    };
    private static final String[] CAT_KEYS = {
            "graphics", "lighting", "shaders",
            "perf", "access", "advanced",
            "system"
    };
    private static final String[] PRESETS = {"Battery", "Balanced", "Quality", "Extreme"};
    private static final String[] UPSCALERS = {"Auto", "DLSS", "FSR", "XeSS"};

    private final List<GlassButton> hubButtons = new ArrayList<>();
    private boolean advancedSettingsUi;
    private int selectedUpscaler = 0;

    public HubScreen(Screen parent) {
        super(parent, "Lumen Hub", "hub");
        var cfg = LumenConfig.get();
        this.advancedSettingsUi = cfg.advancedSettingsUi;
        this.selectedUpscaler = cfg.upscalingMode;
    }

    @Override
    protected void init() {
        super.init();
        rebuildHubButtons();
    }

    private void rebuildHubButtons() {
        hubButtons.clear();
        var cfg = LumenConfig.get();
        int cx = CONTENT_LEFT + 20;
        int bw = 200;
        int bh = 22;
        int gap = 8;
        int y = CONTENT_TOP + 50;

        if (!advancedSettingsUi) {
            hubButtons.add(new GlassButton(cx, y, bw, bh,
                    Component.literal("Advanced UI"), () -> {
                advancedSettingsUi = true;
                cfg.advancedSettingsUi = true;
                LumenConfig.save();
                rebuildHubButtons();
            }));
            y += bh + gap;

            hubButtons.add(new GlassButton(cx, y, bw, bh,
                    Component.literal("Reload"), () -> {
                ShaderPackManager.refreshNativeShader();
            }));
            y += bh + gap;

            int presetIdx = cfg.performancePreset >= 0 && cfg.performancePreset < PRESETS.length
                    ? cfg.performancePreset : -1;
            String presetLabel = presetIdx >= 0 ? PRESETS[presetIdx] : "Custom";
            hubButtons.add(new GlassButton(cx, y, bw, bh,
                    Component.literal("Preset: " + presetLabel), () -> {
                int next = (presetIdx + 1) % PRESETS.length;
                cfg.performancePreset = next;
                applyPreset(next);
                LumenConfig.save();
                rebuildHubButtons();
            }));
            y += bh + gap;

            hubButtons.add(new GlassButton(cx, y, bw, bh,
                    Component.literal("Shaderpacks..."), () -> {
                Minecraft.getInstance().setScreenAndShow(new ShaderScreen(null));
            }));
            y += bh + gap;

            String upscalerLabel = selectedUpscaler >= 0 && selectedUpscaler < UPSCALERS.length
                    ? UPSCALERS[selectedUpscaler] : "Auto";
            hubButtons.add(new GlassButton(cx, y, bw, bh,
                    Component.literal("Upscaler: " + upscalerLabel), () -> {
                selectedUpscaler = (selectedUpscaler + 1) % UPSCALERS.length;
                cfg.upscalingMode = selectedUpscaler;
                cfg.upscalingEnabled = true;
                LumenConfig.save();
                rebuildHubButtons();
            }));
            y += bh + gap;

            hubButtons.add(new GlassButton(cx, y, bw, bh,
                    Component.literal("HDR: " + (cfg.hdrEnabled ? "ON" : "OFF")), () -> {
                cfg.hdrEnabled = !cfg.hdrEnabled;
                LumenConfig.save();
                rebuildHubButtons();
            }));
        } else {
            hubButtons.add(new GlassButton(cx, y, bw, bh,
                    Component.literal("Simple UI"), () -> {
                advancedSettingsUi = false;
                cfg.advancedSettingsUi = false;
                LumenConfig.save();
                rebuildHubButtons();
            }));
        }
    }

    private void applyPreset(int preset) {
        var cfg = LumenConfig.get();
        switch (preset) {
            case 0 -> {
                cfg.renderScale = 0.5f;
                cfg.rtQuality = 0;
                cfg.adaptivePerf = true;
            }
            case 1 -> {
                cfg.renderScale = 0.75f;
                cfg.rtQuality = 1;
                cfg.adaptivePerf = true;
            }
            case 2 -> {
                cfg.renderScale = 1.0f;
                cfg.rtQuality = 1;
                cfg.adaptivePerf = true;
            }
            case 3 -> {
                cfg.renderScale = 1.0f;
                cfg.rtQuality = 2;
                cfg.adaptivePerf = false;
            }
        }
    }

    @Override
    public void removed() {
        LumenConfig.save();
        super.removed();
    }

    @Override
    protected void renderContent(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
        var font = minecraft.font;

        if (!advancedSettingsUi) {
            String title = "\u00a7l\u00a7fLumen Graphics Platform";
            ctx.text(font, Component.literal(title),
                    width / 2 - font.width(Component.literal(title)) / 2,
                    CONTENT_TOP + 25, 0xFFFFFFFF);

            String status = "\u00a77" + RendererManager.get().getStatusMessage();
            ctx.text(font, Component.literal(status),
                    width / 2 - font.width(Component.literal(status)) / 2,
                    CONTENT_TOP + 39, 0xFFFFFFFF);

            for (var btn : hubButtons) {
                btn.render(ctx, mx, my);
            }

            String help = "\u00a77Drop .zip shaderpacks on Shaderpacks, then Reload.";
            ctx.text(font, Component.literal(help),
                    width / 2 - font.width(Component.literal(help)) / 2,
                    CONTENT_TOP + 235, Theme.TEXT_PRIMARY);
        } else {
            ctx.text(font, Component.literal("\u00a7l\u00a7fRadiance-style Path Tracing Controls"),
                    CONTENT_LEFT, CONTENT_TOP + 8, 0xFFFFFFFF);
            ctx.text(font, Component.literal("\u00a77" + RendererManager.get().getStatusMessage()),
                    CONTENT_LEFT, CONTENT_TOP + 24, 0xFFFFFFFF);

            for (var btn : hubButtons) {
                btn.render(ctx, mx, my);
            }

            int cols = 3;
            int gap = 12;
            int cardW = 170;
            int cardH = 70;
            int totalW = cols * cardW + (cols - 1) * gap;
            int startX = CONTENT_LEFT + (width - CONTENT_LEFT - totalW) / 2;
            int startY = height / 2 - 40;

            for (int i = 0; i < CATEGORIES.length; i++) {
                int col = i % cols;
                int row = i / cols;
                int x = startX + col * (cardW + gap);
                int y = startY + row * (cardH + gap);
                String cat = CATEGORIES[i];

                boolean hovered = mx >= x && mx <= x + cardW && my >= y && my <= y + cardH;
                ctx.fill(x, y, x + cardW, y + cardH,
                        hovered ? Theme.glassWithAlpha(0.18f) : Theme.glassWithAlpha(0.10f));
                if (hovered) {
                    ctx.fill(x, y, x + cardW, y + 1, Theme.HIGHLIGHT);
                }
                ctx.fill(x, y, x + 1, y + cardH, Theme.BORDER);
                ctx.fill(x + cardW - 1, y, x + cardW, y + cardH, Theme.BORDER);

                ctx.text(font, Component.literal(cat), x + 12, y + cardH / 2 - 4, 0xFFFFFFFF);
            }
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        if (event.button() == 0) {
            double mx = event.x();
            double my = event.y();

            for (var btn : hubButtons) {
                if (btn.mouseClicked(mx, my)) return true;
            }

            if (advancedSettingsUi) {
                int cols = 3;
                int gap = 12;
                int cardW = 170;
                int cardH = 70;
                int totalW = cols * cardW + (cols - 1) * gap;
                int startX = CONTENT_LEFT + (width - CONTENT_LEFT - totalW) / 2;
                int startY = height / 2 - 40;

                for (int i = 0; i < CATEGORIES.length; i++) {
                    int col = i % cols;
                    int row = i / cols;
                    int x = startX + col * (cardW + gap);
                    int y = startY + row * (cardH + gap);
                    String catKey = CAT_KEYS[i];

                    if (mx >= x && mx <= x + cardW && my >= y && my <= y + cardH) {
                        navigateTo(catKey);
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }
}
