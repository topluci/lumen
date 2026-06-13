package com.luci.lumen.api.renderer;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.nio.file.Path;

@Environment(EnvType.CLIENT)
public interface RendererBackend {

    String getName();

    boolean isAvailable();

    boolean supportsShaders();

    boolean supportsRayTracing();

    RendererInfo getInfo();

    boolean canHandle(Path shaderPackPath);

    boolean setShaderPack(Path shaderPackPath);

    boolean init();

    void shutdown();

    DiagnosticsResult getDiagnostics();
}
