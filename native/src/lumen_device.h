#pragma once
#include <vulkan/vulkan.h>

bool lumen_query_device(VkInstance instance, VkDevice device, struct LumenDevice* out);

struct LumenDevice {
    VkInstance instance = VK_NULL_HANDLE;
    VkPhysicalDevice physicalDevice = VK_NULL_HANDLE;
    VkDevice device = VK_NULL_HANDLE;
    uint32_t graphicsQueueFamily = UINT32_MAX;
    uint32_t computeQueueFamily = UINT32_MAX;
    uint32_t transferQueueFamily = UINT32_MAX;
    VkQueue graphicsQueue = VK_NULL_HANDLE;
    VkQueue computeQueue = VK_NULL_HANDLE;

    bool supportsRayTracingPipeline = false;
    bool supportsRayQuery = false;
    bool supportsShaderExecutionReordering = false;

    char deviceName[256]{};
    uint32_t vendorID = 0;
    uint32_t deviceID = 0;
};
