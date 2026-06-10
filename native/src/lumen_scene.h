#pragma once
#include <cstdint>

// Cornell box geometry (right-handed, y-up)
// Walls: 5 quads (back, left, right, ceiling, floor) = 10 triangles
// Light on ceiling: 1 quad = 2 triangles
// 2 boxes inside (short box + tall box) = 24 triangles
// Total: 36 triangles

struct SceneVertex { float x, y, z; };
struct SceneTri   { uint32_t a, b, c; };
struct SceneMat   { float r, g, b; uint32_t flags; }; // flags: 1=emissive

struct SceneData {
    // 5 walls (20) + light (4) + short box (8) + tall box (8) = 40 vertices
    static constexpr uint32_t vertexCount = 40;
    // 6 walls/light (12 tris) + 2 boxes (24 tris) = 36 tris = 108 indices
    static constexpr uint32_t indexCount  = 108;
    static constexpr uint32_t triCount    = 36;

    static const SceneVertex vertices[vertexCount];
    static const SceneTri    indices[triCount];
    static const SceneMat    materials[triCount];

    static constexpr float lightIntensity = 30.0f;
};
