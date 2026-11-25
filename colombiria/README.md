# COLOMBIRIA: Rutas Salvajes — Beta 1

Isolated Android prototype inside the existing repository. It does not replace the Minecraft acoustic project at the repository root.

## Beta 1 scope
- Android landscape app using Java and OpenGL ES 2.0.
- Procedural experimental terrain, mountain silhouettes, forest, river, road sections, bridge and tunnel portal.
- First low-poly AndesDrive ATX-01 placeholder and basic touch-driven prototype movement.
- Rendering is intentionally a first graphics laboratory pass, not final-quality assets or a complete driving simulator.

## Build
Open this directory as a Gradle project in Android Studio, or run:
`gradle --no-daemon :app:assembleDebug`

APK: `app/build/outputs/apk/debug/app-debug.apk`

## Planned next Beta 1 work
Improve actual 3D geometry and camera, vehicle physics, terrain variety, environmental audio, progressive weather fronts, day/night, LOD and memory/performance profiling on physical Android devices.
