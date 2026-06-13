package com.luci.lumen.api.renderer;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.Set;

@Environment(EnvType.CLIENT)
public record RendererInfo(
        String name,
        String version,
        String gpuName,
        int vendorId,
        int deviceId,
        Set<Capability> caps
) {
    public enum Capability {
        SHADERS,
        RAY_TRACING,
        DLSS,
        FSR,
        XESS,
        HDR
    }
}
