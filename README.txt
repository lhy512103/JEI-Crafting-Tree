# JEI Crafting Tree

JEI Crafting Tree is a client-side Minecraft Forge mod that adds a JEI-backed
crafting tree screen for planning crafting chains.

It lets you open a recipe tree from JEI, inspect the materials required for a
target item, choose alternative input ingredients, collapse branches, and adjust
the final output batch count.

## Supported Version

- Minecraft: 1.20.1
- Forge: 47.4.10 or compatible Forge 47.x
- JEI: 15.0.0 or newer
- Java: 17+

This branch is for the Forge 1.20.1 build.

## Features

- Opens a recipe tree from JEI recipe transfer integration.
- Displays a top-down crafting tree with recipe catalyst icons and output items.
- Shows total consumed materials and leftover materials in separate summary rows.
- Supports collapsing recipe branches; collapsed branches are counted as required
  external materials instead of expanding their sub-recipes.
- Allows the final output batch count to be changed by scrolling or typing.
- Supports JEI recipe selection for child nodes.
- Supports alternative input ingredient selection for tag-like recipe inputs,
  such as any plank accepted by `#minecraft:planks`.
- Remembers selected recipes and alternative ingredients in the local config
  folder, so future trees can reuse the same choices after restarting the game.

## Controls

- Left-click a node: open JEI recipe selection for that item.
- Right-click a node: collapse or expand its recipe branch.
- Right-drag empty space: pan the tree view.
- Mouse wheel on the final output node: change batch count.
- Shift + mouse wheel on the final output node: change batch count by 10.
- Type digits while the final output node is selected: enter batch count.
- Ctrl + mouse wheel: zoom the tree view.
- Left-click a material in the summary row: open JEI recipe selection for that
  material.
- Click the small selector button next to an input node: choose an alternative
  accepted input item.

## Building

Use the Gradle wrapper:

```bash
./gradlew build
```

On Windows:

```bat
gradlew.bat build
```

The built jar will be generated under `build/libs`.

## Development

Common development commands:

```bash
./gradlew compileJava
./gradlew runClient
```

For IntelliJ IDEA or Eclipse, import the Gradle project normally. The project
uses official Mojang mappings for Minecraft 1.20.1.

## License

This project is licensed under the MIT License. See `LICENSE.txt` for details.
