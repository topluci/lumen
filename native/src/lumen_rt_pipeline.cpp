#include "lumen_rt_pipeline.h"
#include "shaders/raygen_spv.h"
#include "shaders/closesthit_spv.h"
#include "shaders/miss_spv.h"
#include <cstdio>
#include <cstring>
#include <cstdlib>

static VkShaderModule createShaderModule(VkDevice device, const uint32_t* code, uint32_t size) {
    VkShaderModuleCreateInfo info = {VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO};
    info.codeSize = size * sizeof(uint32_t);
    info.pCode = code;
    VkShaderModule module;
    if (vkCreateShaderModule(device, &info, nullptr, &module) != VK_SUCCESS) {
        fprintf(stderr, "[Lumen RT] Failed to create shader module\n");
        return VK_NULL_HANDLE;
    }
    return module;
}

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

bool LumenRTPipeline::init(VkDevice device, VkPhysicalDevice physDev,
                            const LumenAccelStruct* tlas,
                            LumenOutputImage* outputImage) {
    auto vkCreateRTPipelines = (PFN_vkCreateRayTracingPipelinesKHR)
        vkGetDeviceProcAddr(device, "vkCreateRayTracingPipelinesKHR");
    auto vkGetRTShaderGroupHandles = (PFN_vkGetRayTracingShaderGroupHandlesKHR)
        vkGetDeviceProcAddr(device, "vkGetRayTracingShaderGroupHandlesKHR");
    auto vkCmdTraceRays = (PFN_vkCmdTraceRaysKHR)
        vkGetDeviceProcAddr(device, "vkCmdTraceRaysKHR");

    if (!vkCreateRTPipelines || !vkGetRTShaderGroupHandles || !vkCmdTraceRays) {
        fprintf(stderr, "[Lumen RT] Ray tracing function pointers not available\n");
        return false;
    }

    // --- Descriptor set layout: output image (binding=0), TLAS (binding=1), params (binding=2) ---
    VkDescriptorSetLayoutBinding bindings[3] = {};
    bindings[0].binding = 0;
    bindings[0].descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_IMAGE;
    bindings[0].descriptorCount = 1;
    bindings[0].stageFlags = VK_SHADER_STAGE_RAYGEN_BIT_KHR;

    bindings[1].binding = 1;
    bindings[1].descriptorType = VK_DESCRIPTOR_TYPE_ACCELERATION_STRUCTURE_KHR;
    bindings[1].descriptorCount = 1;
    bindings[1].stageFlags = VK_SHADER_STAGE_RAYGEN_BIT_KHR;

    bindings[2].binding = 2;
    bindings[2].descriptorType = VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER;
    bindings[2].descriptorCount = 1;
    bindings[2].stageFlags = VK_SHADER_STAGE_RAYGEN_BIT_KHR;

    VkDescriptorSetLayoutCreateInfo dslInfo = {VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO};
    dslInfo.bindingCount = 3;
    dslInfo.pBindings = bindings;
    if (vkCreateDescriptorSetLayout(device, &dslInfo, nullptr, &descSetLayout) != VK_SUCCESS) {
        fprintf(stderr, "[Lumen RT] Failed to create descriptor set layout\n");
        return false;
    }

    // --- Pipeline layout ---
    VkPipelineLayoutCreateInfo plInfo = {VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO};
    plInfo.setLayoutCount = 1;
    plInfo.pSetLayouts = &descSetLayout;
    if (vkCreatePipelineLayout(device, &plInfo, nullptr, &pipelineLayout) != VK_SUCCESS) {
        fprintf(stderr, "[Lumen RT] Failed to create pipeline layout\n");
        return false;
    }

    // --- Descriptor pool + set ---
    VkDescriptorPoolSize poolSizes[4] = {};
    poolSizes[0].type = VK_DESCRIPTOR_TYPE_STORAGE_IMAGE;
    poolSizes[0].descriptorCount = 1;
    poolSizes[1].type = VK_DESCRIPTOR_TYPE_ACCELERATION_STRUCTURE_KHR;
    poolSizes[1].descriptorCount = 1;
    poolSizes[2].type = VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER;
    poolSizes[2].descriptorCount = 1;
    poolSizes[3].type = VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
    poolSizes[3].descriptorCount = 1;

    VkDescriptorPoolCreateInfo dpInfo = {VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO};
    dpInfo.maxSets = 1;
    dpInfo.poolSizeCount = 4;
    dpInfo.pPoolSizes = poolSizes;
    if (vkCreateDescriptorPool(device, &dpInfo, nullptr, &descPool) != VK_SUCCESS) {
        fprintf(stderr, "[Lumen RT] Failed to create descriptor pool\n");
        return false;
    }

    VkDescriptorSetAllocateInfo dsAlloc = {VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO};
    dsAlloc.descriptorPool = descPool;
    dsAlloc.descriptorSetCount = 1;
    dsAlloc.pSetLayouts = &descSetLayout;
    if (vkAllocateDescriptorSets(device, &dsAlloc, &descSet) != VK_SUCCESS) {
        fprintf(stderr, "[Lumen RT] Failed to allocate descriptor set\n");
        return false;
    }

    // Write storage image descriptor
    VkDescriptorImageInfo imgInfo = {};
    imgInfo.imageView = outputImage->view;
    imgInfo.imageLayout = VK_IMAGE_LAYOUT_GENERAL;

    VkWriteDescriptorSet writeImg = {VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET};
    writeImg.dstSet = descSet;
    writeImg.dstBinding = 0;
    writeImg.descriptorCount = 1;
    writeImg.descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_IMAGE;
    writeImg.pImageInfo = &imgInfo;
    vkUpdateDescriptorSets(device, 1, &writeImg, 0, nullptr);

    // Write TLAS descriptor
    VkWriteDescriptorSetAccelerationStructureKHR writeAS = {
        VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET_ACCELERATION_STRUCTURE_KHR};
    writeAS.accelerationStructureCount = 1;
    writeAS.pAccelerationStructures = &tlas->handle;

    VkWriteDescriptorSet writeTLAS = {VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET};
    writeTLAS.pNext = &writeAS;
    writeTLAS.dstSet = descSet;
    writeTLAS.dstBinding = 1;
    writeTLAS.descriptorCount = 1;
    writeTLAS.descriptorType = VK_DESCRIPTOR_TYPE_ACCELERATION_STRUCTURE_KHR;
    vkUpdateDescriptorSets(device, 1, &writeTLAS, 0, nullptr);

    // --- Shader modules ---
    VkShaderModule raygenMod = createShaderModule(device, g_raygen_spv, g_raygen_spv_size);
    VkShaderModule closesthitMod = createShaderModule(device, g_closesthit_spv, g_closesthit_spv_size);
    VkShaderModule missMod = createShaderModule(device, g_miss_spv, g_miss_spv_size);
    if (!raygenMod || !closesthitMod || !missMod) return false;

    VkPipelineShaderStageCreateInfo stages[3] = {};
    stages[0].sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
    stages[0].stage = VK_SHADER_STAGE_RAYGEN_BIT_KHR;
    stages[0].module = raygenMod;
    stages[0].pName = "main";

    stages[1].sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
    stages[1].stage = VK_SHADER_STAGE_CLOSEST_HIT_BIT_KHR;
    stages[1].module = closesthitMod;
    stages[1].pName = "main";

    stages[2].sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
    stages[2].stage = VK_SHADER_STAGE_MISS_BIT_KHR;
    stages[2].module = missMod;
    stages[2].pName = "main";

    VkRayTracingShaderGroupCreateInfoKHR groups[3] = {};
    groups[0].sType = VK_STRUCTURE_TYPE_RAY_TRACING_SHADER_GROUP_CREATE_INFO_KHR;
    groups[0].type = VK_RAY_TRACING_SHADER_GROUP_TYPE_GENERAL_KHR;
    groups[0].generalShader = 0;
    groups[0].closestHitShader = VK_SHADER_UNUSED_KHR;
    groups[0].anyHitShader = VK_SHADER_UNUSED_KHR;
    groups[0].intersectionShader = VK_SHADER_UNUSED_KHR;

    groups[1].sType = VK_STRUCTURE_TYPE_RAY_TRACING_SHADER_GROUP_CREATE_INFO_KHR;
    groups[1].type = VK_RAY_TRACING_SHADER_GROUP_TYPE_GENERAL_KHR;
    groups[1].generalShader = 2;
    groups[1].closestHitShader = VK_SHADER_UNUSED_KHR;
    groups[1].anyHitShader = VK_SHADER_UNUSED_KHR;
    groups[1].intersectionShader = VK_SHADER_UNUSED_KHR;

    groups[2].sType = VK_STRUCTURE_TYPE_RAY_TRACING_SHADER_GROUP_CREATE_INFO_KHR;
    groups[2].type = VK_RAY_TRACING_SHADER_GROUP_TYPE_TRIANGLES_HIT_GROUP_KHR;
    groups[2].generalShader = VK_SHADER_UNUSED_KHR;
    groups[2].closestHitShader = 1;
    groups[2].anyHitShader = VK_SHADER_UNUSED_KHR;
    groups[2].intersectionShader = VK_SHADER_UNUSED_KHR;

    raygenIndex = 0;
    missIndex = 1;
    closestHitIndex = 2;

    VkRayTracingPipelineCreateInfoKHR rtInfo = {VK_STRUCTURE_TYPE_RAY_TRACING_PIPELINE_CREATE_INFO_KHR};
    rtInfo.stageCount = 3;
    rtInfo.pStages = stages;
    rtInfo.groupCount = 3;
    rtInfo.pGroups = groups;
    rtInfo.maxPipelineRayRecursionDepth = 4;
    rtInfo.layout = pipelineLayout;

    VkDeferredOperationKHR deferredOp = VK_NULL_HANDLE;
    VkPipelineCache pipelineCache = VK_NULL_HANDLE;
    if (vkCreateRTPipelines(device, deferredOp, pipelineCache, 1, &rtInfo, nullptr, &pipeline) != VK_SUCCESS) {
        fprintf(stderr, "[Lumen RT] Failed to create ray tracing pipeline\n");
        return false;
    }

    vkDestroyShaderModule(device, raygenMod, nullptr);
    vkDestroyShaderModule(device, closesthitMod, nullptr);
    vkDestroyShaderModule(device, missMod, nullptr);

    // --- Shader binding table ---
    VkPhysicalDeviceRayTracingPipelinePropertiesKHR rtProps = {
        VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_RAY_TRACING_PIPELINE_PROPERTIES_KHR};
    VkPhysicalDeviceProperties2 props2 = {VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PROPERTIES_2};
    props2.pNext = &rtProps;
    vkGetPhysicalDeviceProperties2(physDev, &props2);

    sbtHandleSize = rtProps.shaderGroupHandleSize;
    uint32_t sbtBaseAlignment = rtProps.shaderGroupBaseAlignment;
    uint32_t groupCount = 3;

    uint32_t handleArraySize = groupCount * sbtHandleSize;
    uint8_t* handles = (uint8_t*)malloc(handleArraySize);
    vkGetRTShaderGroupHandles(device, pipeline, 0, groupCount, handleArraySize, handles);

    uint32_t groupSize = ((sbtHandleSize + sbtBaseAlignment - 1) / sbtBaseAlignment) * sbtBaseAlignment;

    raygenSBT.stride = sbtBaseAlignment;
    raygenSBT.size = sbtBaseAlignment;

    missSBT.stride = groupSize;
    missSBT.size = groupSize;

    hitSBT.stride = groupSize;
    hitSBT.size = groupSize;

    uint32_t sbtSize = raygenSBT.size + missSBT.size + hitSBT.size;

    VkBufferCreateInfo bufInfo = {VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO};
    bufInfo.size = sbtSize;
    bufInfo.usage = VK_BUFFER_USAGE_SHADER_BINDING_TABLE_BIT_KHR |
                    VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT |
                    VK_BUFFER_USAGE_TRANSFER_DST_BIT;
    bufInfo.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
    vkCreateBuffer(device, &bufInfo, nullptr, &sbtBuffer);

    VkMemoryRequirements memReqs;
    vkGetBufferMemoryRequirements(device, sbtBuffer, &memReqs);

    VkMemoryAllocateInfo allocInfo = {VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO};
    allocInfo.allocationSize = memReqs.size;
    allocInfo.memoryTypeIndex = findMemoryType(physDev, memReqs.memoryTypeBits,
        VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
    vkAllocateMemory(device, &allocInfo, nullptr, &sbtMemory);
    vkBindBufferMemory(device, sbtBuffer, sbtMemory, 0);

    auto vkGetBufAddr = (PFN_vkGetBufferDeviceAddressKHR)
        vkGetDeviceProcAddr(device, "vkGetBufferDeviceAddressKHR");
    if (!vkGetBufAddr) {
        fprintf(stderr, "[Lumen RT] vkGetBufferDeviceAddress not available\n");
        return false;
    }

    VkBufferDeviceAddressInfo addrInfo = {VK_STRUCTURE_TYPE_BUFFER_DEVICE_ADDRESS_INFO};
    addrInfo.buffer = sbtBuffer;
    VkDeviceAddress sbtAddress = vkGetBufAddr(device, &addrInfo);

    uint8_t* mapped;
    vkMapMemory(device, sbtMemory, 0, sbtSize, 0, (void**)&mapped);
    memcpy(mapped, handles, sbtHandleSize);
    memcpy(mapped + raygenSBT.size, handles + 1 * sbtHandleSize, sbtHandleSize);
    memcpy(mapped + raygenSBT.size + groupSize, handles + 2 * sbtHandleSize, sbtHandleSize);
    vkUnmapMemory(device, sbtMemory);

    raygenSBT.deviceAddress = sbtAddress;
    missSBT.deviceAddress = sbtAddress + raygenSBT.size;
    hitSBT.deviceAddress = sbtAddress + raygenSBT.size + groupSize;

    fprintf(stderr, "[Lumen RT] Pipeline + SBT ready\n");
    free(handles);
    return true;
}

bool LumenRTPipeline::reloadShaders(VkDevice device, VkPhysicalDevice physDev,
                                    LumenOutputImage* outputImage,
                                    const uint32_t* raygenCode, uint32_t raygenSize,
                                    const uint32_t* hitCode, uint32_t hitSize,
                                    const uint32_t* missCode, uint32_t missSize) {
    auto vkCreateRTPipelines = (PFN_vkCreateRayTracingPipelinesKHR)
        vkGetDeviceProcAddr(device, "vkCreateRayTracingPipelinesKHR");
    auto vkGetRTShaderGroupHandles = (PFN_vkGetRayTracingShaderGroupHandlesKHR)
        vkGetDeviceProcAddr(device, "vkGetRayTracingShaderGroupHandlesKHR");
    auto vkCmdTraceRays = (PFN_vkCmdTraceRaysKHR)
        vkGetDeviceProcAddr(device, "vkCmdTraceRaysKHR");

    if (!vkCreateRTPipelines || !vkGetRTShaderGroupHandles || !vkCmdTraceRays) {
        fprintf(stderr, "[Lumen RT] reload: RT function pointers not available\n");
        return false;
    }

    // Destroy old pipeline + SBT (keep descriptor infrastructure)
    if (pipeline) { vkDestroyPipeline(device, pipeline, nullptr); pipeline = VK_NULL_HANDLE; }
    if (sbtBuffer) { vkDestroyBuffer(device, sbtBuffer, nullptr); sbtBuffer = VK_NULL_HANDLE; }
    if (sbtMemory) { vkFreeMemory(device, sbtMemory, nullptr); sbtMemory = VK_NULL_HANDLE; }

    // Create new shader modules
    VkShaderModule raygenMod = createShaderModule(device, raygenCode, raygenSize);
    VkShaderModule hitMod = createShaderModule(device, hitCode, hitSize);
    VkShaderModule missMod = createShaderModule(device, missCode, missSize);
    if (!raygenMod || !hitMod || !missMod) return false;

    VkPipelineShaderStageCreateInfo stages[3] = {};
    stages[0].sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
    stages[0].stage = VK_SHADER_STAGE_RAYGEN_BIT_KHR;
    stages[0].module = raygenMod;
    stages[0].pName = "main";

    stages[1].sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
    stages[1].stage = VK_SHADER_STAGE_CLOSEST_HIT_BIT_KHR;
    stages[1].module = hitMod;
    stages[1].pName = "main";

    stages[2].sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
    stages[2].stage = VK_SHADER_STAGE_MISS_BIT_KHR;
    stages[2].module = missMod;
    stages[2].pName = "main";

    VkRayTracingShaderGroupCreateInfoKHR groups[3] = {};
    groups[0].sType = VK_STRUCTURE_TYPE_RAY_TRACING_SHADER_GROUP_CREATE_INFO_KHR;
    groups[0].type = VK_RAY_TRACING_SHADER_GROUP_TYPE_GENERAL_KHR;
    groups[0].generalShader = 0;
    groups[0].closestHitShader = VK_SHADER_UNUSED_KHR;
    groups[0].anyHitShader = VK_SHADER_UNUSED_KHR;
    groups[0].intersectionShader = VK_SHADER_UNUSED_KHR;

    groups[1].sType = VK_STRUCTURE_TYPE_RAY_TRACING_SHADER_GROUP_CREATE_INFO_KHR;
    groups[1].type = VK_RAY_TRACING_SHADER_GROUP_TYPE_GENERAL_KHR;
    groups[1].generalShader = 2;
    groups[1].closestHitShader = VK_SHADER_UNUSED_KHR;
    groups[1].anyHitShader = VK_SHADER_UNUSED_KHR;
    groups[1].intersectionShader = VK_SHADER_UNUSED_KHR;

    groups[2].sType = VK_STRUCTURE_TYPE_RAY_TRACING_SHADER_GROUP_CREATE_INFO_KHR;
    groups[2].type = VK_RAY_TRACING_SHADER_GROUP_TYPE_TRIANGLES_HIT_GROUP_KHR;
    groups[2].generalShader = VK_SHADER_UNUSED_KHR;
    groups[2].closestHitShader = 1;
    groups[2].anyHitShader = VK_SHADER_UNUSED_KHR;
    groups[2].intersectionShader = VK_SHADER_UNUSED_KHR;

    VkRayTracingPipelineCreateInfoKHR rtInfo = {VK_STRUCTURE_TYPE_RAY_TRACING_PIPELINE_CREATE_INFO_KHR};
    rtInfo.stageCount = 3;
    rtInfo.pStages = stages;
    rtInfo.groupCount = 3;
    rtInfo.pGroups = groups;
    rtInfo.maxPipelineRayRecursionDepth = 4;
    rtInfo.layout = pipelineLayout;

    VkDeferredOperationKHR deferredOp = VK_NULL_HANDLE;
    VkPipelineCache pipelineCache = VK_NULL_HANDLE;
    if (vkCreateRTPipelines(device, deferredOp, pipelineCache, 1, &rtInfo, nullptr, &pipeline) != VK_SUCCESS) {
        fprintf(stderr, "[Lumen RT] reload: failed to create pipeline\n");
        vkDestroyShaderModule(device, raygenMod, nullptr);
        vkDestroyShaderModule(device, hitMod, nullptr);
        vkDestroyShaderModule(device, missMod, nullptr);
        return false;
    }

    vkDestroyShaderModule(device, raygenMod, nullptr);
    vkDestroyShaderModule(device, hitMod, nullptr);
    vkDestroyShaderModule(device, missMod, nullptr);

    // Rebuild SBT
    VkPhysicalDeviceRayTracingPipelinePropertiesKHR rtProps = {
        VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_RAY_TRACING_PIPELINE_PROPERTIES_KHR};
    VkPhysicalDeviceProperties2 props2 = {VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PROPERTIES_2};
    props2.pNext = &rtProps;
    vkGetPhysicalDeviceProperties2(physDev, &props2);

    sbtHandleSize = rtProps.shaderGroupHandleSize;
    uint32_t sbtBaseAlignment = rtProps.shaderGroupBaseAlignment;
    uint32_t groupCount = 3;

    uint32_t handleArraySize = groupCount * sbtHandleSize;
    uint8_t* handles = (uint8_t*)malloc(handleArraySize);
    vkGetRTShaderGroupHandles(device, pipeline, 0, groupCount, handleArraySize, handles);

    uint32_t groupSize = ((sbtHandleSize + sbtBaseAlignment - 1) / sbtBaseAlignment) * sbtBaseAlignment;

    raygenSBT.stride = sbtBaseAlignment;
    raygenSBT.size = sbtBaseAlignment;
    missSBT.stride = groupSize;
    missSBT.size = groupSize;
    hitSBT.stride = groupSize;
    hitSBT.size = groupSize;

    uint32_t sbtSize = raygenSBT.size + missSBT.size + hitSBT.size;

    VkBufferCreateInfo bufInfo = {VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO};
    bufInfo.size = sbtSize;
    bufInfo.usage = VK_BUFFER_USAGE_SHADER_BINDING_TABLE_BIT_KHR |
                    VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT |
                    VK_BUFFER_USAGE_TRANSFER_DST_BIT;
    bufInfo.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
    vkCreateBuffer(device, &bufInfo, nullptr, &sbtBuffer);

    VkMemoryRequirements memReqs;
    vkGetBufferMemoryRequirements(device, sbtBuffer, &memReqs);

    VkMemoryAllocateInfo allocInfo = {VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO};
    allocInfo.allocationSize = memReqs.size;
    allocInfo.memoryTypeIndex = findMemoryType(physDev, memReqs.memoryTypeBits,
        VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
    vkAllocateMemory(device, &allocInfo, nullptr, &sbtMemory);
    vkBindBufferMemory(device, sbtBuffer, sbtMemory, 0);

    auto vkGetBufAddr = (PFN_vkGetBufferDeviceAddressKHR)
        vkGetDeviceProcAddr(device, "vkGetBufferDeviceAddressKHR");
    if (!vkGetBufAddr) {
        fprintf(stderr, "[Lumen RT] reload: vkGetBufferDeviceAddress not available\n");
        free(handles);
        return false;
    }

    VkBufferDeviceAddressInfo addrInfo = {VK_STRUCTURE_TYPE_BUFFER_DEVICE_ADDRESS_INFO};
    addrInfo.buffer = sbtBuffer;
    VkDeviceAddress sbtAddress = vkGetBufAddr(device, &addrInfo);

    uint8_t* mapped;
    vkMapMemory(device, sbtMemory, 0, sbtSize, 0, (void**)&mapped);
    memcpy(mapped, handles, sbtHandleSize);
    memcpy(mapped + raygenSBT.size, handles + 1 * sbtHandleSize, sbtHandleSize);
    memcpy(mapped + raygenSBT.size + groupSize, handles + 2 * sbtHandleSize, sbtHandleSize);
    vkUnmapMemory(device, sbtMemory);

    raygenSBT.deviceAddress = sbtAddress;
    missSBT.deviceAddress = sbtAddress + raygenSBT.size;
    hitSBT.deviceAddress = sbtAddress + raygenSBT.size + groupSize;

    free(handles);

    // Re-write storage image descriptor (binding 0)
    VkDescriptorImageInfo imgInfo = {};
    imgInfo.imageView = outputImage->view;
    imgInfo.imageLayout = VK_IMAGE_LAYOUT_GENERAL;

    VkWriteDescriptorSet writeImg = {VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET};
    writeImg.dstSet = descSet;
    writeImg.dstBinding = 0;
    writeImg.descriptorCount = 1;
    writeImg.descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_IMAGE;
    writeImg.pImageInfo = &imgInfo;
    vkUpdateDescriptorSets(device, 1, &writeImg, 0, nullptr);

    fprintf(stderr, "[Lumen RT] reload: shaders reloaded successfully\n");
    return true;
}

void LumenRTPipeline::destroy(VkDevice device) {
    if (pipeline) vkDestroyPipeline(device, pipeline, nullptr);
    if (pipelineLayout) vkDestroyPipelineLayout(device, pipelineLayout, nullptr);
    if (descSetLayout) vkDestroyDescriptorSetLayout(device, descSetLayout, nullptr);
    if (descPool) vkDestroyDescriptorPool(device, descPool, nullptr);
    if (sbtBuffer) vkDestroyBuffer(device, sbtBuffer, nullptr);
    if (sbtMemory) vkFreeMemory(device, sbtMemory, nullptr);
}
