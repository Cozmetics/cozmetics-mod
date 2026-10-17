# Cozmetics

[![Build This Minecraft Mod](https://github.com/ItsARCn/cozmetics-mod/actions/workflows/build-mod.yml/badge.svg)](https://github.com/ItsARCn/cozmetics-mod/actions/workflows/build-mod.yml)
[![Minecraft Version](https://img.shields.io/badge/Minecraft-1.21.11-brightgreen.svg)](https://www.minecraft.net/)
[![Fabric Loader](https://img.shields.io/badge/Fabric-0.18.4-blue.svg)](https://fabricmc.net/)
[![GeckoLib](https://img.shields.io/badge/GeckoLib-5.4.5-orange.svg)](https://geckolib.com/)

**Cozmetics** is a high-performance cosmetic rendering mod for Minecraft Java Edition built on the Fabric toolchain. It brings animated 3D accessories, companions, and effects to player models using GeckoLib 5.

---

## Features

- **3D Animated Pets**: Shoulder-bound companions with continuous idle/movement animations, synchronized with the player model.
- **Client-Side Safe Rendering**: Zero server footprint. Cosmetic render logic and GeckoLib pipelines are strictly isolated to the client environment, preventing dedicated server crashes.
- **Z-Fighting & Culling Protection**: Optimized cutout render passes preventing coplanar depth-fighting artifacts on 2D planes and thin model geometries.
- **Extensible Architecture**: Modular foundation designed for future expansion across multiple cosmetic types:
  - Pets
  - Wings
  - Auras
  - Headwear
  - Fists
  - Boots
  - Back accessories
  - *(Capes reserved)*

---

## Tech Stack & Compatibility

| Component | Target Version |
| :--- | :--- |
| **Minecraft** | `1.21.11` |
| **Fabric Loader** | `0.18.4` |
| **Fabric API** | `0.141.3+1.21.11` |
| **GeckoLib** | `5.4.5` (Fabric 1.21.11) |
| **Java Toolchain** | `Java 21` (Compilation) / `Java 25` (Loom build runner) |
| **Mappings** | Yarn `1.21.11+build.6` |

---

## Building Locally

### Prerequisites
- JDK 25 installed (required by Fabric Loom 1.18 runtime).
- A local terminal with Git.

### Build the Mod JAR
Run Gradle wrapper from the project root:

```bash
./gradlew build
```

Compiled installable mod JARs will be generated in:
```text
build/libs/cozmetics-<version>.jar
```

### Launch Minecraft Client
To test in a local development client environment:

```bash
./gradlew runClient
```

---

## GitHub Actions Automated Builds

A portable GitHub Actions build workflow is available under [`.github/workflows/build-mod.yml`](.github/workflows/build-mod.yml):

1. Navigate to the **Actions** tab in the repository.
2. Select **Build This Minecraft Mod**.
3. Click **Run workflow** (default is set to Java 25 runner with Java 21 compilation toolchain).
4. Download the compiled release JAR directly from the generated release or workflow artifacts.

---

## License

This project is licensed under the [CC0 1.0 Universal License](LICENSE).
