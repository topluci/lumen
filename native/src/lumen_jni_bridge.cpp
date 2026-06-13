#include <jni.h>
#include <vulkan/vulkan.h>
#ifdef _WIN32
#include <windows.h>
#endif
#include "lumen_device.h"
#include "lumen_as.h"
#include "lumen_rt_pipeline.h"
#include "lumen_scene.h"
#include "lumen_output_image.h"
#include "lumen_postprocess.h"
#include "oidn_denoiser.h"
#include <cstdio>
#include <cstring>
#include <string>
#include <vector>
#include <fstream>

static LumenDevice g_dev{};
static LumenASBuilder g_asBuilder{};
static LumenAccelStruct* g_blas = nullptr;
static LumenAccelStruct* g_tlas = nullptr;
static LumenRTPipeline g_rtPipeline{};
static LumenOutputImage g_outputImage{};
static VkCommandPool g_cmdPool = VK_NULL_HANDLE;
static VkBuffer g_materialBuffer = VK_NULL_HANDLE;
static VkDeviceMemory g_materialMemory = VK_NULL_HANDLE;
static VkBuffer g_paramsBuffer = VK_NULL_HANDLE;
static VkDeviceMemory g_paramsMemory = VK_NULL_HANDLE;
static LumenPostProcess g_postProcess{};
static bool g_initialized = false;

// Init step tracking for diagnostics
enum InitStep {
    INIT_STEP_NONE = 0,
    INIT_STEP_HANDLE_VERIFY,
    INIT_STEP_QUERY_DEVICE,
    INIT_STEP_RT_SUPPORT,
    INIT_STEP_COMMAND_POOL,
    INIT_STEP_AS_BUILDER,
    INIT_STEP_BLAS,
    INIT_STEP_TLAS,
    INIT_STEP_OUTPUT_IMAGE,
    INIT_STEP_MATERIAL_BUFFER,
    INIT_STEP_PARAMS_BUFFER,
    INIT_STEP_RT_PIPELINE,
    INIT_STEP_DESCRIPTORS,
    INIT_STEP_COMPLETE
};
static int g_initStep = INIT_STEP_NONE;
static const char* initStepName(int step) {
    switch (step) {
        case INIT_STEP_NONE: return "Not started";
        case INIT_STEP_HANDLE_VERIFY: return "Handle verification";
        case INIT_STEP_QUERY_DEVICE: return "Device query";
        case INIT_STEP_RT_SUPPORT: return "RT support check";
        case INIT_STEP_COMMAND_POOL: return "Command pool";
        case INIT_STEP_AS_BUILDER: return "AS builder init";
        case INIT_STEP_BLAS: return "BLAS build";
        case INIT_STEP_TLAS: return "TLAS build";
        case INIT_STEP_OUTPUT_IMAGE: return "Output image";
        case INIT_STEP_MATERIAL_BUFFER: return "Material buffer";
        case INIT_STEP_PARAMS_BUFFER: return "Params buffer";
        case INIT_STEP_RT_PIPELINE: return "RT pipeline";
        case INIT_STEP_DESCRIPTORS: return "Descriptors";
        case INIT_STEP_COMPLETE: return "Complete";
        default: return "Unknown";
    }
}

// Staging buffer for GPU→CPU readback
static VkBuffer g_stagingBuffer = VK_NULL_HANDLE;
static VkDeviceMemory g_stagingMemory = VK_NULL_HANDLE;

struct RTParams {
    uint32_t frameCount;
    uint32_t samplePerPixel;
    float randomSeed;
    float lightIntensity;
    float cameraX, cameraY, cameraZ, _camPad1;  // vec4 aligned
    float cameraDirX, cameraDirY, cameraDirZ, _camPad2; // vec4 aligned
    uint32_t skipCounter;
    uint32_t skipEvery;
};

struct PerfState {
    uint64_t lastFrameTimeNs;
    uint64_t frameCount;
    uint32_t skipCounter;
    uint32_t skipEvery;
    uint32_t qualityLevel;
    float targetFrameTimeNs;
    bool adaptiveEnabled;
} g_perf = {0, 0, 0, 0, 1, 16666666.0f, true};

static uint32_t findMemoryType(VkPhysicalDevice physDev, uint32_t typeFilter,
                                VkMemoryPropertyFlags props) {
    VkPhysicalDeviceMemoryProperties memProps;
    vkGetPhysicalDeviceMemoryProperties(physDev, &memProps);
    for (uint32_t i = 0; i < memProps.memoryTypeCount; ++i) {
        if ((typeFilter & (1u << i)) &&
            (memProps.memoryTypes[i].propertyFlags & props) == props)
            return i;
    }
    return 0;
}

