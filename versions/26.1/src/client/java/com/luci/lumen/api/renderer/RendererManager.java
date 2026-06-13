package com.luci.lumen.api.renderer;

import com.luci.lumen.LumenInit;
import com.luci.lumen.api.renderer.backend.BerylBackend;
import com.luci.lumen.api.renderer.backend.VanillaBackend;
import com.luci.lumen.api.renderer.backend.VulkanModBackend;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public final class RendererManager {
    private static RendererManager INSTANCE;

    private final List<RendererBackend> backends = new ArrayList<>();
    private RendererBackend active;
    private boolean detected = false;

    private RendererManager() {}

    public static RendererManager get() {
        if (INSTANCE == null) {
            INSTANCE = new RendererManager();
        }
        return INSTANCE;
    }

    public void detect() {
        if (detected) return;
        detected = true;

        LumenInit.LOGGER.info("[Lumen] Detecting renderer backends...");

        backends.clear();

        VanillaBackend vanilla = new VanillaBackend();
        backends.add(vanilla);

        VulkanModBackend vkMod = new VulkanModBackend();
        if (vkMod.detect()) {
            LumenInit.LOGGER.info("[Lumen] VulkanModBackend detected");
            backends.add(vkMod);
        }

        BerylBackend beryl = new BerylBackend();
        if (beryl.detect()) {
            LumenInit.LOGGER.info("[Lumen] BerylBackend detected");
            backends.add(beryl);
        }

        active = selectBest();
        LumenInit.LOGGER.info("[Lumen] Active backend: {} ({})", active.getName(),
                active.isAvailable() ? "available" : "unavailable");
    }

    private RendererBackend selectBest() {
        for (RendererBackend backend : backends) {
            if (backend.isAvailable()) {
                return backend;
            }
        }
        LumenInit.LOGGER.warn("[Lumen] No renderer backend available, falling back to Vanilla");
        return backends.get(0);
    }

    public RendererBackend getActive() {
        if (!detected) detect();
        return active;
    }

    public List<RendererBackend> getAll() {
        return backends;
    }

    public String getStatusMessage() {
        var backend = getActive();
        String status = backend.isAvailable() ? "\u00a7a" : "\u00a7c";
        return status + backend.getName() + (backend.isAvailable() ? "" : " (unavailable)");
    }

    public boolean setShaderPack(Path path) {
        return getActive().setShaderPack(path);
    }

    public boolean canHandle(Path path) {
        return getActive().canHandle(path);
    }
}
