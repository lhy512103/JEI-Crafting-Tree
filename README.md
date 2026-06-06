# JEI Crafting Tree

## English

JEI Crafting Tree is a client-side NeoForge mod that adds a recursive crafting tree viewer to the JEI recipe screen.

The mod is designed for recipe planning first: open a recipe from JEI, expand the ingredients recursively, compare alternative ingredients, and view the total material requirements. When a compatible backend such as AE2 Utility is installed, the same tree can also expose AE2-focused actions such as existing-pattern hints, one-click pattern encoding, and pattern upload.

### Features

- Adds an **Open JEI Crafting Tree** button to JEI recipe layouts.
- Builds a recursive recipe tree from JEI recipes.
- Shows a merged overview for same-material branches by layer.
- Calculates rolled-up material amounts and required pattern counts.
- Supports alternative ingredients and recipe selection for child branches.
- Provides a floating total-material panel with JEI recipe/usage shortcuts.
- Can remember selected child recipes locally.
- Exposes a small backend API for external mods to provide AE2 integration.

### Requirements

- Minecraft `1.21.1`
- NeoForge `21.1.215` or newer in the `21.1.x` line
- Java `21`
- JEI `19.21.0` or newer

Optional integration:

- AE2 Utility `1.6.0` or newer, when available, can register a crafting tree backend for AE2 pattern hints, encoding, and upload features.

### Usage

1. Open a recipe in JEI.
2. Click the JEI Crafting Tree button.
3. Left-click unresolved ingredient branches to choose or expand child recipes.
4. Use the overview controls to toggle merged view, quantity calculation, and unique-recipe auto expansion.
5. If an external backend is registered, use **Encode** or **Upload** for supported AE2 workflows.

### Build

```powershell
.\gradlew.bat build
```

The compiled jars are generated in `build/libs/`.

For a faster compile check:

```powershell
.\gradlew.bat compileJava
```

### Development Notes

This repository intentionally does not track local Minecraft run data, generated Gradle output, IDE metadata, or built jar files. Use the Gradle wrapper included in the repository so everyone builds with the same Gradle setup.

### License

This project is licensed under the MIT License. See [LICENSE](LICENSE).

## 中文

JEI Crafting Tree 是一个 NeoForge 客户端模组，会在 JEI 配方界面中加入递归配方树查看器。

这个模组首先面向配方规划：从 JEI 打开某个配方，递归展开原料，对比可替代材料，并查看总材料需求。当安装了 AE2 Utility 等兼容后端时，同一棵配方树还可以显示 AE2 相关能力，例如已有样板提示、一键编码样板和上传样板。

### 功能

- 在 JEI 配方布局中加入 **Open JEI Crafting Tree** 按钮。
- 基于 JEI 配方构建递归配方树。
- 支持按层合并相同材料分支的总览视图。
- 计算汇总后的材料用量和所需样板数量。
- 支持可替代材料和下级分支配方选择。
- 提供悬浮总材料面板，并支持跳转 JEI 配方和用途。
- 可在本地记忆已选择的下级配方。
- 提供轻量后端 API，方便外部模组接入 AE2 集成功能。

### 运行需求

- Minecraft `1.21.1`
- NeoForge `21.1.215` 或 `21.1.x` 系列中的更新版本
- Java `21`
- JEI `19.21.0` 或更新版本

可选集成：

- 如果安装了 AE2 Utility `1.6.0` 或更新版本，它可以注册配方树后端，用于提供 AE2 样板提示、编码和上传功能。

### 使用方式

1. 在 JEI 中打开一个配方。
2. 点击 JEI Crafting Tree 按钮。
3. 左键点击未展开的原料分支，选择或展开下级配方。
4. 使用总览界面的控制按钮切换合并视图、数量演算和唯一配方自动展开。
5. 如果已有外部后端注册，可使用 **Encode** 或 **Upload** 执行支持的 AE2 工作流。

### 构建

```powershell
.\gradlew.bat build
```

编译出的 jar 文件会生成在 `build/libs/`。

如果只需要快速检查编译：

```powershell
.\gradlew.bat compileJava
```

### 开发说明

本仓库不会跟踪本地 Minecraft 运行数据、Gradle 生成输出、IDE 元数据或构建出的 jar 文件。请使用仓库内置的 Gradle wrapper，以保证所有人使用相同的 Gradle 配置构建项目。

### 许可证

本项目使用 MIT License。详见 [LICENSE](LICENSE)。
