#include "lumen_device.h"
#include <cstdio>
#include <cstring>

bool lumen_query_device(VkInstance instance, VkDevice device, LumenDevice* out) {
    if (!instance || !device || !out) return false;

    out->instance = instance;
    out->device = device;

    VkPhysicalDevice physicalDevice = VK_NULL_HANDLE;
    uint32_t count = 0;
    vkEnumeratePhysicalDevices(instance, &count, nullptr);
    if (count == 0) return false;

    VkPhysicalDevice physDevices[8];
    count = (count > 8) ? 8 : count;
    vkEnumeratePhysicalDevices(instance, &count, physDevices);
    physicalDevice = physDevices[0];
    out->physicalDevice = physicalDevice;

    VkPhysicalDeviceProperties props{};
    vkGetPhysicalDeviceProperties(physicalDevice, &props);
    strncpy(out->deviceName, props.deviceName, sizeof(out->deviceName) - 1);
    out->vendorID = props.vendorID;
    out->deviceID = props.deviceID;

    // Get queue families
    uint32_t queueCount = 0;
    vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, &queueCount, nullptr);
    if (queueCount > 0) {
        VkQueueFamilyProperties* queues = new VkQueueFamilyProperties[queueCount];
        vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, &queueCount, queues);
        for (uint32_t i = 0; i < queueCount; i++) {
            if (queues[i].queueFlags & VK_QUEUE_GRAPHICS_BIT && out->graphicsQueueFamily == UINT32_MAX)
                out->graphicsQueueFamily = i;
            if (queues[i].queueFlags & VK_QUEUE_COMPUTE_BIT && out->computeQueueFamily == UINT32_MAX)
                out->computeQueueFamily = i;
            if (queues[i].queueFlags & VK_QUEUE_TRANSFER_BIT && out->transferQueueFamily == UINT32_MAX)
                out->transferQueueFamily = i;
        }
        delete[] queues;
    }

    if (out->graphicsQueueFamily != UINT32_MAX) {
        vkGetDeviceQueue(device, out->graphicsQueueFamily, 0, &out->graphicsQueue);
    }
    if (out->computeQueueFamily != UINT32_MAX) {
        vkGetDeviceQueue(device, out->computeQueueFamily, 0, &out->computeQueue);
    }

    // Check ray tracing extensions
    uint32_t extCount = 0;
    vkEnumerateDeviceExtensionProperties(physicalDevice, nullptr, &extCount, nullptr);
    if (extCount > 0) {
        VkExtensionProperties* exts = new VkExtensionProperties[extCount];
        vkEnumerateDeviceExtensionProperties(physicalDevice, nullptr, &extCount, exts);
        for (uint32_t i = 0; i < extCount; i++) {
            if (strcmp(exts[i].extensionName, VK_KHR_RAY_TRACING_PIPELINE_EXTENSION_NAME) == 0)
                out->supportsRayTracingPipeline = true;
            if (strcmp(exts[i].extensionName, VK_KHR_RAY_QUERY_EXTENSION_NAME) == 0)
                out->supportsRayQuery = true;
            if (strcmp(exts[i].extensionName, VK_EXT_RAY_TRACING_INVOCATION_REORDER_EXTENSION_NAME) == 0)
                out->supportsShaderExecutionReordering = true;
        }
        delete[] exts;
    }

    printf("[Lumen device] %s | RT-pipeline=%s RayQuery=%s SER=%s\n",
           out->deviceName,
           out->supportsRayTracingPipeline ? "YES" : "no",
           out->supportsRayQuery ? "YES" : "no",
           out->supportsShaderExecutionReordering ? "YES" : "no");

    return true;
}
