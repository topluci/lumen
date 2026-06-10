#pragma once
#include <vulkan/vulkan.h>
#include "lumen_device.h"

struct LumenAccelStruct {
    VkAccelerationStructureKHR handle = VK_NULL_HANDLE;
    VkBuffer buffer = VK_NULL_HANDLE;
    VkDeviceMemory memory = VK_NULL_HANDLE;
    uint64_t deviceAddress = 0;
    bool isTopLevel = false;

    void destroy(VkDevice device);
};

struct LumenASBuilder {
    VkDevice device = VK_NULL_HANDLE;
    VkPhysicalDevice physDev = VK_NULL_HANDLE;
    VkCommandPool cmdPool = VK_NULL_HANDLE;
    VkQueue queue = VK_NULL_HANDLE;

    LumenAccelStruct* buildBottomLevel(const float* vertices, uint32_t vertexCount,
                                       const uint32_t* indices, uint32_t indexCount);
    LumenAccelStruct* buildTopLevel(LumenAccelStruct* blas, uint32_t instanceCount);

    void destroy();
    void init(VkDevice device, VkPhysicalDevice physDev, uint32_t queueFamily, VkQueue queue);
};
