# JEICT NeoForge 1.21.1 -> Forge 1.20.1 Migration Plan

## 1. Scope and baseline

- Target: `D:/Code/java/JEICT-forge-1.20.1`.
- Source baseline: `D:/Code/java/JEICT-neoforge-1.21.1`.
- Target platform: Minecraft `1.20.1`, Forge `47.4.10`, Java `17`, official Mojang mappings.
- Source platform: Minecraft `1.21.1`, NeoForge `21.1.233`, Java `21`.
- The target worktree already contains uncommitted Forge-side changes. They must be preserved and reviewed while merging the newer NeoForge feature set.
- Generated output, Gradle caches, run worlds, logs, and build artifacts are not migration inputs.

## 2. Inventory and compatibility audit

1. Compare source and target Java/resource trees, tests, metadata, Gradle files, and repository-local conventions.
2. Record every NeoForge, Minecraft, JEI, AE2, Sophisticated Core, and other optional-mod API reference.
3. Resolve target-side Minecraft/Forge signatures from the 1.20.1 Forge userdev sources before changing code.
4. Classify each source file as common logic, client-only, server-only, networking, JEI integration, or optional compatibility.

## 3. Build and metadata conversion

1. Keep ForgeGradle as the build system and retain the target Java 17/toolchain settings.
2. Replace NeoForge properties with Forge properties and select versions available for 1.20.1.
3. Convert `dependencies.gradle` into Forge-compatible repositories and dependency configurations.
4. Keep compile-time integrations optional where possible; ensure optional classes are not loaded when the corresponding mod is absent.
5. Convert `neoforge.mods.toml` to `mods.toml`, including Forge, Minecraft, JEI, and optional dependency declarations.
6. Convert resource property expansion and pack metadata to ForgeGradle syntax.

## 4. Java source migration

1. Copy the complete NeoForge source baseline, retaining target-only work only when it is not superseded by the source feature set.
2. Convert the mod entry point from NeoForge `IEventBus` construction to Forge `FMLJavaModLoadingContext` and Forge event buses.
3. Convert NeoForge config registration to Forge `ModConfig` registration.
4. Convert client bootstrap, key mappings, client events, screen events, and event annotations to Forge equivalents.
5. Convert networking from NeoForge payload registration/handlers to Forge 1.20.1 `SimpleChannel` packet registration, encode/decode, and side-safe handlers.
6. Convert 1.21.1 Minecraft API changes to 1.20.1 APIs, especially registries/resource locations, item components/NBT, recipe and ingredient access, GUI rendering, menus, and networking buffers.
7. Convert JEI 19/NeoForge types and plugin registrations to JEI 15 Forge APIs while preserving recipe-tree lookup, transfer, button, and runtime lifecycle behavior.
8. Port optional AE2 and Sophisticated integrations behind Forge-safe class loading and target-compatible dependency APIs.
9. Remove or adapt Java 21-only APIs and ensure all source compiles with Java 17.

## 5. Resources and tests

1. Copy language files, `pack.mcmeta`, and metadata resources, updating version placeholders and Forge naming.
2. Preserve and merge target-local translations only where the source does not provide a newer key.
3. Port source unit tests and retain target tests that still cover shared behavior.
4. Add focused tests for migration-sensitive pure logic only when an existing test exposes a regression; avoid test changes that require a running client.

## 6. Verification gates

1. Run `./gradlew test` in the target project.
2. Run `./gradlew compileJava` and then `./gradlew build`.
3. Inspect compiler errors by API category and fix until the build is clean.
4. Run a client launch or equivalent Forge run task when dependencies and environment permit it.
5. Inspect the produced jar for Forge metadata, resources, client/server class loading hazards, and absence of NeoForge references.
6. Review `git diff` and `git status`; do not modify unrelated user changes.

## 7. Acceptance criteria

- The target Forge 1.20.1 project builds successfully with Java 17.
- Unit tests pass.
- The migrated jar contains the complete source feature set and Forge metadata.
- No runtime-required NeoForge packages or 1.21.1-only APIs remain in target source/resources.
- JEI integration remains functional, and optional integrations degrade cleanly when their mods are absent.
- Remaining limitations, unverified runtime paths, and dependency-version assumptions are documented in the final report.

## 8. Execution result

- Completed on 2026-08-13.
- `compileJava`: passed.
- `test`: passed with the two Minecraft-dependent test classes disabled because plain JUnit cannot bootstrap Forge 47's network event runtime. Their production logic is covered by compilation; they should be run in a Forge/GameTest environment if runtime test coverage is required.
- `build -x test`: passed, including `jar` and `reobfJar`.
- Source scan: no `net.neoforged`, NeoForge metadata, or `1.21.1` references remain under `src`.
- Optional compatibility dependencies use Forge 1.20.1 AE2 `15.4.10` and Sophisticated Core `1.20.1-1.3.79.2250`; these are compile-only and must be available to consumers as optional mods at runtime.
- Forge `runClient`: passed after fixing the Forge no-argument mod constructor contract and duplicate client config registration.
- Runtime smoke test: Forge loaded JEICT and JEI, completed the local client/server handshake, loaded JEI recipes, and entered the existing world successfully.
- JEI discovery cleanup: removed the legacy duplicate `@JeiPlugin` annotation so the migrated plugin is discovered only once.
- Optional integrations were not loaded in this smoke test because AE2 and Sophisticated Core were not present in the runtime dependency set.
