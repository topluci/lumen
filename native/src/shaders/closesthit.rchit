#version 460
#extension GL_EXT_ray_tracing : require

layout(location = 0) rayPayloadInEXT vec4 payload;

// Per-triangle material data via storage buffer
struct Material {
    vec3 color;
    uint flags; // bit 0: emissive
};
layout(set = 0, binding = 3, std430) readonly buffer Materials {
    Material mats[];
} matBuffer;

void main() {
    uint triIdx = gl_PrimitiveID;
    Material m = matBuffer.mats[triIdx];

    vec3 hitPos = gl_WorldRayOriginEXT + gl_HitTEXT * gl_WorldRayDirectionEXT;
    payload = vec4(m.color, (m.flags & 1u) != 0u ? 1.0 : 0.0);
}
