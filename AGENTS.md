# AGENTS.md — StarMSkyblock

## Project Type

Minecraft Spigot/Paper plugin (Java 21) for a skyblock gamemode. Built with Gradle using the Shadow plugin to produce a fat JAR.

## Build & Output

- **Build command:** `./gradlew build` (depends on `shadowJar`)
- **Output JAR:** `build/libs/StarMSkyblock.jar` (base name `StarMSkyblock`, no classifier or version suffix)
- **Shadow plugin:** `com.gradleup.shadow` version `9.4.1`
- **Java toolchain:** 21 (`-Xlint:all` enabled)
- **No tests exist.** Do not expect `./gradlew test` to do anything useful.

## Runtime Environment

- **Target server:** Spigot/Paper API `26.1.2-R0.1-SNAPSHOT`
- **Load order:** `POSTWORLD`
- **Soft dependencies:** `PlaceholderAPI`, `WorldEdit` (FAWE also works)
- **Main class:** `team.starm.starmskyblock.StarMSkyblock`

## Key Dependencies

- `net.kyori:adventure-text-serializer-legacy:4.19.0` (compile-only)
- `com.google.code.gson:gson:2.11.0` (compile-only, runtime provided by server)
- `org.xerial:sqlite-jdbc:3.46.1.0` (compile-only, runtime provided by server)
- `com.sk89q.worldedit:worldedit-bukkit:7.4.2` (compile-only)
- `me.clip:placeholderapi:2.11.6` (compile-only)

## Resources & Schematics

- `plugin.yml` has a version placeholder `${version}` expanded by `processResources` from `build.gradle`.
- Built-in schematics (`default.schem`, `default_nether.schem`, `default_the_end.schem`) live under `src/main/resources/schematics/`.
- `processResources` explicitly includes `schematics/**` with `duplicatesStrategy = INCLUDE`.
- On startup, the plugin extracts these schematics to `<dataFolder>/schematics/` if missing.

## Architecture Notes

- **Entry point:** `StarMSkyblock.java` — initializes configs, SQLite, schematic manager, grid system, world manager, invitation manager, permission coordinator, listeners, and commands.
- **Commands:** `is` (player skyblock command) and `isadmin` (admin command). Both use tab completers.
- **Permission subcommands** (`/is permission`) are delegated to `IslandPermissionCommand`; all other subcommands follow the command pattern via `command/subcommand/` package with a `SubCommand` abstract base class.
- **Worlds:** Three worlds are eagerly created/loaded on enable: normal skyblock world, nether, and end.
- **Database:** SQLite via `SQLiteManager`, stored in the plugin data folder.
- **Permissions & Settings:** YAML-driven (`permissions.yml`, `settings.yml`, `config.yml`). Config managers handle defaults and reloading.
- **Schematic loading:** Uses WorldEdit/FAWE APIs via `SchematicManager`.

## Code Conventions

- Package root: `team.starm.starmskyblock`
- Chinese comments are common in this codebase.
- UTF-8 encoding is enforced for compilation.
- World name checks should go through `SkyblockWorldManager.isSkyblockWorld(World)` / `isSkyblockWorldName(String)` / `isNormalWorld(String)` / `isNetherWorld(String)` / `isEndWorld(String)` rather than comparing strings directly.
- Message utilities are in `MessageUtil` (`team.starm.starmskyblock.message.MessageUtil`).
- Version is sourced from `gradle.properties` via `project.version` in `build.gradle`.
- Subcommand dispatcher pattern: `IslandCommand` routes via `Map<String, SubCommand>`; each subcommand is its own class in `command/subcommand/`.

## Package Structure

- `command/` — `IslandCommand` (dispatcher), `AdminCommand`, `IslandPermissionCommand`
- `command/subcommand/` — `SubCommand` base + individual subcommand classes (Create, Home, Tp, etc.)
- `config/` — `ConfigManager`, `PermissionConfigManager`, `SettingsConfigManager`, `SignConfigManager`, `SchematicConfig`
- `database/` — `SQLiteManager`
- `generator/` — `VoidChunkGenerator`, `SchematicManager`
- `grid/` — `GridManager` (Ulam Spiral)
- `island/` — `Island`, `IslandManager`, `IslandCreateTask`, `IslandDeleteTask`, `InvitationManager`
- `listener/` — `BorderListener`, `PortalListener`
- `permission/` — `IslandPermission` (enum), `IslandPermissionLevel`, `IslandPermissionManager`, `BasePermissionManager`, 12 sub-managers
- `placeholder/` — `SkyblockExpansion` (PAPI)
- `setting/` — `IslandSetting` (enum), `IslandSettingManager`, `BaseSettingManager`, 6 sub-managers
- `message/` — `MessageUtil` (color/console/broadcast utilities)
- `tag/` — `EntityTags`, `ItemTags` (static sets)
- `world/` — `SkyblockWorldManager`

## CI / Automation

- No CI workflows, no pre-commit hooks, no automated testing.

## Relevant Files

- `build.gradle` — build config, shadow JAR setup, resource processing
- `gradle.properties` — version (`1.0-SNAPSHOT`), group (`team.starm`), Gradle caching flags
- `src/main/resources/plugin.yml` — plugin metadata with templated version
- `src/main/resources/config.yml` — main configuration
- `src/main/resources/permissions.yml` — permission group definitions
- `src/main/resources/settings.yml` — island settings defaults

## Skill

When you need to search docs, use Context7.