static VkBuffer createUploadBuffer(VkDevice device, VkDeviceSize size,
                                    VkBufferUsageFlags usage, const void* data,
                                    VkDeviceMemory* memory, VkPhysicalDevice physDev) {
    VkBufferCreateInfo bufInfo = {VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO};
    bufInfo.size = size;
    bufInfo.usage = usage | VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT;
    bufInfo.sharingMode = VK_SHARING_MODE_EXCLUSIVE;

    VkBuffer buffer;
    vkCreateBuffer(device, &bufInfo, nullptr, &buffer);

    VkMemoryRequirements memReqs;
    vkGetBufferMemoryRequirements(device, buffer, &memReqs);

    VkMemoryAllocateInfo allocInfo = {VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO};
    allocInfo.allocationSize = memReqs.size;
    allocInfo.memoryTypeIndex = findMemoryType(physDev, memReqs.memoryTypeBits,
        VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
    vkAllocateMemory(device, &allocInfo, nullptr, memory);
    vkBindBufferMemory(device, buffer, *memory, 0);

    void* mapped;
    vkMapMemory(device, *memory, 0, size, 0, &mapped);
    memcpy(mapped, data, (size_t)size);
    vkUnmapMemory(device, *memory);
    return buffer;
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_luci_lumen_vk_LumenNativeBridge_init(
    JNIEnv* env, jclass cls, jlong instanceHandle, jlong deviceHandle)
{
    VkInstance instance = reinterpret_cast<VkInstance>(static_cast<uintptr_t>(instanceHandle));
    VkDevice device = reinterpret_cast<VkDevice>(static_cast<uintptr_t>(deviceHandle));

    printf("[Lumen native] init(instance=0x%llx, device=0x%llx)\n",
           (unsigned long long)instanceHandle,
           (unsigned long long)deviceHandle);

    if (!instance || !device) {
        printf("[Lumen native] ERROR: null Vulkan handles\n");
        g_initStep = INIT_STEP_HANDLE_VERIFY;
        return JNI_FALSE;
    }
    g_initStep = INIT_STEP_HANDLE_VERIFY;

    if (!lumen_query_device(instance, device, &g_dev)) {
        printf("[Lumen native] ERROR: failed to query device\n");
        g_initStep = INIT_STEP_QUERY_DEVICE;
        return JNI_FALSE;
    }
    g_initStep = INIT_STEP_QUERY_DEVICE;

    if (!g_dev.supportsRayTracingPipeline) {
        printf("[Lumen native] ERROR: ray tracing pipeline not supported\n");
        g_initStep = INIT_STEP_RT_SUPPORT;
        return JNI_FALSE;
    }
    g_initStep = INIT_STEP_RT_SUPPORT;

    VkCommandPoolCreateInfo poolInfo = {VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO};
    poolInfo.flags = VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT;
    poolInfo.queueFamilyIndex = g_dev.graphicsQueueFamily;
    if (vkCreateCommandPool(device, &poolInfo, nullptr, &g_cmdPool) != VK_SUCCESS) {
        printf("[Lumen native] ERROR: failed to create command pool\n");
        g_initStep = INIT_STEP_COMMAND_POOL;
        return JNI_FALSE;
    }
    g_initStep = INIT_STEP_COMMAND_POOL;

    // Initialize AS builder
    g_asBuilder.init(device, g_dev.physicalDevice, g_dev.graphicsQueueFamily, g_dev.graphicsQueue);
    g_initStep = INIT_STEP_AS_BUILDER;

    // Build BLAS from Cornell box scene
    g_blas = g_asBuilder.buildBottomLevel(
        (const float*)SceneData::vertices, SceneData::vertexCount,
        reinterpret_cast<const uint32_t*>(SceneData::indices), SceneData::indexCount);
    if (!g_blas || !g_blas->handle) {
        printf("[Lumen native] ERROR: failed to build BLAS\n");
        g_initStep = INIT_STEP_BLAS;
        return JNI_FALSE;
    }
    g_initStep = INIT_STEP_BLAS;

    // Build TLAS
    g_tlas = g_asBuilder.buildTopLevel(g_blas, 1);
    if (!g_tlas || !g_tlas->handle) {
        printf("[Lumen native] ERROR: failed to build TLAS\n");
        g_initStep = INIT_STEP_TLAS;
        return JNI_FALSE;
    }
    g_initStep = INIT_STEP_TLAS;

    printf("[Lumen native] Cornell box accel structs built (%u tris)\n", SceneData::triCount);

    // Create output storage image
    if (!g_outputImage.init(device, g_dev.physicalDevice, 800, 600)) {
        printf("[Lumen native] ERROR: failed to create output image\n");
        g_initStep = INIT_STEP_OUTPUT_IMAGE;
        return JNI_FALSE;
    }
    g_initStep = INIT_STEP_OUTPUT_IMAGE;

    // Create material buffer (binding=3 in shader)
    VkDeviceSize matBufSize = SceneData::triCount * sizeof(SceneMat);
    g_materialBuffer = createUploadBuffer(device, matBufSize,
        VK_BUFFER_USAGE_STORAGE_BUFFER_BIT,
        SceneData::materials, &g_materialMemory, g_dev.physicalDevice);
    printf("[Lumen native] Material buffer created (%llu bytes)\n", (unsigned long long)matBufSize);
    g_initStep = INIT_STEP_MATERIAL_BUFFER;

    // Create params uniform buffer (binding=2 in shader)
    uint32_t samples = (g_perf.qualityLevel == 2) ? 4 : (g_perf.qualityLevel == 1) ? 1 : 1;
    RTParams params = {};
    params.frameCount = 0;
    params.samplePerPixel = samples;
    params.randomSeed = 0.0f;
    params.lightIntensity = SceneData::lightIntensity;
    params.cameraX = 0.0f; params.cameraY = 0.0f; params.cameraZ = 5.0f;
    params.cameraDirX = 0.0f; params.cameraDirY = 0.0f; params.cameraDirZ = -1.0f;
    params.skipCounter = 0;
    params.skipEvery = g_perf.skipEvery;
    params._camPad1 = 0.0f;
    params._camPad2 = 0.0f;
    g_paramsBuffer = createUploadBuffer(device, sizeof(RTParams),
        VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
        &params, &g_paramsMemory, g_dev.physicalDevice);
    g_initStep = INIT_STEP_PARAMS_BUFFER;

    // Initialize RT pipeline
    if (!g_rtPipeline.init(device, g_dev.physicalDevice, g_tlas, &g_outputImage)) {
        printf("[Lumen native] ERROR: failed to create RT pipeline\n");
        g_initStep = INIT_STEP_RT_PIPELINE;
        return JNI_FALSE;
    }
    g_initStep = INIT_STEP_RT_PIPELINE;

    // Update descriptor set with material buffer and params buffer
    VkDescriptorBufferInfo matBufInfo = {};
    matBufInfo.buffer = g_materialBuffer;
    matBufInfo.range = matBufSize;

    VkWriteDescriptorSet writeMat = {VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET};
    writeMat.dstSet = g_rtPipeline.descSet;
    writeMat.dstBinding = 3;
    writeMat.descriptorCount = 1;
    writeMat.descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
    writeMat.pBufferInfo = &matBufInfo;
    vkUpdateDescriptorSets(device, 1, &writeMat, 0, nullptr);

    VkDescriptorBufferInfo paramBufInfo = {};
    paramBufInfo.buffer = g_paramsBuffer;
    paramBufInfo.range = sizeof(RTParams);

    VkWriteDescriptorSet writeParams = {VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET};
    writeParams.dstSet = g_rtPipeline.descSet;
    writeParams.dstBinding = 2;
    writeParams.descriptorCount = 1;
    writeParams.descriptorType = VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER;
    writeParams.pBufferInfo = &paramBufInfo;
    vkUpdateDescriptorSets(device, 1, &writeParams, 0, nullptr);
    g_initStep = INIT_STEP_DESCRIPTORS;

    g_initStep = INIT_STEP_COMPLETE;
    printf("[Lumen native] init() complete - Cornell box path tracer ready\n");
    g_initialized = true;
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_luci_lumen_vk_LumenNativeBridge_renderFrame(
    JNIEnv* env, jclass cls)
{
    if (!g_initialized) return JNI_FALSE;

    // Frame pacing: skip frames when GPU is overloaded
    g_perf.skipCounter++;
    if (g_perf.skipEvery > 0 && (g_perf.skipCounter % (g_perf.skipEvery + 1)) != 0) {
        return JNI_TRUE; // skip this frame
    }

    VkDevice device = g_dev.device;

    // --- Blit output image to staging buffer for readback ---
    if (!g_stagingBuffer) {
        VkDeviceSize bufSize = g_outputImage.width * g_outputImage.height * 4;
        VkBufferCreateInfo bufInfo = {VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO};
        bufInfo.size = bufSize;
        bufInfo.usage = VK_BUFFER_USAGE_TRANSFER_DST_BIT;
        bufInfo.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
        vkCreateBuffer(device, &bufInfo, nullptr, &g_stagingBuffer);

        VkMemoryRequirements memReqs;
        vkGetBufferMemoryRequirements(device, g_stagingBuffer, &memReqs);
        VkMemoryAllocateInfo allocInfo = {VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO};
        allocInfo.allocationSize = memReqs.size;
        allocInfo.memoryTypeIndex = findMemoryType(g_dev.physicalDevice,
            memReqs.memoryTypeBits,
            VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
        vkAllocateMemory(device, &allocInfo, nullptr, &g_stagingMemory);
        vkBindBufferMemory(device, g_stagingBuffer, g_stagingMemory, 0);
    }
    auto vkCmdTraceRays = (PFN_vkCmdTraceRaysKHR)
        vkGetDeviceProcAddr(device, "vkCmdTraceRaysKHR");
    if (!vkCmdTraceRays) return JNI_FALSE;

    // Update frame counter and params
    void* mapped;
    vkMapMemory(device, g_paramsMemory, 0, sizeof(RTParams), 0, &mapped);
    RTParams params;
    memcpy(&params, mapped, sizeof(RTParams));
    params.frameCount++;
    params.skipCounter = g_perf.skipCounter;
    params.skipEvery = g_perf.skipEvery;
    memcpy(mapped, &params, sizeof(RTParams));
    vkUnmapMemory(device, g_paramsMemory);

    VkCommandBufferAllocateInfo allocInfo = {VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO};
    allocInfo.commandPool = g_cmdPool;
    allocInfo.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
    allocInfo.commandBufferCount = 1;

    VkCommandBuffer cmdBuf;
    vkAllocateCommandBuffers(device, &allocInfo, &cmdBuf);

    VkCommandBufferBeginInfo beginInfo = {VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO};
    beginInfo.flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
    vkBeginCommandBuffer(cmdBuf, &beginInfo);

    vkCmdBindDescriptorSets(cmdBuf, VK_PIPELINE_BIND_POINT_RAY_TRACING_KHR,
                            g_rtPipeline.pipelineLayout, 0, 1, &g_rtPipeline.descSet, 0, nullptr);

    vkCmdBindPipeline(cmdBuf, VK_PIPELINE_BIND_POINT_RAY_TRACING_KHR, g_rtPipeline.pipeline);

    vkCmdTraceRays(cmdBuf,
        &g_rtPipeline.raygenSBT,
        &g_rtPipeline.missSBT,
        &g_rtPipeline.hitSBT,
        &g_rtPipeline.callableSBT,
        g_outputImage.width, g_outputImage.height, 1);

    vkEndCommandBuffer(cmdBuf);

    VkSubmitInfo submitInfo = {VK_STRUCTURE_TYPE_SUBMIT_INFO};
    submitInfo.commandBufferCount = 1;
    submitInfo.pCommandBuffers = &cmdBuf;

    VkFenceCreateInfo fenceInfo = {VK_STRUCTURE_TYPE_FENCE_CREATE_INFO};
    VkFence fence;
    vkCreateFence(device, &fenceInfo, nullptr, &fence);

    uint64_t submitStart = 0;
    if (g_perf.adaptiveEnabled) {
        LARGE_INTEGER tsc;
        QueryPerformanceCounter(&tsc);
        submitStart = (uint64_t)tsc.QuadPart;
    }

    if (vkQueueSubmit(g_dev.graphicsQueue, 1, &submitInfo, fence) != VK_SUCCESS) {
        printf("[Lumen native] ERROR: queue submit failed\n");
        vkDestroyFence(device, fence, nullptr);
        vkFreeCommandBuffers(device, g_cmdPool, 1, &cmdBuf);
        return JNI_FALSE;
    }

    vkWaitForFences(device, 1, &fence, VK_TRUE, UINT64_MAX);
    vkDestroyFence(device, fence, nullptr);
    vkFreeCommandBuffers(device, g_cmdPool, 1, &cmdBuf);

    // --- Copy output image to staging buffer for Java readback ---
    VkCommandBufferAllocateInfo copyAlloc = {VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO};
    copyAlloc.commandPool = g_cmdPool;
    copyAlloc.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
    copyAlloc.commandBufferCount = 1;
    VkCommandBuffer copyCmd;
    vkAllocateCommandBuffers(device, &copyAlloc, &copyCmd);

    VkCommandBufferBeginInfo copyBegin = {VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO};
    copyBegin.flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
    vkBeginCommandBuffer(copyCmd, &copyBegin);

    // Transition output image from GENERAL to TRANSFER_SRC
    VkImageMemoryBarrier barrier = {VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER};
    barrier.oldLayout = VK_IMAGE_LAYOUT_GENERAL;
    barrier.newLayout = VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL;
    barrier.srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
    barrier.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
    barrier.image = g_outputImage.image;
    barrier.subresourceRange.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
    barrier.subresourceRange.levelCount = 1;
    barrier.subresourceRange.layerCount = 1;
    barrier.srcAccessMask = VK_ACCESS_SHADER_WRITE_BIT;
    barrier.dstAccessMask = VK_ACCESS_TRANSFER_READ_BIT;
    vkCmdPipelineBarrier(copyCmd,
        VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
        VK_PIPELINE_STAGE_TRANSFER_BIT, 0, 0, nullptr, 0, nullptr, 1, &barrier);

    VkBufferImageCopy region = {};
    region.imageSubresource.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
    region.imageSubresource.layerCount = 1;
    region.imageExtent = {g_outputImage.width, g_outputImage.height, 1};
    vkCmdCopyImageToBuffer(copyCmd, g_outputImage.image,
        VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, g_stagingBuffer, 1, &region);

    // Transition back to GENERAL
    barrier.oldLayout = VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL;
    barrier.newLayout = VK_IMAGE_LAYOUT_GENERAL;
    barrier.srcAccessMask = VK_ACCESS_TRANSFER_READ_BIT;
    barrier.dstAccessMask = VK_ACCESS_SHADER_WRITE_BIT;
    vkCmdPipelineBarrier(copyCmd,
        VK_PIPELINE_STAGE_TRANSFER_BIT,
        VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, 0, 0, nullptr, 0, nullptr, 1, &barrier);

    vkEndCommandBuffer(copyCmd);

    VkSubmitInfo copySubmit = {VK_STRUCTURE_TYPE_SUBMIT_INFO};
    copySubmit.commandBufferCount = 1;
    copySubmit.pCommandBuffers = &copyCmd;
    VkFence copyFence;
    VkFenceCreateInfo copyFenceInfo = {VK_STRUCTURE_TYPE_FENCE_CREATE_INFO};
    vkCreateFence(device, &copyFenceInfo, nullptr, &copyFence);
    vkQueueSubmit(g_dev.graphicsQueue, 1, &copySubmit, copyFence);
    vkWaitForFences(device, 1, &copyFence, VK_TRUE, UINT64_MAX);
    vkDestroyFence(device, copyFence, nullptr);
    vkFreeCommandBuffers(device, g_cmdPool, 1, &copyCmd);
    g_outputImage.frameCount++;

    // Adaptive performance: measure frame time and adjust skip
    if (g_perf.adaptiveEnabled) {
        LARGE_INTEGER tsc, freq;
        QueryPerformanceCounter(&tsc);
        QueryPerformanceFrequency(&freq);
        uint64_t frameTimeNs = (tsc.QuadPart - submitStart) * 1000000000ULL / freq.QuadPart;
        g_perf.lastFrameTimeNs = frameTimeNs;
        g_perf.frameCount++;

        if (g_perf.frameCount % 30 == 0) {
            float frameMs = (float)(double)frameTimeNs / 1000000.0f;
            float targetMs = (float)(double)g_perf.targetFrameTimeNs / 1000000.0f;
            if (targetMs > 0.0f && frameMs > targetMs * 1.5f && g_perf.skipEvery < 4) {
                g_perf.skipEvery++;
                printf("[Lumen native] Perf: frame %.1fms > target %.1fms, skipEvery=%u\n",
                       frameMs, targetMs, g_perf.skipEvery);
            } else if (frameMs < targetMs * 0.7f && g_perf.skipEvery > 0) {
                g_perf.skipEvery--;
            }
        }
    }

    return JNI_TRUE;
}

JNIEXPORT jintArray JNICALL
Java_com_luci_lumen_vk_LumenNativeBridge_nativeReadbackPixels(
    JNIEnv* env, jclass cls)
{
    if (!g_initialized) return nullptr;

    VkDevice device = g_dev.device;
    uint32_t w = g_outputImage.width;
    uint32_t h = g_outputImage.height;
    uint32_t pixelCount = w * h;

    void* mapped;
    vkMapMemory(device, g_stagingMemory, 0, pixelCount * 4, 0, &mapped);

    jintArray result = env->NewIntArray((jsize)pixelCount);
    if (result) {
        env->SetIntArrayRegion(result, 0, (jsize)pixelCount, (const jint*)mapped);
    }

    vkUnmapMemory(device, g_stagingMemory);
    return result;
}

JNIEXPORT jboolean JNICALL
Java_com_luci_lumen_vk_LumenNativeBridge_nativeUploadScene(
    JNIEnv* env, jclass cls,
    jfloatArray vertices, jintArray indices,
    jint vertexCount, jint primitiveCount)
{
    if (!g_initialized) {
        printf("[Lumen native] uploadScene: not initialized\n");
        return JNI_FALSE;
    }
    VkDevice device = g_dev.device;

    jfloat* vertData = env->GetFloatArrayElements(vertices, nullptr);
    jint* idxData = env->GetIntArrayElements(indices, nullptr);
    jsize vertFloats = env->GetArrayLength(vertices);
    jsize idxCount = env->GetArrayLength(indices);

    // Destroy old geometry
    if (g_tlas) { g_tlas->destroy(device); delete g_tlas; g_tlas = nullptr; }
    if (g_blas) { g_blas->destroy(device); delete g_blas; g_blas = nullptr; }
    if (g_materialBuffer) { vkDestroyBuffer(device, g_materialBuffer, nullptr); g_materialBuffer = VK_NULL_HANDLE; }
    if (g_materialMemory) { vkFreeMemory(device, g_materialMemory, nullptr); g_materialMemory = VK_NULL_HANDLE; }

    uint32_t indexCount = (uint32_t)idxCount;

    // Build BLAS
    g_blas = g_asBuilder.buildBottomLevel(
        (const float*)vertData, (uint32_t)vertexCount,
        reinterpret_cast<const uint32_t*>(idxData), indexCount);
    if (!g_blas || !g_blas->handle) {
        printf("[Lumen native] uploadScene: BLAS build failed\n");
        env->ReleaseFloatArrayElements(vertices, vertData, JNI_ABORT);
        env->ReleaseIntArrayElements(indices, idxData, JNI_ABORT);
        return JNI_FALSE;
    }

    // Build TLAS
    g_tlas = g_asBuilder.buildTopLevel(g_blas, 1);
    if (!g_tlas || !g_tlas->handle) {
        printf("[Lumen native] uploadScene: TLAS build failed\n");
        env->ReleaseFloatArrayElements(vertices, vertData, JNI_ABORT);
        env->ReleaseIntArrayElements(indices, idxData, JNI_ABORT);
        return JNI_FALSE;
    }

    printf("[Lumen native] Scene uploaded: %u verts, %u tris\n",
           (uint32_t)vertexCount, (uint32_t)primitiveCount);

    // Create default material buffer (white, non-emissive)
    VkDeviceSize matBufSize = (uint32_t)primitiveCount * sizeof(SceneMat);
    SceneMat* defaultMats = new SceneMat[(uint32_t)primitiveCount];
    for (uint32_t i = 0; i < (uint32_t)primitiveCount; i++) {
        defaultMats[i] = {1.0f, 1.0f, 1.0f, 0};
    }
    g_materialBuffer = createUploadBuffer(device, matBufSize,
        VK_BUFFER_USAGE_STORAGE_BUFFER_BIT,
        defaultMats, &g_materialMemory, g_dev.physicalDevice);
    delete[] defaultMats;

    // Update TLAS descriptor (binding 1)
    VkWriteDescriptorSetAccelerationStructureKHR writeAS = {
        VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET_ACCELERATION_STRUCTURE_KHR};
    writeAS.accelerationStructureCount = 1;
    writeAS.pAccelerationStructures = &g_tlas->handle;

    VkWriteDescriptorSet writeTLAS = {VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET};
    writeTLAS.pNext = &writeAS;
    writeTLAS.dstSet = g_rtPipeline.descSet;
    writeTLAS.dstBinding = 1;
    writeTLAS.descriptorCount = 1;
    writeTLAS.descriptorType = VK_DESCRIPTOR_TYPE_ACCELERATION_STRUCTURE_KHR;
    vkUpdateDescriptorSets(device, 1, &writeTLAS, 0, nullptr);

    // Update material descriptor (binding 3)
    VkDescriptorBufferInfo matBufInfo = {};
    matBufInfo.buffer = g_materialBuffer;
    matBufInfo.range = matBufSize;

    VkWriteDescriptorSet writeMat = {VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET};
    writeMat.dstSet = g_rtPipeline.descSet;
    writeMat.dstBinding = 3;
    writeMat.descriptorCount = 1;
    writeMat.descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
    writeMat.pBufferInfo = &matBufInfo;
    vkUpdateDescriptorSets(device, 1, &writeMat, 0, nullptr);

    env->ReleaseFloatArrayElements(vertices, vertData, JNI_ABORT);
    env->ReleaseIntArrayElements(indices, idxData, JNI_ABORT);

    printf("[Lumen native] uploadScene complete\n");
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_luci_lumen_vk_LumenNativeBridge_nativeUpdateCamera(
    JNIEnv* env, jclass cls,
    jfloat x, jfloat y, jfloat z,
    jfloat dirX, jfloat dirY, jfloat dirZ)
{
    if (!g_initialized) return;
    VkDevice device = g_dev.device;

    void* mapped;
    vkMapMemory(device, g_paramsMemory, 0, sizeof(RTParams), 0, &mapped);
    RTParams params;
    memcpy(&params, mapped, sizeof(RTParams));
    params.cameraX = x;
    params.cameraY = y;
    params.cameraZ = z;
    params.cameraDirX = dirX;
    params.cameraDirY = dirY;
    params.cameraDirZ = dirZ;
    memcpy(mapped, &params, sizeof(RTParams));
    vkUnmapMemory(device, g_paramsMemory);
}

JNIEXPORT jboolean JNICALL
Java_com_luci_lumen_vk_LumenNativeBridge_shutdown(
    JNIEnv* env, jclass cls)
{
    printf("[Lumen native] shutdown()\n");

    g_postProcess.destroy();
    g_rtPipeline.destroy(g_dev.device);
    g_outputImage.destroy(g_dev.device);

    if (g_tlas) { g_tlas->destroy(g_dev.device); delete g_tlas; g_tlas = nullptr; }
    if (g_blas) { g_blas->destroy(g_dev.device); delete g_blas; g_blas = nullptr; }
    g_asBuilder.destroy();

    if (g_materialBuffer) vkDestroyBuffer(g_dev.device, g_materialBuffer, nullptr);
    if (g_materialMemory) vkFreeMemory(g_dev.device, g_materialMemory, nullptr);
    if (g_paramsBuffer) vkDestroyBuffer(g_dev.device, g_paramsBuffer, nullptr);
    if (g_paramsMemory) vkFreeMemory(g_dev.device, g_paramsMemory, nullptr);
    if (g_cmdPool) vkDestroyCommandPool(g_dev.device, g_cmdPool, nullptr);

    // Clean up staging buffer
    if (g_stagingBuffer) vkDestroyBuffer(g_dev.device, g_stagingBuffer, nullptr);
    if (g_stagingMemory) vkFreeMemory(g_dev.device, g_stagingMemory, nullptr);
    g_stagingBuffer = VK_NULL_HANDLE;
    g_stagingMemory = VK_NULL_HANDLE;

    g_initialized = false;
    g_initStep = INIT_STEP_NONE;
    g_dev = {};
    printf("[Lumen native] shutdown() complete\n");
    return JNI_TRUE;
}

// ---- Diagnostics JNI ----

JNIEXPORT jboolean JNICALL
Java_com_luci_lumen_vk_LumenNativeBridge_nativeVerifyHandles(
    JNIEnv* env, jclass cls, jlong instanceHandle, jlong deviceHandle)
{
    VkInstance instance = reinterpret_cast<VkInstance>(static_cast<uintptr_t>(instanceHandle));
    VkDevice device = reinterpret_cast<VkDevice>(static_cast<uintptr_t>(deviceHandle));

    if (!instance || !device) {
        printf("[Lumen native] verifyHandles: null handles\n");
        return JNI_FALSE;
    }

    // Try to get a queue from the device — if this works, handles are valid
    VkQueue queue = VK_NULL_HANDLE;
    vkGetDeviceQueue(device, 0, 0, &queue);
    if (!queue) {
        printf("[Lumen native] verifyHandles: vkGetDeviceQueue returned null\n");
        return JNI_FALSE;
    }

    // Verify physical device enumeration works
    uint32_t count = 0;
    vkEnumeratePhysicalDevices(instance, &count, nullptr);
    if (count == 0) {
        printf("[Lumen native] verifyHandles: no physical devices found\n");
        return JNI_FALSE;
    }

    printf("[Lumen native] verifyHandles: OK (%u physical device(s), queue=%p)\n", count, (void*)queue);
    return JNI_TRUE;
}

JNIEXPORT jint JNICALL
Java_com_luci_lumen_vk_LumenNativeBridge_nativeGetInitStep(
    JNIEnv* env, jclass cls)
{
    return (jint)g_initStep;
}

// ---- Resize JNI ----

JNIEXPORT jboolean JNICALL
Java_com_luci_lumen_vk_LumenNativeBridge_nativeResize(
    JNIEnv* env, jclass cls, jint width, jint height)
{
    if (!g_initialized) {
        printf("[Lumen native] resize: not initialized\n");
        return JNI_FALSE;
    }

    if (width <= 0 || height <= 0) {
        printf("[Lumen native] resize: invalid dimensions %dx%d\n", (int)width, (int)height);
        return JNI_FALSE;
    }

    VkDevice device = g_dev.device;

    // Destroy old staging buffer
    if (g_stagingBuffer) vkDestroyBuffer(device, g_stagingBuffer, nullptr);
    if (g_stagingMemory) vkFreeMemory(device, g_stagingMemory, nullptr);
    g_stagingBuffer = VK_NULL_HANDLE;
    g_stagingMemory = VK_NULL_HANDLE;

    // Resize output image
    bool ok = g_outputImage.resize(device, g_dev.physicalDevice, (uint32_t)width, (uint32_t)height);
    if (!ok) {
        printf("[Lumen native] resize: output image resize failed\n");
        return JNI_FALSE;
    }

    printf("[Lumen native] resize: output image resized to %dx%d\n", (int)width, (int)height);
    return JNI_TRUE;
}

// ---- Performance JNI ----

JNIEXPORT void JNICALL
Java_com_luci_lumen_vk_LumenNativeBridge_nativeSetPerfParams(
    JNIEnv* env, jclass cls,
    jint targetFps, jint frameSkip, jint quality,
    jboolean adaptive, jfloat renderScale)
{
    g_perf.qualityLevel = (uint32_t)(quality < 0 ? 1 : quality > 2 ? 2 : quality);
    g_perf.skipEvery = (uint32_t)(frameSkip < 0 ? 0 : frameSkip > 4 ? 4 : frameSkip);
    g_perf.adaptiveEnabled = adaptive != JNI_FALSE;
    g_perf.targetFrameTimeNs = targetFps > 0
        ? 1000000000.0f / (float)targetFps
        : 16666666.0f;  // default 60fps target

    // Update sample count based on quality in the params buffer
    if (g_initialized) {
        VkDevice device = g_dev.device;
        uint32_t samples = (g_perf.qualityLevel == 2) ? 4 : 1;
        void* mapped;
        vkMapMemory(device, g_paramsMemory, 0, sizeof(RTParams), 0, &mapped);
        RTParams params;
        memcpy(&params, mapped, sizeof(RTParams));
        params.samplePerPixel = samples;
        params.skipEvery = g_perf.skipEvery;
        memcpy(mapped, &params, sizeof(RTParams));
        vkUnmapMemory(device, g_paramsMemory);
    }

    printf("[Lumen native] Perf: targetFps=%d, skipEvery=%u, quality=%u, adaptive=%d, scale=%.2f\n",
           (int)targetFps, g_perf.skipEvery, g_perf.qualityLevel, (int)adaptive, (float)renderScale);
}

// ---- Post-Process JNI ----

JNIEXPORT jboolean JNICALL
Java_com_luci_lumen_vk_LumenNativeBridge_nativeInitPostProcess(
    JNIEnv* env, jclass cls, jlong vkDevicePtr)
{
    VkDevice device = reinterpret_cast<VkDevice>(static_cast<uintptr_t>(vkDevicePtr));
    if (device == VK_NULL_HANDLE) return JNI_FALSE;
    bool ok = g_postProcess.init(device, g_dev.physicalDevice);
    printf("[Lumen PP] init: %s\n", ok ? "OK" : "FAILED");
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_luci_lumen_vk_LumenNativeBridge_nativeDispatchPostProcess(
    JNIEnv* env, jclass cls,
    jlong vkCmdBufPtr, jlong inputViewPtr, jlong outputViewPtr,
    jfloat brightness, jfloat contrast, jfloat saturation,
    jfloat vibrance, jfloat temperature, jfloat tint,
    jfloat sharpness, jfloat filmGrain, jfloat vignette,
    jfloat exposure, jfloat shadows, jfloat highlights,
    jfloat whites, jfloat blacks, jfloat clarity, jfloat dehaze,
    jint tonemapCurve)
{
    VkCommandBuffer cmdBuf = reinterpret_cast<VkCommandBuffer>(static_cast<uintptr_t>(vkCmdBufPtr));
    VkImageView inputView = reinterpret_cast<VkImageView>(static_cast<uintptr_t>(inputViewPtr));
    VkImageView outputView = reinterpret_cast<VkImageView>(static_cast<uintptr_t>(outputViewPtr));

    LumenPostProcessParams params = {};
    params.brightness   = brightness;
    params.contrast     = contrast;
    params.saturation   = saturation;
    params.vibrance     = vibrance;
    params.temperature  = temperature;
    params.tint         = tint;
    params.sharpness    = sharpness;
    params.filmGrain    = filmGrain;
    params.vignette     = vignette;
    params.exposure     = exposure;
    params.shadows      = shadows;
    params.highlights   = highlights;
    params.whites       = whites;
    params.blacks       = blacks;
    params.clarity      = clarity;
    params.dehaze       = dehaze;
    params.tonemapCurve = tonemapCurve;
    params.frameCount   = g_outputImage.frameCount;

    bool ok = g_postProcess.dispatch(cmdBuf, inputView, outputView,
                                      g_outputImage.width, g_outputImage.height, params);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_luci_lumen_vk_LumenNativeBridge_nativeShutdownPostProcess(
    JNIEnv* env, jclass cls)
{
    g_postProcess.destroy();
}

static bool readSpvFile(const std::string& path, std::vector<uint32_t>& code) {
    std::ifstream file(path, std::ios::binary | std::ios::ate);
    if (!file.is_open()) {
        printf("[Lumen native] readSpvFile: cannot open %s\n", path.c_str());
        return false;
    }
    size_t fileSize = (size_t)file.tellg();
    if (fileSize < 4 || fileSize % 4 != 0) {
        printf("[Lumen native] readSpvFile: invalid SPV file size %zu\n", fileSize);
        return false;
    }
    file.seekg(0, std::ios::beg);
    code.resize(fileSize / 4);
    file.read(reinterpret_cast<char*>(code.data()), (std::streamsize)fileSize);
    file.close();
    // Verify SPIR-V magic number
    if (code[0] != 0x07230203) {
        printf("[Lumen native] readSpvFile: invalid SPIR-V magic in %s\n", path.c_str());
        return false;
    }
    return true;
}

// ---- Shader Pack JNI ----
JNIEXPORT jboolean JNICALL
Java_com_luci_lumen_vk_LumenNativeBridge_nativeLoadShaderPack(
    JNIEnv* env, jclass cls, jstring shaderPackPath)
{
    if (!g_initialized) {
        printf("[Lumen native] nativeLoadShaderPack: not initialized\n");
        return JNI_FALSE;
    }

    const char* path = env->GetStringUTFChars(shaderPackPath, nullptr);
    printf("[Lumen native] nativeLoadShaderPack(\"%s\")\n", path);

    std::string base(path);
    std::string raygenPath = base + "/.lumen/shaders/raygen.spv";
    std::string closesthitPath = base + "/.lumen/shaders/closesthit.spv";
    std::string missPath = base + "/.lumen/shaders/miss.spv";

    std::vector<uint32_t> raygen, closesthit, miss;
    if (!readSpvFile(raygenPath, raygen) ||
        !readSpvFile(closesthitPath, closesthit) ||
        !readSpvFile(missPath, miss)) {
        printf("[Lumen native] nativeLoadShaderPack: failed to read SPV files from %s/.lumen/shaders/\n", path);
        // Also try legacy flat layout
        raygenPath = base + "/raygen.spv";
        closesthitPath = base + "/closesthit.spv";
        missPath = base + "/miss.spv";
        if (!readSpvFile(raygenPath, raygen) ||
            !readSpvFile(closesthitPath, closesthit) ||
            !readSpvFile(missPath, miss)) {
            printf("[Lumen native] nativeLoadShaderPack: failed to read SPV files from %s (flat)\n", path);
            env->ReleaseStringUTFChars(shaderPackPath, path);
            return JNI_FALSE;
        }
    }

    // Reset frame counter for progressive rendering
    {
        VkDevice device = g_dev.device;
        void* mapped;
        vkMapMemory(device, g_paramsMemory, 0, sizeof(RTParams), 0, &mapped);
        RTParams params;
        memcpy(&params, mapped, sizeof(RTParams));
        params.frameCount = 0;
        memcpy(mapped, &params, sizeof(RTParams));
        vkUnmapMemory(device, g_paramsMemory);
    }

    bool ok = g_rtPipeline.reloadShaders(g_dev.device, g_dev.physicalDevice,
                                          &g_outputImage,
                                          raygen.data(), (uint32_t)raygen.size(),
                                          closesthit.data(), (uint32_t)closesthit.size(),
                                          miss.data(), (uint32_t)miss.size());
    if (!ok) {
        printf("[Lumen native] nativeLoadShaderPack: pipeline reload failed\n");
        env->ReleaseStringUTFChars(shaderPackPath, path);
        return JNI_FALSE;
    }

    // Re-write TLAS descriptor (binding 1)
    {
        VkWriteDescriptorSetAccelerationStructureKHR writeAS = {
            VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET_ACCELERATION_STRUCTURE_KHR};
        writeAS.accelerationStructureCount = 1;
        writeAS.pAccelerationStructures = &g_tlas->handle;

        VkWriteDescriptorSet writeTLAS = {VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET};
        writeTLAS.pNext = &writeAS;
        writeTLAS.dstSet = g_rtPipeline.descSet;
        writeTLAS.dstBinding = 1;
        writeTLAS.descriptorCount = 1;
        writeTLAS.descriptorType = VK_DESCRIPTOR_TYPE_ACCELERATION_STRUCTURE_KHR;
        vkUpdateDescriptorSets(g_dev.device, 1, &writeTLAS, 0, nullptr);
    }

    // Re-write params descriptor (binding 2)
    {
        VkDescriptorBufferInfo paramBufInfo = {};
        paramBufInfo.buffer = g_paramsBuffer;
        paramBufInfo.range = sizeof(RTParams);

        VkWriteDescriptorSet writeParams = {VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET};
        writeParams.dstSet = g_rtPipeline.descSet;
        writeParams.dstBinding = 2;
        writeParams.descriptorCount = 1;
        writeParams.descriptorType = VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER;
        writeParams.pBufferInfo = &paramBufInfo;
        vkUpdateDescriptorSets(g_dev.device, 1, &writeParams, 0, nullptr);
    }

    // Re-write material descriptor (binding 3)
    if (g_materialBuffer) {
        VkDeviceSize matBufSize = g_rtPipeline.descSet ? 0 : 0;
        // Get material buffer size from existing allocation
        VkMemoryRequirements memReqs;
        vkGetBufferMemoryRequirements(g_dev.device, g_materialBuffer, &memReqs);

        VkDescriptorBufferInfo matBufInfo = {};
        matBufInfo.buffer = g_materialBuffer;
        matBufInfo.range = memReqs.size;

        VkWriteDescriptorSet writeMat = {VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET};
        writeMat.dstSet = g_rtPipeline.descSet;
        writeMat.dstBinding = 3;
        writeMat.descriptorCount = 1;
        writeMat.descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
        writeMat.pBufferInfo = &matBufInfo;
        vkUpdateDescriptorSets(g_dev.device, 1, &writeMat, 0, nullptr);
    }

    env->ReleaseStringUTFChars(shaderPackPath, path);
    printf("[Lumen native] nativeLoadShaderPack: loaded successfully from %s\n", path);
    return JNI_TRUE;
}

// ---- HDR JNI (stubs - return false until HDR is fully integrated) ----

JNIEXPORT jboolean JNICALL
Java_com_luci_lumen_vk_LumenNativeBridge_nativeDetectHdr(
    JNIEnv* env, jclass cls, jlong, jlong)
{
    return JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_luci_lumen_vk_LumenNativeBridge_nativeInitHdr(
    JNIEnv* env, jclass cls, jlong, jlong, jint, jint, jfloat, jfloat)
{
    return JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_luci_lumen_vk_LumenNativeBridge_nativeShutdownHdr(
    JNIEnv* env, jclass cls)
{
}

// ---- OIDN Denoiser JNI ----

static OidnDenoiser* g_denoiser = nullptr;

JNIEXPORT jboolean JNICALL
Java_com_luci_lumen_vk_LumenNativeBridge_nativeInitDenoiser(
    JNIEnv* env, jclass cls, jint width, jint height, jboolean useGPU)
{
    if (g_denoiser) {
        delete g_denoiser;
        g_denoiser = nullptr;
    }

    g_denoiser = new OidnDenoiser();
    bool ok = g_denoiser->init((int)width, (int)height, useGPU != JNI_FALSE);
    if (!ok) {
        printf("[Lumen] OIDN init failed: %s\n", g_denoiser->lastError());
        delete g_denoiser;
        g_denoiser = nullptr;
        return JNI_FALSE;
    }

    printf("[Lumen] OIDN denoiser initialized\n");
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_luci_lumen_vk_LumenNativeBridge_nativeDenoiseImage(
    JNIEnv* env, jclass cls, jintArray pixels, jint width, jint height)
{
    if (!g_denoiser || !g_denoiser->isReady()) return JNI_FALSE;

    jsize len = env->GetArrayLength(pixels);
    jint* pixelData = env->GetIntArrayElements(pixels, nullptr);

    bool ok = g_denoiser->denoise(reinterpret_cast<uint32_t*>(pixelData),
                                   (int)width, (int)height);

    env->ReleaseIntArrayElements(pixels, pixelData, 0); // 0 = copy back
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_luci_lumen_vk_LumenNativeBridge_nativeShutdownDenoiser(
    JNIEnv* env, jclass cls)
{
    if (g_denoiser) {
        g_denoiser->shutdown();
        delete g_denoiser;
        g_denoiser = nullptr;
        printf("[Lumen] OIDN denoiser shutdown\n");
    }
}

} // extern "C"
