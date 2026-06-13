package com.luci.lumen.api.renderer.backend;

import com.luci.lumen.LumenInit;
import com.luci.lumen.api.renderer.DiagnosticsResult;
import com.luci.lumen.api.renderer.RendererBackend;
import com.luci.lumen.api.renderer.RendererInfo;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

@Environment(EnvType.CLIENT)
public class VanillaBackend implements RendererBackend {

    @Override
    public String getName() {
        return "Vanilla";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public boolean supportsShaders() {
        return false;
    }

    @Override
    public boolean supportsRayTracing() {
        return false;
    }

    @Override
    public RendererInfo getInfo() {
        return new RendererInfo(
                "Vanilla",
                "1.0",
                "Unknown (OpenGL)",
                0, 0,
                Set.of()
        );
    }

    @Override
    public boolean canHandle(Path shaderPackPath) {
        return false;
    }

    @Override
    public boolean setShaderPack(Path shaderPackPath) {
        LumenInit.LOGGER.warn("[Lumen] Vanilla backend cannot load shader packs (OpenGL renderer)");
        return false;
    }

    @Override
    public boolean init() {
        LumenInit.LOGGER.info("[Lumen] Vanilla backend: no initialization needed");
        return true;
    }

    @Override
    public void shutdown() {
    }

    @Override
    public DiagnosticsResult getDiagnostics() {
        return new DiagnosticsResult(
                true,
                Map.of("renderer", new DiagnosticsResult.StepStatus("Renderer", DiagnosticsResult.Status.OK, "Vanilla OpenGL"))
        );
    }
}
