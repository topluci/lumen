# Lumen — Progress Outline

## Legend

| Icon | Meaning |
|------|---------|
| ✅ | Done |
| 🔄 | In progress |
| ⏳ | Not started |
| ❌ | Blocked |

---

## Phase 1 — Foundation Setup

| # | Task | Status | Notes |
|---|------|--------|-------|
| 1.1 | Multi-version Gradle project (root `26.1` + `26.2`) | ✅ | `settings.gradle`, `build.gradle`, subproject configs |
| 1.2 | Fabric mod entrypoints (`LumenInit`, `LumenClientInit`) | ✅ | `main` and `client` entrypoints registered |
| 1.3 | Mixin plugin (`LumenMixinPlugin`) | ✅ | Conditional mixin application per MC version |
| 1.4 | Mixin configs (`lumen.mixins.json`, `lumen.client.mixins.json`) | ✅ | Separate server/client mixin sets; 26.1 excludes `VulkanDeviceMixin` |
| 1.5 | `VulkanDeviceMixin` — capture `VkInstance`/`VkDevice` handles | ✅ | 26.2 only, `@Inject` into `VulkanDevice` constructor |
| 1.6 | `VulkanDeviceInterceptor` — receive native handles as `long` | ✅ | Called from mixin; routes to JNI bridge; includes VulkanMod fallback |
| 1.7 | `GameRendererMixin` — `@Inject` at `TAIL` of `extract()` (26.x API) | ✅ | Uses `@Accessor("gameRenderState")` for cross-version compat; hooks native `renderFrame()` + overlay |
| 1.8 | `KeyboardInputMixin` — `@Inject` into `KeyboardHandler.keyPress()` | ✅ | `cancellable = true` for overlay toggle |
| 1.9 | `LumenNativeBridge.java` — JNI `native` declarations | ✅ | `init(long, long)`, `renderFrame()`, `shutdown()`, `nativeSetPerfParams()`, `updatePerf()` |
| 1.10 | `fabric.mod.json` (26.1 + 26.2) | ✅ | 26.1 targets `>=26.1`, 26.2 targets `>=26.2` |
| 1.11 | `build.gradle` cmake tasks (configure + build) | ✅ | LLVM-MinGW + Ninja, Vulkan SDK paths |
| 1.12 | Native DLL packed into JAR `META-INF/natives/` | ✅ | `jar.from(...)` in root `build.gradle` |
| 1.13 | JDK 25+ incubator vector module enabled | ✅ | `vmArgs '--add-modules', 'jdk.incubator.vector'` |
| 1.14 | Combined JAR task (`lumen-0.1.0-26.1-26.1.2.jar`) | ✅ | Merges 26.1 + 26.2 jars + native DLL; uses 26.1 `fabric.mod.json` (`>=26.1`) |
| 1.15 | Mojmap conversion (all imports) | ✅ | 26.2-snapshot-7 uses Mojmap, not Yarn |
| 1.16 | 26.x rendering API migration | ✅ | `extract(DeltaTracker, boolean)`, `GuiGraphicsExtractor`, `rebuildWidgets()` |
| 1.17 | ModMenu 20.0.0-alpha.1 update | ✅ | Fixes `class_437` remapping issue on 26.x |
| 1.18 | Subproject rename `26.1.2` → `26.1` | ✅ | Covers 26.1, 26.1.1, 26.1.2 |

---

## Phase 2 — Native RT Pipeline

| # | Task | Status | Notes |
|---|------|--------|-------|
| 2.1 | `lumen_device.h/.cpp` — Vulkan device query, RT caps | ✅ | Queries `VK_KHR_ray_tracing_pipeline`, `rayQuery`, `SER` |
| 2.2 | `lumen_as.h/.cpp` — BLAS builder | ✅ | `vkCmdBuildAccelerationStructuresKHR` |
| 2.3 | `lumen_as.h/.cpp` — TLAS builder | ✅ | Single instance wrapping BLAS |
| 2.4 | `lumen_scene.h/.cpp` — Cornell box (36 triangles, materials) | ✅ | Walls, ceiling, floor, light, 2 boxes |
| 2.5 | `lumen_output_image.h/.cpp` — storage image | ✅ | `R32G32B32A32_SFLOAT`, 800×600 |
| 2.6 | `lumen_rt_pipeline.h/.cpp` — RT pipeline | ✅ | `vkCreateRayTracingPipelinesKHR` |
| 2.7 | `lumen_rt_pipeline.h/.cpp` — Descriptor set layout | ✅ | TLAS + output + UBO + materials SSBO |
| 2.8 | `lumen_rt_pipeline.h/.cpp` — SBT (shader binding table) | ✅ | Strided handle generation |
| 2.9 | Raygen shader | ✅ | Pinhole camera, cosine-hemisphere, RR, NEE, Halton |
| 2.10 | Closest-hit shader | ✅ | Material color + emissive |
| 2.11 | Miss shader | ✅ | Sky color |
| 2.12 | SPIR-V compilation (`glslc`) | ✅ | Embedded as C headers |
| 2.13 | `lumen_jni_bridge.cpp` — `init()` wiring | ✅ | Pool, BLAS+TLAS, buffers, descriptors |
| 2.14 | `lumen_jni_bridge.cpp` — `renderFrame()` | ✅ | Dispatch, fence sync, adaptive frame pacing |
| 2.15 | `lumen_jni_bridge.cpp` — `shutdown()` | ✅ | Full Vulkan resource cleanup |
| 2.16 | Native post-process pipeline (`postprocess.comp`) | ✅ | 17 adjustment params + tonemap curve |
| 2.17 | Native frame pacing (QPC, adaptive skip) | ✅ | `nativeSetPerfParams()`, `updatePerf()` |
| 2.18 | Full Gradle build succeeds | ✅ | 26.1 and 26.2 subprojects + combined jar |
| 2.19 | DLSS/FSR/XeSS research complete | ✅ | |

