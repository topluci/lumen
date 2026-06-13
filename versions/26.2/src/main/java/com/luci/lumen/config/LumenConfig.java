package com.luci.lumen.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.luci.lumen.LumenInit;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class LumenConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static LumenConfig INSTANCE;
    private static Path CONFIG_PATH;

    // General
    public boolean enabled = true;
    public boolean showExperimentalWarning = true;

    // Advanced — Post-Process Image Adjustments
    public float brightness = 0.0f;     // -1..1
    public float contrast = 0.0f;       // -1..1
    public float saturation = 1.0f;     // 0..2
    public float vibrance = 0.0f;       // -1..1
    public float temperature = 0.0f;    // -1..1 (blue← →orange)
    public float tint = 0.0f;           // -1..1 (green← →magenta)
    public float sharpness = 0.0f;      // 0..2
    public float filmGrain = 0.0f;      // 0..1
    public float vignette = 0.0f;       // 0..1
    public float exposure = 0.0f;       // -2..2 EV
    public float shadows = 0.0f;        // -1..1
    public float highlights = 0.0f;     // -1..1
    public float whites = 0.0f;         // -1..1
    public float blacks = 0.0f;         // -1..1
    public float clarity = 0.0f;        // -1..1 (local contrast)
    public float dehaze = 0.0f;         // 0..1
    public int tonemapCurve = 0;        // 0=ACES, 1=Filmic, 2=Reinhard, 3=Uncharted2

    // Advanced — HDR
    public boolean hdrEnabled = false;
    public float hdrPaperWhiteNits = 200.0f;
    public float hdrMaxLuminance = 1000.0f;

    // Experimental — Frame Generation
    public boolean frameGenEnabled = false;
    public int frameGenMode = 0;        // 0=auto, 1=NVIDIA DLSS FG, 2=AMD FMF, 3=Intel XeSS FG
    public int frameGenTargetFps = 0;   // 0=auto

    // Experimental — Upscaling / Super Resolution
    public boolean upscalingEnabled = false;
    public int upscalingMode = 0;       // 0=auto, 1=DLSS, 2=FSR, 3=XeSS
    public float upscalingQuality = 0.5f; // 0=performance..1=quality

    // Performance
    public int performancePreset = -1;   // -1=custom, 0=Battery, 1=Balanced, 2=Quality, 3=Extreme
    public int targetFps = 0;            // 0=auto (monitor refresh)
    public int frameSkip = 0;            // 0=off, 1=skip every other frame, etc.
    public float renderScale = 1.0f;     // 0.25..1.0 internal resolution scale
    public int rtQuality = 1;            // 0=low, 1=medium, 2=high (controls samples)
    public boolean adaptivePerf = true;  // auto-adjust quality to maintain FPS
    public boolean skipWhenPaused = true;
    public boolean skipWhenOverlayOpen = false;

    // Shader Pack
    public String shaderPack = "builtin";

    // Keyboard shortcuts
    public String toggleOverlayKey = "F6";
    public String screenshotKey = "F9";
    public String resetImageKey = "F10";

    // UI
    public boolean overlayShowAdvanced = false;
    public float overlayOpacity = 0.85f;

    // Denoiser (OIDN)
    public boolean denoiserEnabled = false;
    public boolean denoiserUseGPU = true;

    public static LumenConfig get() {
        if (INSTANCE == null) {
            CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("lumen.json");
            load();
        }
        return INSTANCE;
    }

    public static void load() {
        if (Files.exists(CONFIG_PATH)) {
            try {
                String json = Files.readString(CONFIG_PATH);
                INSTANCE = GSON.fromJson(json, LumenConfig.class);
                LumenInit.LOGGER.info("[Lumen] Config loaded from {}", CONFIG_PATH);
            } catch (Exception e) {
                LumenInit.LOGGER.error("[Lumen] Failed to load config, using defaults", e);
                INSTANCE = new LumenConfig();
            }
        } else {
            INSTANCE = new LumenConfig();
            save();
        }
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(INSTANCE));
            LumenInit.LOGGER.info("[Lumen] Config saved to {}", CONFIG_PATH);
        } catch (IOException e) {
            LumenInit.LOGGER.error("[Lumen] Failed to save config", e);
        }
    }
}
