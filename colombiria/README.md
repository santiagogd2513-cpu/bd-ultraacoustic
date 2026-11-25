# COLOMBIRIA: Rutas Salvajes — Godot 4 / Phase 1

The isolated project lives under colombiria/ on feature/colombiria-open-world. The repository root and default branch remain untouched.

## Engine migration

The hand-written Vulkan/JNI prototype is retired as the active runtime. Godot 4.4.1 is now the game engine. The Android export uses Godot's Mobile renderer (Vulkan on supported Android devices), arm64-v8a, landscape orientation and the immersive fullscreen export option.

## Current Phase 1 foundation

- Godot 4 project, main scene and procedural visual acceptance scene.
- Procedural Andean foothills with generated mesh and normals.
- Filmic tonemapping, procedural sky, ambient sky lighting and atmospheric fog.
- Directional sun with four-split directional shadows.
- Animated shader-based river surface.
- Forest prototype rendered with MultiMesh instancing.
- Fictional AndesDrive-inspired vehicle blockout for scale and material checks.
- Android export preset with immersive mode requested.
- GitHub Actions installs Godot 4.4.1, Android SDK and export templates, validates the project headlessly, and exports an arm64 APK.

## Limitations

This is the first Godot migration milestone, not a finished high-end graphics engine. The scene uses procedural placeholder geometry/materials; production assets, texture sets, a complete PBR asset pipeline, robust world streaming, advanced water, foliage wind animation, adaptive quality tiers and real-device profiling remain unfinished. The old Vulkan triangle app is not the active game.

## Phase 1 acceptance gates

1. Confirm Android export and immersive fullscreen on real devices.
2. Verify actual Vulkan renderer/device capability and fallback behavior.
3. Replace placeholder terrain with multi-resolution terrain chunks and LOD.
4. Establish PBR texture/material pipeline and asset import settings.
5. Improve sky, atmospheric perspective, clouds and gradual exposure adaptation.
6. Implement production vegetation, wind animation, culling and protected colliders.
7. Improve water shading, waves, transparency/reflection approximation and shoreline.
8. Add mobile post-processing and lighting quality tiers.
9. Add camera rigs, GPU/memory profiling, dynamic resolution and thermal-aware scaling.
10. Produce a representative visual benchmark scene and sustained device tests before Phase 1 sign-off.
