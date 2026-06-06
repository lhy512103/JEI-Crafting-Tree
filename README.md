# JEI Crafting Tree

JEI Crafting Tree is a client-side NeoForge mod that adds a recursive crafting tree viewer to the JEI recipe screen.

The mod is designed for recipe planning first: open a recipe from JEI, expand the ingredients recursively, compare alternative ingredients, and view the total material requirements. When a compatible backend such as AE2 Utility is installed, the same tree can also expose AE2-focused actions such as existing-pattern hints, one-click pattern encoding, and pattern upload.

## Features

- Adds an **Open JEI Crafting Tree** button to JEI recipe layouts.
- Builds a recursive recipe tree from JEI recipes.
- Shows a merged overview for same-material branches by layer.
- Calculates rolled-up material amounts and required pattern counts.
- Supports alternative ingredients and recipe selection for child branches.
- Provides a floating total-material panel with JEI recipe/usage shortcuts.
- Can remember selected child recipes locally.
- Exposes a small backend API for external mods to provide AE2 integration.

## Requirements

- Minecraft `1.21.1`
- NeoForge `21.1.215` or newer in the `21.1.x` line
- Java `21`
- JEI `19.21.0` or newer

Optional integration:

- AE2 Utility `1.6.0` or newer, when available, can register a crafting tree backend for AE2 pattern hints, encoding, and upload features.

## Usage

1. Open a recipe in JEI.
2. Click the JEI Crafting Tree button.
3. Left-click unresolved ingredient branches to choose or expand child recipes.
4. Use the overview controls to toggle merged view, quantity calculation, and unique-recipe auto expansion.
5. If an external backend is registered, use **Encode** or **Upload** for supported AE2 workflows.

## Build

```powershell
.\gradlew.bat build
```

The compiled jars are generated in `build/libs/`.

For a faster compile check:

```powershell
.\gradlew.bat compileJava
```

## Development Notes

This repository intentionally does not track local Minecraft run data, generated Gradle output, IDE metadata, or built jar files. Use the Gradle wrapper included in the repository so everyone builds with the same Gradle setup.

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE).
