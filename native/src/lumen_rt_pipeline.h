#pragma once
#include <vulkan/vulkan.h>
#include "lumen_as.h"
#include "lumen_output_image.h"

struct LumenRTPipeline {
    VkPipeline pipeline = VK_NULL_HANDLE;
    VkPipelineLayout pipelineLayout = VK_NULL_HANDLE;
    VkDescriptorSetLayout descSetLayout = VK_NULL_HANDLE;
    VkDescriptorPool descPool = VK_NULL_HANDLE;
    VkDescriptorSet descSet = VK_NULL_HANDLE;

    VkBuffer sbtBuffer = VK_NULL_HANDLE;
    VkDeviceMemory sbtMemory = VK_NULL_HANDLE;
    VkStridedDeviceAddressRegionKHR raygenSBT = {};
    VkStridedDeviceAddressRegionKHR missSBT = {};
    VkStridedDeviceAddressRegionKHR hitSBT = {};
    VkStridedDeviceAddressRegionKHR callableSBT = {};

    uint32_t raygenIndex = 0;
    uint32_t missIndex = 1;
    uint32_t closestHitIndex = 2;
    uint32_t sbtHandleSize = 0;

    void destroy(VkDevice device);
    bool init(VkDevice device, VkPhysicalDevice physDev, const LumenAccelStruct* tlas,
              LumenOutputImage* outputImage);

    bool reloadShaders(VkDevice device, VkPhysicalDevice physDev,
                       LumenOutputImage* outputImage,
                       const uint32_t* raygenCode, uint32_t raygenSize,
                       const uint32_t* hitCode, uint32_t hitSize,
                       const uint32_t* missCode, uint32_t missSize);
};
