# Cozmetics

[![Build This Minecraft Mod](https://github.com/Cozmetics/cozmetics-mod/actions/workflows/build-mod.yml/badge.svg)](https://github.com/Cozmetics/cozmetics-mod/actions/workflows/build-mod.yml)
[![Minecraft Version](https://img.shields.io/badge/Minecraft-1.21.11-brightgreen.svg)](https://www.minecraft.net/)
[![Fabric Loader](https://img.shields.io/badge/Fabric-0.19.5+-blue.svg)](https://fabricmc.net/)
[![GeckoLib](https://img.shields.io/badge/GeckoLib-5.4.5-orange.svg)](https://geckolib.com/)

**Cozmetics** is a high-performance, client-side cosmetic rendering mod for Minecraft Java Edition built on Fabric and GeckoLib 5. It provides a modular, data-driven engine capable of loading animated 3D models, direct Blockbench (`.bbmodel`) project files, and advanced texture formats (static/animated WebP, APNG, PNG) with full player bone synchronization.

---

## Key Features

### 1. Reusable 3D Cosmetic Engine
- **Data-Driven Architecture**: Clean, extensible cosmetic registry with zero hardcoded demo clutter. Ready for dynamic catalogue synchronization from the future Cozmetics website/backend.
- **8 Cosmetic Categories Supported**:
  - **Pets**: Animated companions hovering alongside the player (`AttachmentSlot.PET`).
  - **Wings**: Player-attached back wings moving with flight and sneaking.
  - **Auras**: Rotating, pulsing atmospheric particle rings.
  - **Back Accessories**: Scythes, quivers, backpacks, and back-mounted gear.
  - **Boots**: Animated & static footwear synchronized with leg walking swings.
  - **Face Masks**: Synchronized headwear matching player pitch, yaw, and head tilt.
  - **Fists**: Arm-bound gauntlets and weapons moving with player arm swings.
  - **Headwear**: Skull-fitted crowns, halos, and hats with configurable offsets and static full-bright lighting.
  - *(Capes reserved)*: Strictly disabled (`CAPE.isEnabled() == false`) for now.
- **Z-Fighting & Culling Protection**: Optimized cutout render passes preventing coplanar depth-fighting artifacts on 2D planes and thin model geometries.

### 2. Direct Blockbench (`.bbmodel`) Support
- **Native In-Memory Loading**: Directly load original `.bbmodel` project files without manually exporting `.geo.json` or `.animation.json` beforehand:
  - **Geometry**: Automatically converts cuboids (`from`, `to`, `origin`, `rotation`, `inflate`) and hierarchies into Bedrock 1.12.0 format.
  - **UV Mapping**: Supports both standard Box UV (`uv_offset`) and explicit per-face UV coordinates.
  - **Hierarchy & Bones**: Automatically builds parent-child bone trees and preserves anchor bones (`anchor_head`, `anchor_body`, `anchor_leftarm`, `anchor_rightarm`, `anchor_leftleg`, `anchor_rightleg`).
  - **Embedded Textures**: Automatically decodes embedded `data:image/png;base64` textures directly from `.bbmodel` files into GPU textures.
  - **Animations**: Translates timeline channels (`rotation`, `position`, `scale`) and keyframe interpolation (`linear`, `catmullrom`, `step`, `bezier`) into Bedrock 1.8.0 animation files.
- **Standalone Converter Utility**: Includes `BbModelConverter` for build-time or programmatic export of `.bbmodel` files into standalone `.geo.json`, `.animation.json`, and `.png` assets.

### 3. Universal Image & Texture Pipeline
- **Multi-Format Image Support**: Bundles TwelveMonkeys ImageIO plugins (jar-in-jar with zero native binary dependencies).
- **Supported Formats**:
  - **WebP & Animated WebP**: Multi-frame ANMF alpha blending (`AlphaComposite.Src` vs `SrcOver`), frame disposal flags, and variable frame durations.
  - **APNG & PNG**: Static and animated PNG frame sequences.
  - **GIF, JPEG, BMP, TIFF**: Fallback and legacy image decoding.
- **Zero-Allocation Time Sampling**: Time-based frame lookups driven by continuous real-time to avoid animation hitches when paused or inside menus.

### 4. Interactive 360° Wardrobe Screen
- **Full 360° Orbital Preview**: Interactive 3D character viewport with mouse drag orbital rotation (pitch + yaw) and mouse wheel zoom.
- **Visibility Control**: Dedicated wardrobe controls (Show, Hide, Solo) that affect client-side rendering visibility without altering equipped state.
- **Clean Default Empty State**: When launched without account/backend data, the mod starts with an empty catalogue and gracefully renders an informative status message.
- **Entrypoints**: Accessible from:
  1. Minecraft Pause Menu (`✦ Cozmetics` button).
  2. Minecraft Title Screen (before joining a world).
  3. Fabric Mod Menu (optional compatibility integration).

### 5. Backend & Offline Architecture
- **Clean Service Boundaries**: Decouples cosmetic discovery (`CosmeticCatalogueProvider`) and player equipment (`EquippedCosmeticsProvider`) from network and UI code.
- **100% Offline-Safe**: Requires no internet connection, external database, or remote servers to launch and operate.

---

## Tech Stack & Compatibility

| Component | Target Version |
| :--- | :--- |
| **Minecraft** | `1.21.11` |
| **Fabric Loader** | `>=0.19.5` |
| **Fabric API** | `0.141.6+1.21.11` |
| **GeckoLib** | `5.4.5` (Fabric 1.21.11) |
| **Image Pipeline** | TwelveMonkeys ImageIO `3.12.0` (WebP, BMP, TIFF) |
| **Java Toolchain** | `Java 21` (Compilation & Runtime) / `Java 25` (Loom daemon) |
| **Mappings** | Yarn `1.21.11+build.6` |

---

## Building Locally

### Prerequisites
- JDK 21+ installed (JDK 25 recommended for Loom daemon).
- Terminal with Git.

### Run Unit Tests
The test suite is 100% in-memory and verifies `.bbmodel` parsing, JSON round-trips, configuration serialization, and provider contracts:

```bash
./gradlew test
```

### Build the Mod JAR
Compile and remap the production mod JAR:

```bash
./gradlew build
```

The resulting JAR will be generated in:
```text
build/libs/cozmetics-1.5.0.jar
```
*(The final shipped JAR has a lightweight footprint of ~1.3 MB with zero demo assets).*

### Launch Minecraft Client
Launch a test development client:

```bash
./gradlew runClient
```

---

## Blockbench `.bbmodel` Integration Guide

To author cosmetics in Blockbench for Cozmetics:

1. **Model Format**: Create a project using **Bedrock Model**, **Generic Model**, or **GeckoLib Animated Model**.
2. **Anchor Bones**: Group cubes intended to bind to the player under standard anchor bones:
   - `anchor_head` — Binds to player head (hats, crowns, masks).
   - `anchor_body` — Binds to player torso (wings, auras, back gear, pets).
   - `anchor_leftarm` / `anchor_rightarm` — Binds to player arms (gauntlets, fists).
   - `anchor_leftleg` / `anchor_rightleg` — Binds to player legs (boots).
3. **Textures**: Save textures directly in Blockbench so they are embedded as base64 in the `.bbmodel`, or specify external texture paths.
4. **Animations**: Add animations (e.g. `animation.idle`) with rotation, position, or scale keyframes.
5. **Runtime Loading**: Register the model via `BbModelManager.getInstance().load(id, stream)` or convert via `BbModelConverter.convert(inputPath, outputDir)`.

---

## License

This project is licensed under a **Source-Available / All Rights Reserved** license. See [LICENSE](LICENSE) for details.

The source code is publicly accessible for study, education, and audit. Unauthorized copying, modification, re-hosting, redistribution, or commercial use is strictly prohibited.
