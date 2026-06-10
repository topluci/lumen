#include "lumen_scene.h"

// Cornell box dimensions: 4x4x4 centered at origin
// Walls: back (z=-2), left (x=-2), right (x=2), ceiling (y=2), floor (y=-2)
// Light on ceiling: small centered square

#define V(x,y,z) {x, y, z}
#define T(a,b,c) {a, b, c}

const SceneVertex SceneData::vertices[] = {
    // Back wall (z=-2)
    V(-2, -2, -2), V( 2, -2, -2), V( 2,  2, -2), V(-2,  2, -2),
    // Left wall (x=-2), red
    V(-2, -2, -2), V(-2, -2,  2), V(-2,  2,  2), V(-2,  2, -2),
    // Right wall (x=2), green
    V( 2, -2,  2), V( 2, -2, -2), V( 2,  2, -2), V( 2,  2,  2),
    // Ceiling (y=2)
    V(-2,  2, -2), V( 2,  2, -2), V( 2,  2,  2), V(-2,  2,  2),
    // Floor (y=-2)
    V(-2, -2,  2), V( 2, -2,  2), V( 2, -2, -2), V(-2, -2, -2),
    // Light (on ceiling, centered 1x1)
    V(-0.5f, 1.99f, -0.5f), V( 0.5f, 1.99f, -0.5f), V( 0.5f, 1.99f,  0.5f), V(-0.5f, 1.99f,  0.5f),
    // Short box (1.5x1x1.5 on floor, left side)
    // Front face
    V(-1.5f, -2,  1), V(-0.5f, -2,  1), V(-0.5f, -1,  1), V(-1.5f, -1,  1),
    // Back face
    V(-1.5f, -2, 0.5f), V(-0.5f, -2, 0.5f), V(-0.5f, -1, 0.5f), V(-1.5f, -1, 0.5f),
    // Tall box (1.5x2x1.5 on floor, right side)
    V( 0.5f, -2,  1), V( 1.5f, -2,  1), V( 1.5f,  0,  1), V( 0.5f,  0,  1),
    // Back face
    V( 0.5f, -2, 0.5f), V( 1.5f, -2, 0.5f), V( 1.5f,  0, 0.5f), V( 0.5f,  0, 0.5f),
};

const SceneTri SceneData::indices[] = {
    // Back wall (2 tris)
    T(0,1,2), T(0,2,3),
    // Left wall
    T(4,5,6), T(4,6,7),
    // Right wall
    T(8,9,10), T(8,10,11),
    // Ceiling
    T(12,13,14), T(12,14,15),
    // Floor
    T(16,17,18), T(16,18,19),
    // Light
    T(20,21,22), T(20,22,23),
    // Short box front
    T(24,25,26), T(24,26,27),
    // Short box back
    T(28,29,30), T(28,30,31),
    // Short box left
    T(24,28,31), T(24,31,27),
    // Short box right
    T(25,29,30), T(25,30,26),
    // Short box top
    T(27,31,30), T(27,30,26),
    // Short box bottom
    T(24,28,29), T(24,29,25),
    // Tall box front
    T(32,33,34), T(32,34,35),
    // Tall box back
    T(36,37,38), T(36,38,39),
    // Tall box left
    T(32,36,39), T(32,39,35),
    // Tall box right
    T(33,37,38), T(33,38,34),
    // Tall box top
    T(35,39,38), T(35,38,34),
    // Tall box bottom
    T(32,36,37), T(32,37,33),
};

const SceneMat SceneData::materials[] = {
    {1,1,1,0}, {1,1,1,0}, // back wall white
    {1,0,0,0}, {1,0,0,0}, // left wall red
    {0,1,0,0}, {0,1,0,0}, // right wall green
    {1,1,1,0}, {1,1,1,0}, // ceiling white
    {1,1,1,0}, {1,1,1,0}, // floor white
    {1,1,1,1}, {1,1,1,1}, // light emissive
    {1,1,1,0}, {1,1,1,0}, // short box front
    {1,1,1,0}, {1,1,1,0}, // short box back
    {1,1,1,0}, {1,1,1,0}, // short box left
    {1,1,1,0}, {1,1,1,0}, // short box right
    {1,1,1,0}, {1,1,1,0}, // short box top
    {1,1,1,0}, {1,1,1,0}, // short box bottom
    {1,1,1,0}, {1,1,1,0}, // tall box front
    {1,1,1,0}, {1,1,1,0}, // tall box back
    {1,1,1,0}, {1,1,1,0}, // tall box left
    {1,1,1,0}, {1,1,1,0}, // tall box right
    {1,1,1,0}, {1,1,1,0}, // tall box top
    {1,1,1,0}, {1,1,1,0}, // tall box bottom
};

#undef V
#undef T
