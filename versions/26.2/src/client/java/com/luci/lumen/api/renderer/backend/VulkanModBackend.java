package com.luci.lumen.api.renderer.backend;

import com.luci.lumen.LumenInit;
import com.luci.lumen.api.renderer.DiagnosticsResult;
import com.luci.lumen.api.renderer.RendererBackend;
import com.luci.lumen.api.renderer.RendererInfo;
import com.luci.lumen.config.LumenConfig;
import com.luci.lumen.vk.LumenNativeBridge;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Environment(EnvType.CLIENT)
public class VulkanModBackend implements RendererBackend {

    private boolean detected = false;
    private boolean available = false;

    private long instanceHandle;
    private long deviceHandle;

    private final Map<String, DiagnosticsResult.StepStatus> diagnostics = new LinkedHashMap<>();

    public boolean detect() {
        if (!FabricLoader.getInstance().isModLoaded("vulkanmod")) {
            diagnostics.put("vulkanmod_loaded", new DiagnosticsResult.StepStatus(
                    "VulkanMod loaded", DiagnosticsResult.Status.FAILED, "VulkanMod not installed"));
            return false;
        }

        diagnostics.put("vulkanmod_loaded", new DiagnosticsResult.StepStatus(
                "VulkanMod loaded", DiagnosticsResult.Status.OK, "VulkanMod found"));
        detected = true;
        return true;
    }

    private boolean captureHandles() {
        try {
            Class<?> vulkanClass = Class.forName("net.vulkanmod.vulkan.Vulkan");

            Object vkDevice = vulkanClass.getMethod("getVkDevice").invoke(null);
            deviceHandle = (long) vkDevice.getClass().getMethod("address").invoke(vkDevice);

            Field instanceField = vulkanClass.getDeclaredField("instance");
            instanceField.setAccessible(true);
            Object vkInstance = instanceField.get(null);
            instanceHandle = (long) vkInstance.getClass().getMethod("address").invoke(vkInstance);

            LumenInit.LOGGER.info("[Lumen] Vulkan handles: instance=0x{}, device=0x{}",
                    Long.toHexString(instanceHandle), Long.toHexString(deviceHandle));

            if (instanceHandle == 0 || deviceHandle == 0) {
                diagnostics.put("handles", new DiagnosticsResult.StepStatus(
                        "Vulkan handles", DiagnosticsResult.Status.FAILED, "Null handle"));
                return false;
            }

            diagnostics.put("handles", new DiagnosticsResult.StepStatus(
                    "Vulkan handles", DiagnosticsResult.Status.OK,
                    String.format("instance=0x%X, device=0x%X", instanceHandle, deviceHandle)));
            return true;
        } catch (Exception e) {
            LumenInit.LOGGER.error("[Lumen] Failed to capture Vulkan handles", e);
            diagnostics.put("handles", new DiagnosticsResult.StepStatus(
                    "Vulkan handles", DiagnosticsResult.Status.FAILED, e.getMessage()));
            return false;
        }
    }

    private boolean verifyHandles() {
        if (instanceHandle == 0 || deviceHandle == 0) {
            diagnostics.put("verify_handles", new DiagnosticsResult.StepStatus(
                    "Handle verification", DiagnosticsResult.Status.FAILED, "Handles not captured"));
            return false;
        }

        boolean ok = LumenNativeBridge.nativeVerifyHandles(instanceHandle, deviceHandle);
        diagnostics.put("verify_handles", new DiagnosticsResult.StepStatus(
                "Handle verification",
                ok ? DiagnosticsResult.Status.OK : DiagnosticsResult.Status.FAILED,
                ok ? "Handles valid" : "Handles invalid — device may not support required extensions"));
        return ok;
    }

