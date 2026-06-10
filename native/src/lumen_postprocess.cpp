#include "lumen_postprocess.h"
#include "shaders/postprocess_spv.h"
#include <cstdio>
#include <cstring>

static uint32_t findMemoryTypePP(VkPhysicalDevice physDev, uint32_t typeFilter,
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

bool LumenPostProcess::init(VkDevice dev, VkPhysicalDevice physDev) {
    device = dev;

    // Descriptor set layout: input (storage), output (storage)
    VkDescriptorSetLayoutBinding bindings[2] = {};
    bindings[0].binding = 0;
    bindings[0].descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_IMAGE;
    bindings[0].descriptorCount = 1;
    bindings[0].stageFlags = VK_SHADER_STAGE_COMPUTE_BIT;

    bindings[1].binding = 1;
    bindings[1].descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_IMAGE;
    bindings[1].descriptorCount = 1;
    bindings[1].stageFlags = VK_SHADER_STAGE_COMPUTE_BIT;

    VkDescriptorSetLayoutCreateInfo dslInfo = {VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO};
    dslInfo.bindingCount = 2;
    dslInfo.pBindings = bindings;
    vkCreateDescriptorSetLayout(device, &dslInfo, nullptr, &descSetLayout);

    // Pipeline layout with push constants
    VkPushConstantRange pushRange = {};
    pushRange.stageFlags = VK_SHADER_STAGE_COMPUTE_BIT;
    pushRange.size = sizeof(LumenPostProcessParams);
    pushRange.offset = 0;

    VkPipelineLayoutCreateInfo plInfo = {VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO};
    plInfo.setLayoutCount = 1;
    plInfo.pSetLayouts = &descSetLayout;
    plInfo.pushConstantRangeCount = 1;
    plInfo.pPushConstantRanges = &pushRange;
    vkCreatePipelineLayout(device, &plInfo, nullptr, &pipelineLayout);

    // Shader module
    VkShaderModuleCreateInfo smInfo = {VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO};
    smInfo.codeSize = postprocess_spv_size;
    smInfo.pCode = (const uint32_t*)postprocess_spv_data;
    VkShaderModule shaderModule;
    vkCreateShaderModule(device, &smInfo, nullptr, &shaderModule);

    // Compute pipeline
    VkComputePipelineCreateInfo cpInfo = {VK_STRUCTURE_TYPE_COMPUTE_PIPELINE_CREATE_INFO};
    cpInfo.stage = {VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO};
    cpInfo.stage.stage = VK_SHADER_STAGE_COMPUTE_BIT;
    cpInfo.stage.module = shaderModule;
    cpInfo.stage.pName = "main";
    cpInfo.layout = pipelineLayout;

    vkCreateComputePipelines(device, VK_NULL_HANDLE, 1, &cpInfo, nullptr, &pipeline);
    vkDestroyShaderModule(device, shaderModule, nullptr);

    // Descriptor pool
    VkDescriptorPoolSize poolSizes[1] = {};
    poolSizes[0].type = VK_DESCRIPTOR_TYPE_STORAGE_IMAGE;
    poolSizes[0].descriptorCount = 2;

    VkDescriptorPoolCreateInfo dpInfo = {VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO};
    dpInfo.maxSets = 1;
    dpInfo.poolSizeCount = 1;
    dpInfo.pPoolSizes = poolSizes;
    vkCreateDescriptorPool(device, &dpInfo, nullptr, &descPool);

    VkDescriptorSetAllocateInfo allocInfo = {VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO};
    allocInfo.descriptorPool = descPool;
    allocInfo.descriptorSetCount = 1;
    allocInfo.pSetLayouts = &descSetLayout;
    vkAllocateDescriptorSets(device, &allocInfo, &descSet);

    // Params buffer
    VkBufferCreateInfo bufInfo = {VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO};
    bufInfo.size = sizeof(LumenPostProcessParams);
    bufInfo.usage = VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT;
    bufInfo.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
    vkCreateBuffer(device, &bufInfo, nullptr, &paramsBuffer);

    VkMemoryRequirements memReqs;
    vkGetBufferMemoryRequirements(device, paramsBuffer, &memReqs);

    VkMemoryAllocateInfo allocInfo2 = {VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO};
    allocInfo2.allocationSize = memReqs.size;
    allocInfo2.memoryTypeIndex = findMemoryTypePP(physDev, memReqs.memoryTypeBits,
        VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
    vkAllocateMemory(device, &allocInfo2, nullptr, &paramsMemory);
    vkBindBufferMemory(device, paramsBuffer, paramsMemory, 0);

    // TODO: write descriptor for input/output each frame since they change

    initialized = true;
    fprintf(stderr, "[Lumen PP] Post-process pipeline initialized\n");
    return true;
}

bool LumenPostProcess::dispatch(VkCommandBuffer cmdBuf, VkImageView inputView, VkImageView outputView,
                                 uint32_t width, uint32_t height, const LumenPostProcessParams& params) {
    if (!initialized) return false;

    // Write descriptors for current frame's images
    VkDescriptorImageInfo inputImgInfo = {};
    inputImgInfo.imageView = inputView;
    inputImgInfo.imageLayout = VK_IMAGE_LAYOUT_GENERAL;

    VkDescriptorImageInfo outputImgInfo = {};
    outputImgInfo.imageView = outputView;
    outputImgInfo.imageLayout = VK_IMAGE_LAYOUT_GENERAL;

    VkWriteDescriptorSet writes[2] = {};
    writes[0] = {VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET};
    writes[0].dstSet = descSet;
    writes[0].dstBinding = 0;
    writes[0].descriptorCount = 1;
    writes[0].descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_IMAGE;
    writes[0].pImageInfo = &inputImgInfo;

    writes[1] = {VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET};
    writes[1].dstSet = descSet;
    writes[1].dstBinding = 1;
    writes[1].descriptorCount = 1;
    writes[1].descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_IMAGE;
    writes[1].pImageInfo = &outputImgInfo;

    vkUpdateDescriptorSets(device, 2, writes, 0, nullptr);

    // Upload params via push constants
    vkCmdPushConstants(cmdBuf, pipelineLayout, VK_SHADER_STAGE_COMPUTE_BIT, 0,
                       sizeof(LumenPostProcessParams), &params);

    vkCmdBindPipeline(cmdBuf, VK_PIPELINE_BIND_POINT_COMPUTE, pipeline);
    vkCmdBindDescriptorSets(cmdBuf, VK_PIPELINE_BIND_POINT_COMPUTE, pipelineLayout, 0, 1, &descSet, 0, nullptr);

    uint32_t gx = (width + 7) / 8;
    uint32_t gy = (height + 7) / 8;
    vkCmdDispatch(cmdBuf, gx, gy, 1);

    return true;
}

void LumenPostProcess::destroy() {
    if (!device) return;
    if (pipeline) vkDestroyPipeline(device, pipeline, nullptr);
    if (pipelineLayout) vkDestroyPipelineLayout(device, pipelineLayout, nullptr);
    if (descSetLayout) vkDestroyDescriptorSetLayout(device, descSetLayout, nullptr);
    if (descPool) vkDestroyDescriptorPool(device, descPool, nullptr);
    if (paramsBuffer) vkDestroyBuffer(device, paramsBuffer, nullptr);
    if (paramsMemory) vkFreeMemory(device, paramsMemory, nullptr);
    initialized = false;
}
