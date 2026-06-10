package com.luci.lumen.config;

import com.luci.lumen.LumenInit;
import com.luci.lumen.vk.LumenNativeBridge;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class LumenConfigScreen extends Screen {
    private final Screen parent;
    private String selectedCategory = "Post-Process";
    private boolean showDangerous = false;
    private List<String> discoveredPacks;

    public LumenConfigScreen(Screen parent) {
        super(Component.literal("Lumen Settings \u00a7c[Experimental]"));
        this.parent = parent;
    }

    @Override
    protected void rebuildWidgets() {
        int cx = width / 2;
        int left = cx - 200;
        int right = cx + 10;
        int y0 = 40;
        int[] y = {y0};

        addRenderableWidget(Button.builder(Component.literal("Post-Process"), b -> selectedCategory = "Post-Process")
                .bounds(left - 110, y0 - 20, 90, 20).build());
        addRenderableWidget(Button.builder(Component.literal("HDR"), b -> selectedCategory = "HDR")
                .bounds(left - 110, y0 - 20 + 22, 90, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Frame Gen"), b -> selectedCategory = "Frame Gen")
                .bounds(left - 110, y0 - 20 + 44, 90, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Upscaling"), b -> selectedCategory = "Upscaling")
                .bounds(left - 110, y0 - 20 + 66, 90, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Keys"), b -> selectedCategory = "Keys")
                .bounds(left - 110, y0 - 20 + 88, 90, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Shader"), b -> selectedCategory = "Shader")
                .bounds(left - 110, y0 - 20 + 110, 90, 20).build());

        addRenderableWidget(Button.builder(
                Component.literal(showDangerous ? "\u00a7cHide Advanced" : "\u00a77Show Advanced"),
                b -> { showDangerous = !showDangerous; clearWidgets(); rebuildWidgets(); })
                .bounds(right + 100, y0 - 20, 100, 20).build());

        var cfg = LumenConfig.get();

        if (discoveredPacks == null) {
            discoveredPacks = discoverShaderPacks();
        }

        switch (selectedCategory) {
            case "Post-Process" -> {
                y[0] = addSlider(left, y[0], "Brightness", cfg.brightness, -1.0, 1.0);
                y[0] = addSlider(left, y[0], "Contrast", cfg.contrast, -1.0, 1.0);
                y[0] = addSlider(left, y[0], "Saturation", cfg.saturation, 0.0, 2.0);
                y[0] = addSlider(left, y[0], "Vibrance", cfg.vibrance, -1.0, 1.0);
                y[0] = addSlider(left, y[0], "Temperature", cfg.temperature, -1.0, 1.0);
                y[0] = addSlider(left, y[0], "Tint", cfg.tint, -1.0, 1.0);
                y[0] = addSlider(left, y[0], "Exposure", cfg.exposure, -2.0, 2.0);
                y[0] = addSlider(left, y[0], "Sharpness", cfg.sharpness, 0.0, 2.0);
                y[0] = addSlider(left, y[0], "Film Grain", cfg.filmGrain, 0.0, 1.0);
                y[0] = addSlider(left, y[0], "Vignette", cfg.vignette, 0.0, 1.0);
                y[0] = addSlider(left, y[0], "Shadows", cfg.shadows, -1.0, 1.0);
                y[0] = addSlider(left, y[0], "Highlights", cfg.highlights, -1.0, 1.0);
                y[0] = addSlider(left, y[0], "Whites", cfg.whites, -1.0, 1.0);
                y[0] = addSlider(left, y[0], "Blacks", cfg.blacks, -1.0, 1.0);
                y[0] = addSlider(left, y[0], "Clarity", cfg.clarity, -1.0, 1.0);
                y[0] = addSlider(left, y[0], "Dehaze", cfg.dehaze, 0.0, 1.0);
                if (showDangerous) {
                    y[0] = addIntSlider(left, y[0], "\u00a7cTonemap Curve", cfg.tonemapCurve, 0, 3);
                }
            }
            case "HDR" -> {
                addToggle(left, y[0], "HDR Enabled", cfg.hdrEnabled);
                y[0] += 24;
                y[0] = addSlider(left, y[0], "Paper White (nits)", cfg.hdrPaperWhiteNits, 80.0, 500.0);
                y[0] = addSlider(left, y[0], "Max Luminance", cfg.hdrMaxLuminance, 400.0, 10000.0);
            }
            case "Frame Gen" -> {
                if (showDangerous) {
                    addToggle(left, y[0], "\u00a7cEnable Frame Gen", cfg.frameGenEnabled);
                    y[0] += 24;
                    y[0] = addIntSlider(left, y[0], "\u00a7cMode (0=auto)", cfg.frameGenMode, 0, 3);
                    y[0] = addIntSlider(left, y[0], "\u00a7cTarget FPS", cfg.frameGenTargetFps, 0, 240);
                } else {
                    y[0] = addNote(left, y[0], "\u00a7c\u00a7l[ADVANCED] Toggle \"Show Advanced\"");
                }
            }
            case "Upscaling" -> {
                if (showDangerous) {
                    addToggle(left, y[0], "\u00a7cEnable Upscaling", cfg.upscalingEnabled);
                    y[0] += 24;
                    y[0] = addIntSlider(left, y[0], "\u00a7cMode (0=auto)", cfg.upscalingMode, 0, 3);
                    y[0] = addSlider(left, y[0], "\u00a7cQuality", cfg.upscalingQuality, 0.0, 1.0);
                } else {
                    y[0] = addNote(left, y[0], "\u00a7c\u00a7l[ADVANCED] Toggle \"Show Advanced\"");
                }
            }
            case "Keys" -> {
                addKeyField(left, y[0], "Toggle Overlay", cfg.toggleOverlayKey);
                y[0] += 24;
                addKeyField(left, y[0], "Screenshot Key", cfg.screenshotKey);
                y[0] += 24;
                addKeyField(left, y[0], "Reset Image Key", cfg.resetImageKey);
                y[0] += 24;
                y[0] = addSlider(left, y[0], "Overlay Opacity", cfg.overlayOpacity, 0.2, 1.0);
            }
            case "Shader" -> {
                addRenderableWidget(Button.builder(
                        Component.literal("\u00a76Current: " + cfg.shaderPack),
                        b -> {}).bounds(left, y[0], 230, 20).build());
                y[0] += 24;
                for (String pack : discoveredPacks) {
                    boolean active = pack.equals(cfg.shaderPack);
                    String prefix = active ? "\u00a7a\u25b6 " : "  ";
                    addRenderableWidget(Button.builder(
                            Component.literal(prefix + pack),
                            b -> { cfg.shaderPack = pack; clearWidgets(); rebuildWidgets(); }
                    ).bounds(left + 10, y[0], 200, 20).build());
                    y[0] += 22;
                }
                y[0] = addNote(left, y[0], "\u00a77Place packs in .minecraft/shaderpacks/");
                addRenderableWidget(Button.builder(
                        Component.literal("Rescan"),
                        b -> { discoveredPacks = discoverShaderPacks(); clearWidgets(); rebuildWidgets(); }
                ).bounds(left, y[0], 100, 20).build());
                addRenderableWidget(Button.builder(
                        Component.literal("\u00a7cReload Shader"),
                        b -> {
                            if (!"builtin".equals(cfg.shaderPack)) {
                                LumenNativeBridge.nativeLoadShaderPack(
                                        FabricLoader.getInstance().getGameDir()
                                                .resolve("shaderpacks").resolve(cfg.shaderPack).toString());
                            }
                        }
                ).bounds(left + 110, y[0], 120, 20).build());
                y[0] += 24;
            }
        }

        addRenderableWidget(Button.builder(Component.literal("Reset All"), b -> {
            LumenConfig.get();
            clearWidgets(); rebuildWidgets();
        }).bounds(left, height - 30, 100, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> {
            LumenConfig.save();
            onClose();
        }).bounds(width / 2 - 50, height - 30, 100, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
                .bounds(left + 300, height - 30, 100, 20).build());
    }

    private int addSlider(int x, int y, String label, float value, double min, double max) {
        float fMin = (float) min, fMax = (float) max;
        double pct = (value - fMin) / (fMax - fMin);
        addRenderableWidget(new AbstractSliderButton(x, y, 200, 20, Component.literal(label + ": " + String.format("%.2f", value)), pct) {
            @Override
            protected void updateMessage() {
                float val = fMin + (float) ((fMax - fMin) * this.value);
                setMessage(Component.literal(label + ": " + String.format("%.2f", val)));
            }
            @Override
            protected void applyValue() {
                float val = fMin + (float) ((fMax - fMin) * this.value);
                if (label.contains("Brightness")) LumenConfig.get().brightness = val;
                else if (label.contains("Contrast")) LumenConfig.get().contrast = val;
                else if (label.contains("Saturation")) LumenConfig.get().saturation = val;
                else if (label.contains("Vibrance")) LumenConfig.get().vibrance = val;
                else if (label.contains("Temperature")) LumenConfig.get().temperature = val;
                else if (label.contains("Tint")) LumenConfig.get().tint = val;
                else if (label.contains("Exposure")) LumenConfig.get().exposure = val;
                else if (label.contains("Sharpness")) LumenConfig.get().sharpness = val;
                else if (label.contains("Film Grain")) LumenConfig.get().filmGrain = val;
                else if (label.contains("Vignette")) LumenConfig.get().vignette = val;
                else if (label.contains("Shadows")) LumenConfig.get().shadows = val;
                else if (label.contains("Highlights")) LumenConfig.get().highlights = val;
                else if (label.contains("Whites")) LumenConfig.get().whites = val;
                else if (label.contains("Blacks")) LumenConfig.get().blacks = val;
                else if (label.contains("Clarity")) LumenConfig.get().clarity = val;
                else if (label.contains("Dehaze")) LumenConfig.get().dehaze = val;
                else if (label.contains("Paper White")) LumenConfig.get().hdrPaperWhiteNits = val;
                else if (label.contains("Max Luminance")) LumenConfig.get().hdrMaxLuminance = val;
                else if (label.contains("Quality")) LumenConfig.get().upscalingQuality = val;
                else if (label.contains("Opacity")) LumenConfig.get().overlayOpacity = val;
            }
        });
        return y + 24;
    }

    private int addIntSlider(int x, int y, String label, int value, int min, int max) {
        double pct = (double) (value - min) / (max - min);
        int fMin = min, fMax = max;
        addRenderableWidget(new AbstractSliderButton(x, y, 200, 20, Component.literal(label + ": " + value), pct) {
            @Override
            protected void updateMessage() {
                int val = fMin + (int) ((fMax - fMin) * this.value);
                setMessage(Component.literal(label + ": " + val));
            }
            @Override
            protected void applyValue() {
                int val = fMin + (int) ((fMax - fMin) * this.value);
                if (label.contains("Tonemap")) LumenConfig.get().tonemapCurve = val;
                else if (label.contains("Mode")) LumenConfig.get().frameGenMode = val;
                else if (label.contains("Target FPS")) LumenConfig.get().frameGenTargetFps = val;
                else if (label.contains("Upscaling Mode")) LumenConfig.get().upscalingMode = val;
            }
        });
        return y + 24;
    }

    private void addToggle(int x, int y, String label, boolean value) {
        addRenderableWidget(Button.builder(
                Component.literal(label + ": " + (value ? "\u00a7aON" : "\u00a77OFF")),
                b -> {
                    if (label.contains("HDR")) LumenConfig.get().hdrEnabled = !value;
                    else if (label.contains("Frame Gen")) LumenConfig.get().frameGenEnabled = !value;
                    else if (label.contains("Upscaling")) LumenConfig.get().upscalingEnabled = !value;
                    clearWidgets(); rebuildWidgets();
                }).bounds(x, y, 230, 20).build());
    }

    private int addNote(int x, int y, String text) {
        addRenderableWidget(Button.builder(Component.literal(text), b -> {})
                .bounds(x, y, 300, 20).build());
        return y + 24;
    }

    private List<String> discoverShaderPacks() {
        List<String> packs = new ArrayList<>();
        packs.add("builtin");
        Path dir = FabricLoader.getInstance().getGameDir().resolve("shaderpacks");
        if (Files.isDirectory(dir)) {
            try (Stream<Path> stream = Files.list(dir)) {
                stream.filter(Files::isDirectory)
                      .map(p -> p.getFileName().toString())
                      .sorted()
                      .forEach(packs::add);
            } catch (IOException e) {
                LumenInit.LOGGER.warn("[Lumen] Failed to scan shaderpacks dir", e);
            }
        }
        return packs;
    }

    private void addKeyField(int x, int y, String label, String current) {
        addRenderableWidget(Button.builder(
                Component.literal(label + ": " + current),
                b -> {}).bounds(x, y, 220, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
        extractBackground(ctx, mx, my, delta);
        ctx.centeredText(getFont(), getTitle(), width / 2, 15, 0xFFFFFF);
        ctx.text(getFont(), Component.literal("\u00a77" + selectedCategory + " settings"), width / 2 - 200, 60, 0x888888, false);

        var rendererStatus = com.luci.lumen.vk.VulkanDeviceInterceptor.getStatusMessage();
        var statusColor = com.luci.lumen.vk.VulkanDeviceInterceptor.isVulkanAvailable() ? 0x55FF55 : 0xFF5555;
        ctx.text(getFont(), Component.literal("\u00a77Renderer: " + rendererStatus), width / 2 + 50, 60, statusColor, false);

        super.extractRenderState(ctx, mx, my, delta);
    }

    @Override
    public void onClose() {
        LumenConfig.save();
        Minecraft.getInstance().setScreenAndShow(parent);
    }
}