    @Override
    public String getName() {
        return "VulkanMod";
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public boolean supportsShaders() {
        return true;
    }

    @Override
    public boolean supportsRayTracing() {
        return available;
    }

    @Override
    public RendererInfo getInfo() {
        String gpu = "Unknown GPU";
        try {
            Class<?> vulkanClass = Class.forName("net.vulkanmod.vulkan.Vulkan");
            Object physDevice = vulkanClass.getMethod("getVkPhysicalDevice").invoke(null);
            if (physDevice != null) {
                gpu = physDevice.toString();
            }
        } catch (Exception ignored) {}

        Set<RendererInfo.Capability> caps;
        if (available) {
            caps = Set.of(RendererInfo.Capability.SHADERS, RendererInfo.Capability.RAY_TRACING);
        } else {
            caps = Set.of();
        }

        return new RendererInfo("VulkanMod",
                FabricLoader.getInstance().getModContainer("vulkanmod")
                        .map(m -> m.getMetadata().getVersion().getFriendlyString())
                        .orElse("unknown"),
                gpu, 0, 0, caps);
    }

    @Override
    public boolean canHandle(Path shaderPackPath) {
        if (!available && !detected) return false;
        if (shaderPackPath == null) return false;
        if (!shaderPackPath.toFile().exists()) return false;

        Path lumenDir = shaderPackPath.resolve(".lumen");
        if (lumenDir.toFile().exists() && lumenDir.resolve("shaders").toFile().exists()) {
            return true;
        }

        return true;
    }

    @Override
    public boolean setShaderPack(Path shaderPackPath) {
        if (!available) {
            LumenInit.LOGGER.warn("[Lumen] VulkanMod backend not available, cannot load shader pack");
            return false;
        }

        Path lumenDir = shaderPackPath.resolve(".lumen");
        if (lumenDir.toFile().exists() && lumenDir.resolve("shaders").toFile().exists()) {
            LumenInit.LOGGER.info("[Lumen] Loading .lumen pack: {}", shaderPackPath);
            return LumenNativeBridge.nativeLoadShaderPack(shaderPackPath.toString());
        }

        LumenInit.LOGGER.info("[Lumen] Pack {} has no .lumen overrides — using built-in RT shaders", shaderPackPath);
        return true;
    }

    @Override
    public boolean init() {
        if (!detected) return false;

        if (!LumenNativeBridge.loadNative()) {
            diagnostics.put("native_dll", new DiagnosticsResult.StepStatus(
                    "Native DLL", DiagnosticsResult.Status.FAILED, "Could not load lumen_native_rt.dll"));
            LumenInit.LOGGER.warn("[Lumen] Native library not found, RT disabled");
            return false;
        }

        diagnostics.put("native_dll", new DiagnosticsResult.StepStatus(
                "Native DLL", DiagnosticsResult.Status.OK, "Library loaded"));

        if (!captureHandles()) return false;

        if (!verifyHandles()) return false;

        boolean initResult = LumenNativeBridge.init(instanceHandle, deviceHandle);
        diagnostics.put("rt_init", new DiagnosticsResult.StepStatus(
                "RT pipeline init",
                initResult ? DiagnosticsResult.Status.OK : DiagnosticsResult.Status.FAILED,
                initResult ? "Pipeline ready" : "Init returned false"));

        if (initResult) {
            LumenNativeBridge.markInitialized();
            if (LumenConfig.get().denoiserEnabled) {
                boolean denoiseOk = LumenNativeBridge.nativeInitDenoiser(
                        800, 600, LumenConfig.get().denoiserUseGPU);
                diagnostics.put("oidn_init", new DiagnosticsResult.StepStatus(
                        "OIDN denoiser",
                        denoiseOk ? DiagnosticsResult.Status.OK : DiagnosticsResult.Status.FAILED,
                        denoiseOk ? "Denoiser ready" : "Failed to initialize (DLL not found?)"));
                if (!denoiseOk) {
                    LumenNativeBridge.nativeShutdownDenoiser();
                }
            }
            available = true;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                LumenInit.LOGGER.info("[Lumen] Shutting down native bridge...");
                LumenNativeBridge.nativeShutdownDenoiser();
                LumenNativeBridge.shutdown();
            }, "Lumen Shutdown"));
            return true;
        }

        return false;
    }

    @Override
    public void shutdown() {
        if (available) {
            LumenNativeBridge.shutdown();
            available = false;
        }
    }

    @Override
    public DiagnosticsResult getDiagnostics() {
        return new DiagnosticsResult(available, Map.copyOf(diagnostics));
    }

    public long getInstanceHandle() { return instanceHandle; }
    public long getDeviceHandle() { return deviceHandle; }
}
