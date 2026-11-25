# COLOMBIRIA: Rutas Salvajes — Vulkan Graphics Engine / Phase 1

This Android project lives under `colombiria/` on the isolated branch `feature/colombiria-open-world`. It does not replace the Minecraft project at the repository root or modify the default branch.

## Phase 1 objective

Build a production-oriented Android 3D renderer with Vulkan as the primary graphics API. Phase 1 is not complete merely because the app builds: it must produce a convincing visual test scene and pass real-device performance and stability tests.

## Current implementation milestone: Vulkan Alpha 1

- Android Java activity hosts a native Android Surface.
- C++17 renderer uses the Android NDK and Vulkan 1.1 API.
- Native Vulkan instance, Android surface, physical-device selection, graphics/present queue, logical device and swapchain.
- Render pass, framebuffers, command buffers, synchronization primitives and graphics pipeline.
- GLSL 450 vertex/fragment shaders compiled to SPIR-V with `glslc` during CMake build.
- Initial GPU draw call renders a shader-driven triangle, proving the native Vulkan pipeline path.
- Vulkan 1.1-capable GPU selection is checked at runtime.
- CI builds an arm64 Android debug APK with JDK 17, Android NDK, CMake and shader compiler.

## What is not finished yet

This alpha is an API and rendering-pipeline foundation, not the promised realistic graphics engine. It does not yet include a 3D landscape, PBR material system, texture streaming, shadow maps, physically based sky, volumetric clouds, water reflections, vegetation assets, post-processing, or production asset pipeline. The previous OpenGL ES renderer is no longer the active rendering path.

## Phase 1 completion gates

1. Vulkan device/extension/format capability reporting and robust swapchain recreation.
2. GPU resource and memory management, descriptor/pipeline cache and upload staging.
3. Camera, transforms, mesh buffers, depth buffer and indexed geometry.
4. Terrain mesh generation, normal/tangent generation and multi-resolution LOD.
5. PBR materials with albedo, normal, roughness/metallic and ambient occlusion textures.
6. Directional lighting, cascaded shadow maps, image-based/ambient lighting and tone mapping.
7. Real water material, animated waves, reflections/refraction approximation and shoreline blending.
8. Detailed vegetation and terrain/rock assets with wind animation and distance culling.
9. Sky, clouds, atmospheric perspective, fog and exposure adaptation.
10. GPU timing, dynamic resolution, memory budgets, thermal-aware quality tiers and device profiling.
11. Visual acceptance scene and sustained real-device test before Phase 1 sign-off.

## Build

GitHub Actions installs Android NDK 27.2.12479018, CMake 3.22.1 and `glslc`, then runs:

```sh
gradle --no-daemon :app:assembleDebug
```

Target ABI for this Vulkan alpha: `arm64-v8a`.