---

## Phase 3 — Minecraft Integration

| # | Task | Status | Notes |
|---|------|--------|-------|
| 3.1 | Launch Minecraft 26.2, verify intercept log | ⏳ | Requires running `runClient` |
| 3.2 | Hybrid renderer detection (OpenGL / built-in Vulkan / VulkanMod) | ✅ | Prefer built-in → VulkanMod → OpenGL fallback |
| 3.3 | OpenGL banner + config status line | ✅ | Red "OpenGL — RT disabled" shown in settings |
| 3.4 | CompatibilityGuard (Iris, Sodium, Lithium, Potassium, VulkanMod, Beryl) | ✅ | Disables post-process when Iris shader packs active |
| 3.5 | In-game image adjustment overlay (F6) | ✅ | `GuiGraphicsExtractor`-based, keyboard nav |
| 3.6 | Config screen with categorized tabs (ModMenu) | ✅ | Post-Process, HDR, Frame Gen, Upscaling, Keys; Advanced toggle |
| 3.7 | Config persistence (JSON via Gson) | ✅ | `lumen.json` in config dir, 30+ fields |
| 3.8 | Capture Minecraft frame's color/depth `VkImage` | ⏳ | Need mixin into `MainTarget` or `VulkanGpuSurface` |
| 3.9 | Composite RT output → Minecraft framebuffer | ⏳ | |

---

## Phase 4 — Scene Integration

| # | Task | Status | Notes |
|---|------|--------|-------|
| 4.1 | Hook into chunk rebuild events | ⏳ | |
| 4.2 | Extract vertex/index buffers from chunk meshes | ⏳ | |
| 4.3 | Upload chunk geometry to GPU | ⏳ | |
| 4.4 | Rebuild BLAS per chunk | ⏳ | |
| 4.5 | Rebuild TLAS each frame | ⏳ | |
| 4.6 | Map block IDs → material properties | ⏳ | |

---

## Phase 5 — Features & Quality

| # | Task | Status | Notes |
|---|------|--------|-------|
| 5.1 | PBR material auto-generation | ⏳ | |
| 5.2 | Emissive block annotation (`@EmissiveBlock`) | ⏳ | |
| 5.3 | PBR material annotation (`@PBRMaterial`) | ⏳ | |
| 5.4 | Refractive annotation (`@Refractive`) | ⏳ | |
| 5.5 | Dynamic light source annotation (`@DynamicLightSource`) | ⏳ | |
| 5.6 | Ray query fallback | ⏳ | |
| 5.7 | Temporal accumulation / denoising | ⏳ | |
| 5.8 | DLSS 4.5 integration (Streamline SDK) | ⏳ | |
| 5.9 | FSR 3.1/4 integration (GPUOpen) | ⏳ | |
| 5.10 | XeSS 2.0 integration (DP4a) | ⏳ | |
| 5.11 | Vulkan 1.4 SER | ⏳ | |
| 5.12 | Vulkan descriptor heaps | ⏳ | |
| 5.13 | Potassium integration | ⏳ | |

---

## High-Level Timeline

```
Phase 1 ─████████████████████████████  (100%)
Phase 2 ─████████████████████████████  (100%)
Phase 3 ─████░░░░░░░░░░░░░░░░░░░░░░░░  (12%)
Phase 4 ─░░░░░░░░░░░░░░░░░░░░░░░░░░░░  (0%)
Phase 5 ─░░░░░░░░░░░░░░░░░░░░░░░░░░░░  (0%)
```

## Build Artifacts

| Artifact | Size |
|----------|------|
| `liblumen_native_rt.dll` (x86_64) | ~87 KB |
| `lumen-0.1.0-26.1.jar` (26.1.x, with DLL) | ~90 KB |
| `lumen-0.1.0-26.2.jar` (26.2, with DLL) | ~90 KB |
| `lumen-0.1.0-26.1-26.1.2.jar` (combined) | ~95 KB |
