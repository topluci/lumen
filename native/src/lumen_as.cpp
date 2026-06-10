#include "lumen_as.h"
#include <cstdlib>
#include <cstring>
#include <cstdio>

static uint32_t findMemoryType(VkPhysicalDevice physDev, uint32_t typeFilter,
                                VkMemoryPropertyFlags props) {
    VkPhysicalDeviceMemoryProperties memProps;
    vkGetPhysicalDeviceMemoryProperties(physDev, &memProps);
    for (uint32_t i = 0; i < memProps.memoryTypeCount; ++i) {
        if ((typeFilter & (1u << i)) &&
            (memProps.memoryTypes[i].propertyFlags & props) == props)
            return i;
    }
    fprintf(stderr, "[Lumen AS] Failed to find suitable memory type\n");
    return 0;
}

static VkBuffer createBuffer(VkDevice device, VkDeviceSize size,
                              VkBufferUsageFlags usage, VkMemoryPropertyFlags props,
                              VkDeviceMemory* memory, VkPhysicalDevice physDev) {
    VkBufferCreateInfo bufInfo = {VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO};
    bufInfo.size = size;
    bufInfo.usage = usage;
    bufInfo.sharingMode = VK_SHARING_MODE_EXCLUSIVE;

    VkBuffer buffer = VK_NULL_HANDLE;
    vkCreateBuffer(device, &bufInfo, nullptr, &buffer);

    VkMemoryRequirements memReqs;
    vkGetBufferMemoryRequirements(device, buffer, &memReqs);

    VkMemoryAllocateInfo allocInfo = {VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO};
    allocInfo.allocationSize = memReqs.size;
    allocInfo.memoryTypeIndex = findMemoryType(physDev, memReqs.memoryTypeBits, props);

    vkAllocateMemory(device, &allocInfo, nullptr, memory);
    vkBindBufferMemory(device, buffer, *memory, 0);
    return buffer;
}

static void uploadBuffer(VkDevice device, VkDeviceMemory memory, VkDeviceSize size,
                          const void* data) {
    void* mapped;
    vkMapMemory(device, memory, 0, size, 0, &mapped);
    memcpy(mapped, data, (size_t)size);
    vkUnmapMemory(device, memory);
}

static VkCommandBuffer beginOneShotCmd(VkDevice device, VkCommandPool cmdPool) {
    VkCommandBufferAllocateInfo allocInfo = {VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO};
    allocInfo.commandPool = cmdPool;
    allocInfo.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
    allocInfo.commandBufferCount = 1;

    VkCommandBuffer cmdBuf;
    vkAllocateCommandBuffers(device, &allocInfo, &cmdBuf);

    VkCommandBufferBeginInfo beginInfo = {VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO};
    beginInfo.flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
    vkBeginCommandBuffer(cmdBuf, &beginInfo);
    return cmdBuf;
}

static void cmdBufferSubmit(VkDevice device, VkCommandPool cmdPool, VkQueue queue,
                             VkCommandBuffer cmdBuf) {
    vkEndCommandBuffer(cmdBuf);

    VkSubmitInfo submitInfo = {VK_STRUCTURE_TYPE_SUBMIT_INFO};
    submitInfo.commandBufferCount = 1;
    submitInfo.pCommandBuffers = &cmdBuf;

    VkFenceCreateInfo fenceInfo = {VK_STRUCTURE_TYPE_FENCE_CREATE_INFO};
    VkFence fence;
    vkCreateFence(device, &fenceInfo, nullptr, &fence);

    vkQueueSubmit(queue, 1, &submitInfo, fence);
    vkWaitForFences(device, 1, &fence, VK_TRUE, UINT64_MAX);
    vkDestroyFence(device, fence, nullptr);

    vkFreeCommandBuffers(device, cmdPool, 1, &cmdBuf);
}

void LumenAccelStruct::destroy(VkDevice dev) {
    if (handle) {
        auto vkDestroyAS = (PFN_vkDestroyAccelerationStructureKHR)
            vkGetDeviceProcAddr(dev, "vkDestroyAccelerationStructureKHR");
        if (vkDestroyAS) vkDestroyAS(dev, handle, nullptr);
    }
    if (buffer) vkDestroyBuffer(dev, buffer, nullptr);
    if (memory) vkFreeMemory(dev, memory, nullptr);
}

