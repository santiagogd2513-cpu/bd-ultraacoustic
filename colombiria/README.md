# COLOMBIRIA: Rutas Salvajes — Beta 2

Android graphics-engine laboratory, isolated under `colombiria/` inside the existing repository. The Minecraft acoustic project at the repository root and the default branch are not modified.

## Beta 2 graphics target

Beta 2 replaces the Beta 1 flat low-poly scene with a substantially expanded procedural OpenGL ES 2.0 renderer:

- Tessellated rolling terrain with generated height variation and vertex shading.
- Layered mountain valley silhouettes and distance fog.
- Curving road ribbon with shoulders and center markings.
- Procedural river surface with animated wave and sparkle modulation in the material shader.
- Bridge deck, tunnel portal, fixed conifer meshes and faceted rock formations.
- Directional diffuse lighting, hemisphere fill, material-aware water shading and atmospheric distance blending.
- More efficient primitive-array mesh construction to reduce Java allocation overhead.
- Shader compile/link checks so graphics-program errors fail visibly instead of silently rendering a blank frame.
- Android app version 0.2.0-beta2 and automatic GitHub Actions debug-APK build.

## Important scope and limitations

This is an ambitious graphics prototype, not yet the final production renderer. It still uses a single bounded procedural scene and one interleaved mesh. It does not yet contain true sector streaming, physically based materials, texture maps, shadow maps, reflection probes, volumetric clouds, full weather simulation, production vehicle assets, full vehicle physics, or a completed sound system. Those require further implementation and device profiling.

The scene uses fixed world props; trees are not destructible. The renderer is designed for GLES 2.0 compatibility and must be tested on real Android GPUs. Visual quality and frame rate will depend on device capabilities.

## Build

GitHub Actions builds with JDK 17 and Gradle 8.7:
`gradle --no-daemon :app:assembleDebug`

Output: `app/build/outputs/apk/debug/app-debug.apk`

Workflow artifact: `Colombiria-Beta2-Android`.
