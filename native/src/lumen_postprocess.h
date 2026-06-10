#pragma once
#include <vulkan/vulkan.h>

struct LumenPostProcessParams {
    float brightness;
    float contrast;
    float saturation;
    float vibrance;
    float temperature;
    float tint;
    float sharpness;
    float filmGrain;
    float vignette;
    float exposure;
    float shadows;
    float highlights;
    float whites;
    float blacks;
    float clarity;
    float dehaze;
    int32_t tonemapCurve;
    uint32_t frameCount;
    float _pad0, _pad1;
};

struct LumenPostProcess {
    VkDevice device = VK_NULL_HANDLE;
    VkPipeline pipeline = VK_NULL_HANDLE;
    VkPipelineLayout pipelineLayout = VK_NULL_HANDLE;
    VkDescriptorSetLayout descSetLayout = VK_NULL_HANDLE;
    VkDescriptorPool descPool = VK_NULL_HANDLE;
    VkDescriptorSet descSet = VK_NULL_HANDLE;
    VkBuffer paramsBuffer = VK_NULL_HANDLE;
    VkDeviceMemory paramsMemory = VK_NULL_HANDLE;
    bool initialized = false;

    bool init(VkDevice device, VkPhysicalDevice physDev);
    bool dispatch(VkCommandBuffer cmdBuf, VkImageView inputView, VkImageView outputView,
                  uint32_t width, uint32_t height, const LumenPostProcessParams& params);
    void destroy();
};