void LumenASBuilder::init(VkDevice d, VkPhysicalDevice p, uint32_t, VkQueue q) {
    device = d;
    physDev = p;
    queue = q;

    VkCommandPoolCreateInfo poolInfo = {VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO};
    poolInfo.flags = VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT;
    poolInfo.queueFamilyIndex = 0;
    vkCreateCommandPool(device, &poolInfo, nullptr, &cmdPool);
}

void LumenASBuilder::destroy() {
    if (cmdPool) vkDestroyCommandPool(device, cmdPool, nullptr);
}

LumenAccelStruct* LumenASBuilder::buildBottomLevel(
    const float* vertices, uint32_t vertexCount,
    const uint32_t* indices, uint32_t indexCount)
{
    VkPhysicalDevice physDev = this->physDev;

    VkDeviceSize vertexBufferSize = vertexCount * 3 * sizeof(float);
    VkDeviceSize indexBufferSize = indexCount * sizeof(uint32_t);

    VkBuffer vertexBuf;
    VkDeviceMemory vertexMem;
    vertexBuf = createBuffer(device, vertexBufferSize,
        VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_BUILD_INPUT_READ_ONLY_BIT_KHR |
        VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT,
        VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
        &vertexMem, physDev);
    uploadBuffer(device, vertexMem, vertexBufferSize, vertices);

    VkBuffer indexBuf;
    VkDeviceMemory indexMem;
    indexBuf = createBuffer(device, indexBufferSize,
        VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_BUILD_INPUT_READ_ONLY_BIT_KHR |
        VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT,
        VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
        &indexMem, physDev);
    uploadBuffer(device, indexMem, indexBufferSize, indices);

    auto vkGetBufAddr = (PFN_vkGetBufferDeviceAddressKHR)
        vkGetDeviceProcAddr(device, "vkGetBufferDeviceAddressKHR");
    if (!vkGetBufAddr) {
        fprintf(stderr, "[Lumen AS] vkGetBufferDeviceAddress not available\n");
        return nullptr;
    }

    VkBufferDeviceAddressInfo addrInfo = {VK_STRUCTURE_TYPE_BUFFER_DEVICE_ADDRESS_INFO};
    addrInfo.buffer = vertexBuf;
    VkDeviceAddress vertexAddress = vkGetBufAddr(device, &addrInfo);
    addrInfo.buffer = indexBuf;
    VkDeviceAddress indexAddress = vkGetBufAddr(device, &addrInfo);

    VkAccelerationStructureGeometryKHR asGeom = {VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_GEOMETRY_KHR};
    asGeom.geometryType = VK_GEOMETRY_TYPE_TRIANGLES_KHR;
    asGeom.geometry.triangles = {VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_GEOMETRY_TRIANGLES_DATA_KHR};
    asGeom.geometry.triangles.vertexFormat = VK_FORMAT_R32G32B32_SFLOAT;
    asGeom.geometry.triangles.vertexData.deviceAddress = vertexAddress;
    asGeom.geometry.triangles.vertexStride = sizeof(float) * 3;
    asGeom.geometry.triangles.maxVertex = vertexCount - 1;
    asGeom.geometry.triangles.indexType = VK_INDEX_TYPE_UINT32;
    asGeom.geometry.triangles.indexData.deviceAddress = indexAddress;
    asGeom.flags = VK_GEOMETRY_OPAQUE_BIT_KHR;

    VkAccelerationStructureBuildGeometryInfoKHR buildInfo = {
        VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_BUILD_GEOMETRY_INFO_KHR};
    buildInfo.type = VK_ACCELERATION_STRUCTURE_TYPE_BOTTOM_LEVEL_KHR;
    buildInfo.flags = VK_BUILD_ACCELERATION_STRUCTURE_PREFER_FAST_TRACE_BIT_KHR;
    buildInfo.geometryCount = 1;
    buildInfo.pGeometries = &asGeom;

    uint32_t primCount = indexCount / 3;
    VkAccelerationStructureBuildSizesInfoKHR sizeInfo = {
        VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_BUILD_SIZES_INFO_KHR};
    PFN_vkGetAccelerationStructureBuildSizesKHR vkGetASBuildSizes =
        (PFN_vkGetAccelerationStructureBuildSizesKHR)
        vkGetDeviceProcAddr(device, "vkGetAccelerationStructureBuildSizesKHR");
    if (!vkGetASBuildSizes) {
        fprintf(stderr, "[Lumen AS] vkGetAccelerationStructureBuildSizes not available\n");
        return nullptr;
    }
    vkGetASBuildSizes(device, VK_ACCELERATION_STRUCTURE_BUILD_TYPE_HOST_KHR,
                       &buildInfo, &primCount, &sizeInfo);

    LumenAccelStruct* as = new LumenAccelStruct();

    VkBufferUsageFlags asBufferUsage =
        VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_STORAGE_BIT_KHR |
        VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT;
    as->buffer = createBuffer(device, sizeInfo.accelerationStructureSize,
                               asBufferUsage,
                               VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,
                               &as->memory, physDev);

    VkAccelerationStructureCreateInfoKHR asCreateInfo = {
        VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_CREATE_INFO_KHR};
    asCreateInfo.buffer = as->buffer;
    asCreateInfo.size = sizeInfo.accelerationStructureSize;
    asCreateInfo.type = VK_ACCELERATION_STRUCTURE_TYPE_BOTTOM_LEVEL_KHR;

    PFN_vkCreateAccelerationStructureKHR vkCreateAS =
        (PFN_vkCreateAccelerationStructureKHR)
        vkGetDeviceProcAddr(device, "vkCreateAccelerationStructureKHR");
    if (!vkCreateAS) {
        fprintf(stderr, "[Lumen AS] vkCreateAccelerationStructure not available\n");
        delete as;
        return nullptr;
    }
    vkCreateAS(device, &asCreateInfo, nullptr, &as->handle);

    VkBuffer scratchBuffer;
    VkDeviceMemory scratchMemory;
    scratchBuffer = createBuffer(device, sizeInfo.buildScratchSize,
        VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT |
        VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_BUILD_INPUT_READ_ONLY_BIT_KHR,
        VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, &scratchMemory, physDev);

    addrInfo.buffer = scratchBuffer;
    VkDeviceAddress scratchAddress = vkGetBufAddr(device, &addrInfo);
    buildInfo.scratchData.deviceAddress = scratchAddress;
    buildInfo.dstAccelerationStructure = as->handle;

    VkAccelerationStructureBuildRangeInfoKHR rangeInfo = {};
    rangeInfo.primitiveCount = primCount;
    rangeInfo.primitiveOffset = 0;
    rangeInfo.firstVertex = 0;
    rangeInfo.transformOffset = 0;
    const VkAccelerationStructureBuildRangeInfoKHR* pRangeInfo = &rangeInfo;

    PFN_vkCmdBuildAccelerationStructuresKHR vkCmdBuildAS =
        (PFN_vkCmdBuildAccelerationStructuresKHR)
        vkGetDeviceProcAddr(device, "vkCmdBuildAccelerationStructuresKHR");

    if (!vkCmdBuildAS) {
        fprintf(stderr, "[Lumen AS] vkCmdBuildAccelerationStructures not available\n");
        delete as;
        return nullptr;
    }

    VkCommandBuffer cmdBuf = beginOneShotCmd(device, cmdPool);
    vkCmdBuildAS(cmdBuf, 1, &buildInfo, &pRangeInfo);
    cmdBufferSubmit(device, cmdPool, queue, cmdBuf);

    vkDestroyBuffer(device, scratchBuffer, nullptr);
    vkFreeMemory(device, scratchMemory, nullptr);

    VkAccelerationStructureDeviceAddressInfoKHR asAddrInfo = {
        VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_DEVICE_ADDRESS_INFO_KHR};
    asAddrInfo.accelerationStructure = as->handle;
    PFN_vkGetAccelerationStructureDeviceAddressKHR vkGetASDeviceAddress =
        (PFN_vkGetAccelerationStructureDeviceAddressKHR)
        vkGetDeviceProcAddr(device, "vkGetAccelerationStructureDeviceAddressKHR");
    if (!vkGetASDeviceAddress) {
        fprintf(stderr, "[Lumen AS] vkGetAccelerationStructureDeviceAddress not available\n");
        as->deviceAddress = 0;
    } else {
        as->deviceAddress = vkGetASDeviceAddress(device, &asAddrInfo);
    }
    as->isTopLevel = false;

    fprintf(stderr, "[Lumen AS] BLAS built, deviceAddress=0x%llx\n",
            (unsigned long long)as->deviceAddress);
    return as;
}

