#pragma once
#include <vulkan/vulkan.h>

struct LumenOutputImage {
    VkImage image = VK_NULL_HANDLE;
    VkDeviceMemory memory = VK_NULL_HANDLE;
    VkImageView view = VK_NULL_HANDLE;
    uint32_t width = 0;
    uint32_t height = 0;
    uint32_t frameCount = 0;

    bool init(VkDevice device, VkPhysicalDevice physDev, uint32_t w, uint32_t h);
    bool resize(VkDevice device, VkPhysicalDevice physDev, uint32_t w, uint32_t h);
    void destroy(VkDevice device);
};
