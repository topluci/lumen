package com.luci.lumen.api.renderer.backend;

import com.luci.lumen.LumenInit;
import com.luci.lumen.api.renderer.DiagnosticsResult;
import com.luci.lumen.api.renderer.RendererBackend;
import com.luci.lumen.api.renderer.RendererInfo;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

@Environment(EnvType.CLIENT)
public class BerylBackend implements RendererBackend {

    private boolean detected = false;
    private boolean healthy = false;

    public boolean detect() {
        detected = FabricLoader.getInstance().isModLoaded("beryl");
        if (!detected) return false;

        healthy = checkHealth();
        return true;
    }

    private boolean checkHealth() {
        try {
            Class<?> berylClass = Class.forName("net.beryl.Beryl");
            Object instance = berylClass.getMethod("getInstance").invoke(null);
            if (instance != null) {
                LumenInit.LOGGER.info("[Lumen] Beryl health check: instance present");
                return true;
            }
        } catch (Exception e) {
            LumenInit.LOGGER.warn("[Lumen] Beryl health check failed: {}", e.getMessage());
        }
        return false;
    }

    @Override
    public String getName() {
        return "Beryl";
    }

    @Override
    public boolean isAvailable() {
        return detected && healthy;
    }

    @Override
    public boolean supportsShaders() {
        return isAvailable();
    }

    @Override
    public boolean supportsRayTracing() {
        return false;
    }

    @Override
    public RendererInfo getInfo() {
        return new RendererInfo(
                "Beryl",
                isAvailable() ? "detected" : "unavailable",
                "N/A",
                0, 0,
                isAvailable() ? Set.of(RendererInfo.Capability.SHADERS) : Set.of()
        );
    }

    @Override
    public boolean canHandle(Path shaderPackPath) {
        if (!isAvailable()) return false;
        if (shaderPackPath == null) return false;
        if (!shaderPackPath.toFile().exists()) return false;
        Path shadersDir = shaderPackPath.resolve("shaders");
        return shadersDir.toFile().exists() || shaderPackPath.toString().toLowerCase().endsWith(".zip");
    }

    @Override
    public boolean setShaderPack(Path shaderPackPath) {
        if (!isAvailable()) {
            LumenInit.LOGGER.warn("[Lumen] Beryl backend unavailable, cannot load shader pack");
            return false;
        }
        LumenInit.LOGGER.info("[Lumen] Beryl routing shader pack: {}", shaderPackPath);
        return true;
    }

    @Override
    public boolean init() {
        if (!detected) return false;
        if (!healthy) {
            LumenInit.LOGGER.warn("[Lumen] Beryl detected but unhealthy — will not route shaders through it");
        }
        return healthy;
    }

    @Override
    public void shutdown() {
    }

    @Override
    public DiagnosticsResult getDiagnostics() {
        return new DiagnosticsResult(
                isAvailable(),
                Map.of(
                        "detected", new DiagnosticsResult.StepStatus("Detected",
                                detected ? DiagnosticsResult.Status.OK : DiagnosticsResult.Status.FAILED,
                                detected ? "Beryl mod found" : "Beryl mod not found"),
                        "health", new DiagnosticsResult.StepStatus("Health",
                                healthy ? DiagnosticsResult.Status.OK : DiagnosticsResult.Status.FAILED,
                                healthy ? "Beryl initialized" : "Beryl init failed or unavailable")
                )
        );
    }
}