LumenAccelStruct* LumenASBuilder::buildTopLevel(LumenAccelStruct* blas, uint32_t instanceCount) {
    VkPhysicalDevice physDev = this->physDev;

    VkAccelerationStructureInstanceKHR instance = {};
    memset(&instance, 0, sizeof(instance));
    instance.transform.matrix[0][0] = 1.0f;
    instance.transform.matrix[1][1] = 1.0f;
    instance.transform.matrix[2][2] = 1.0f;
    instance.instanceCustomIndex = 0;
    instance.mask = 0xFF;
    instance.instanceShaderBindingTableRecordOffset = 0;
    instance.flags = VK_GEOMETRY_INSTANCE_TRIANGLE_FACING_CULL_DISABLE_BIT_KHR;
    instance.accelerationStructureReference = blas->deviceAddress;

    VkDeviceSize instanceBufferSize = sizeof(VkAccelerationStructureInstanceKHR) * instanceCount;
    VkBuffer instanceBuf;
    VkDeviceMemory instanceMem;
    instanceBuf = createBuffer(device, instanceBufferSize,
        VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_BUILD_INPUT_READ_ONLY_BIT_KHR |
        VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT,
        VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
        &instanceMem, physDev);
    uploadBuffer(device, instanceMem, instanceBufferSize, &instance);

    auto vkGetBufAddr = (PFN_vkGetBufferDeviceAddressKHR)
        vkGetDeviceProcAddr(device, "vkGetBufferDeviceAddressKHR");
    if (!vkGetBufAddr) {
        fprintf(stderr, "[Lumen AS] vkGetBufferDeviceAddress not available\n");
        return nullptr;
    }

    VkBufferDeviceAddressInfo addrInfo = {VK_STRUCTURE_TYPE_BUFFER_DEVICE_ADDRESS_INFO};
    addrInfo.buffer = instanceBuf;
    VkDeviceAddress instancesAddress = vkGetBufAddr(device, &addrInfo);

    VkAccelerationStructureGeometryKHR asGeom = {
        VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_GEOMETRY_KHR};
    asGeom.geometryType = VK_GEOMETRY_TYPE_INSTANCES_KHR;
    asGeom.geometry.instances = {
        VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_GEOMETRY_INSTANCES_DATA_KHR};
    asGeom.geometry.instances.arrayOfPointers = VK_FALSE;
    asGeom.geometry.instances.data.deviceAddress = instancesAddress;
    asGeom.flags = VK_GEOMETRY_OPAQUE_BIT_KHR;

    VkAccelerationStructureBuildGeometryInfoKHR buildInfo = {
        VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_BUILD_GEOMETRY_INFO_KHR};
    buildInfo.type = VK_ACCELERATION_STRUCTURE_TYPE_TOP_LEVEL_KHR;
    buildInfo.flags = VK_BUILD_ACCELERATION_STRUCTURE_PREFER_FAST_TRACE_BIT_KHR;
    buildInfo.geometryCount = 1;
    buildInfo.pGeometries = &asGeom;

    VkAccelerationStructureBuildSizesInfoKHR sizeInfo = {
        VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_BUILD_SIZES_INFO_KHR};
    PFN_vkGetAccelerationStructureBuildSizesKHR vkGetASBuildSizes =
        (PFN_vkGetAccelerationStructureBuildSizesKHR)
        vkGetDeviceProcAddr(device, "vkGetAccelerationStructureBuildSizesKHR");
    if (!vkGetASBuildSizes) {
        fprintf(stderr, "[Lumen AS] vkGetAccelerationStructureBuildSizes not available\n");
        return nullptr;
    }
    vkGetASBuildSizes(device, VK_ACCELERATION_STRUCTURE_BUILD_TYPE_HOST_KHR,
                       &buildInfo, &instanceCount, &sizeInfo);

    LumenAccelStruct* as = new LumenAccelStruct();

    VkBufferUsageFlags asBufferUsage =
        VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_STORAGE_BIT_KHR |
        VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT;
    as->buffer = createBuffer(device, sizeInfo.accelerationStructureSize,
                               asBufferUsage,
                               VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,
                               &as->memory, physDev);

    VkAccelerationStructureCreateInfoKHR asCreateInfo = {
        VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_CREATE_INFO_KHR};
    asCreateInfo.buffer = as->buffer;
    asCreateInfo.size = sizeInfo.accelerationStructureSize;
    asCreateInfo.type = VK_ACCELERATION_STRUCTURE_TYPE_TOP_LEVEL_KHR;

    PFN_vkCreateAccelerationStructureKHR vkCreateAS =
        (PFN_vkCreateAccelerationStructureKHR)
        vkGetDeviceProcAddr(device, "vkCreateAccelerationStructureKHR");
    if (!vkCreateAS) {
        fprintf(stderr, "[Lumen AS] vkCreateAccelerationStructure not available\n");
        delete as;
        return nullptr;
    }
    vkCreateAS(device, &asCreateInfo, nullptr, &as->handle);

    VkBuffer scratchBuffer;
    VkDeviceMemory scratchMemory;
    scratchBuffer = createBuffer(device, sizeInfo.buildScratchSize,
        VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT |
        VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_BUILD_INPUT_READ_ONLY_BIT_KHR,
        VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, &scratchMemory, physDev);

    addrInfo.buffer = scratchBuffer;
    VkDeviceAddress scratchAddress = vkGetBufAddr(device, &addrInfo);
    buildInfo.scratchData.deviceAddress = scratchAddress;
    buildInfo.dstAccelerationStructure = as->handle;

    VkAccelerationStructureBuildRangeInfoKHR rangeInfo = {};
    rangeInfo.primitiveCount = instanceCount;
    rangeInfo.primitiveOffset = 0;
    rangeInfo.firstVertex = 0;
    rangeInfo.transformOffset = 0;
    const VkAccelerationStructureBuildRangeInfoKHR* pRangeInfo = &rangeInfo;

    PFN_vkCmdBuildAccelerationStructuresKHR vkCmdBuildAS =
        (PFN_vkCmdBuildAccelerationStructuresKHR)
        vkGetDeviceProcAddr(device, "vkCmdBuildAccelerationStructuresKHR");

    if (!vkCmdBuildAS) {
        fprintf(stderr, "[Lumen AS] vkCmdBuildAccelerationStructures not available\n");
        delete as;
        return nullptr;
    }

    VkCommandBuffer cmdBuf = beginOneShotCmd(device, cmdPool);
    vkCmdBuildAS(cmdBuf, 1, &buildInfo, &pRangeInfo);
    cmdBufferSubmit(device, cmdPool, queue, cmdBuf);

    vkDestroyBuffer(device, scratchBuffer, nullptr);
    vkFreeMemory(device, scratchMemory, nullptr);

    VkAccelerationStructureDeviceAddressInfoKHR asAddrInfo = {
        VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_DEVICE_ADDRESS_INFO_KHR};
    asAddrInfo.accelerationStructure = as->handle;
    PFN_vkGetAccelerationStructureDeviceAddressKHR vkGetASDeviceAddress =
        (PFN_vkGetAccelerationStructureDeviceAddressKHR)
        vkGetDeviceProcAddr(device, "vkGetAccelerationStructureDeviceAddressKHR");
    if (!vkGetASDeviceAddress) {
        fprintf(stderr, "[Lumen AS] vkGetAccelerationStructureDeviceAddress not available\n");
        as->deviceAddress = 0;
    } else {
        as->deviceAddress = vkGetASDeviceAddress(device, &asAddrInfo);
    }
    as->isTopLevel = true;

    fprintf(stderr, "[Lumen AS] TLAS built, deviceAddress=0x%llx\n",
            (unsigned long long)as->deviceAddress);
    return as;
}
