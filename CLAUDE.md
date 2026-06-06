# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Development

```bash
./gradlew build        # Builds the shadow JAR → build/libs/StarMSkyblock.jar
```

The project uses Gradle (Groovy DSL — `build.gradle`) with Shadow plugin. Output is a fat JAR. Gradle properties enable configuration cache, parallel builds, and build caching.

- **Java toolchain**: 25 (runs on Paper 1.26.x servers, Java 21+)
- **API target**: Spigot 26.1.2 (Paper)
- Output JAR is a shadow (fat) JAR — all dependencies are bundled.
- **No tests** — `./gradlew test` is not used in this project.
- **TrMenu jar**: `libs/TrMenu-3.12.2.jar` must exist to compile (used via `compileOnly files()`).

## Architecture

This is a Minecraft Spigot/Paper skyblock plugin. See `README.md` for the full module tree and command/placeholder reference. This section covers patterns that require reading multiple files.

### Startup sequence (strict ordering in `StarMSkyblock.onEnable()`)

1. Check WorldEdit/FAWE presence → abort if missing
2. Initialize 6 config managers (config, permissions, settings, sign, generator, upgrades)
3. Extract built-in `.schem` files to `plugins/StarMSkyblock/schematics/`
4. Open SQLite database, run migrations (`PRAGMA table_info` column checks + `ALTER TABLE ADD COLUMN`)
5. Load schematics via WorldEdit API
6. Load all islands from DB into in-memory indices
7. Create/lazily-load the three worlds (overworld, nether, end) with `VoidChunkGenerator`
8. Schedule invitation cleanup task (every 5 min)
9. Initialize 12 permission listeners + 6 setting listeners
10. Register PAPI expansion (if present), TrMenu JS bridge (if present)
11. Register Bukkit event listeners and commands

### Island indexing (in `IslandManager`)

Six `ConcurrentHashMap` indices for O(1) lookup:
- `islandsById` (int → Island)
- `islandsByOwner` (UUID → Island)
- `islandGridIndex` (Long encoded chunk-key → island ID)
- `memberToIslandIndex` (UUID → island ID)
- `coopToIslandsIndex` (UUID → List<Integer>)
- `deleteCountCache` (UUID → int)

New indices must be updated in `loadIslandsFromDatabase()` and all mutation methods.

### Commands: dispatch pattern

Both `IslandCommand` and `AdminCommand` use the same dispatch pattern: strip `-s` silent flag, then dispatch via `Map<String, SubCommand>`. `IslandCommand` routes 25+ player subcommands; `AdminCommand` routes 3 admin subcommands (`setradius`, `setgenerator`, `settask`). Each subcommand extends `SubCommand` (player commands) or `AdminSubCommand` (admin commands). Both abstract classes provide `getIsland()`, `getPlayerName()`, `isLocationSafe()`, `assertMaxArgs()`. Tab completion delegates to each subcommand's `onTabComplete()`.

### Task system

Tasks are organized by **chapters** (directories under `tasks/`), each containing individual task YAML files. `TaskManager` handles progress tracking, registers per-type Bukkit event listeners, and persists player progress as JSON in the `players` table.

**Chapter unlock flow**: A chapter can declare `required` predecessor chapters. All predecessor tasks must be fully claimed before the chapter unlocks. Individual tasks can declare `task_required` predecessor task IDs within the same chapter. Progress is only recorded when both the chapter and all task prerequisites are satisfied.

**Task types** (`TaskType` enum): `BLOCK_BREAK`, `BLOCK_PLACE`, `ITEM` (manual submit), `ENTITY_KILL`, `FARMING`, `FISHING`, `CRAFTING`, `EARN_MONEY`. Each type has a corresponding listener in `task/listener/`. ITEM tasks support optional `potion_type` matching.

**Request format**: Multi-group `request` map — types within a group are "OR", different groups are "AND". Example: group 1 requires 64 cobblestone OR 64 stone, group 2 requires 10 iron ingots.

**Task lifecycle**: Locked (prerequisites unmet) → In Progress (tracking) → Claimable (progress full) → Completed (reward claimed). `/is task submit` manually submits items for ITEM-type; `/is task claim` collects rewards. Admin command `/isadmin settask <player> <chapter> <task> complete|reset` manages progress.

### Vault economy

Optional Vault integration for island upgrades. `StarMSkyblock.getEconomy()` lazily resolves the Vault `Economy` service. Two upgrade paths: island radius (`/is upgrade radius`) and generator level (`/is upgrade generator`), configured in `upgrades.yml` with per-tier cost. Upgrade availability depends on Vault presence — if Vault is absent, upgrade commands return an error.

### Permissions: composite pattern

`IslandPermissionManager` registers 12 domain-specific `BasePermissionManager` subclasses (Block, Container, Door, Redstone, Vehicle, Tool, Item, Entity, Workblock, DropPickup, Management, Other). Each listens for specific Bukkit events and delegates to `Island.hasPermission(UUID, IslandPermission)` for the permission check. Role hierarchy: OWNER(5) > ADMIN(4) > MOD(3) > MEMBER(2) > COOP(1) > VISITOR(0).

### Settings: composite pattern

`IslandSettingManager` registers 6 domain listeners (Explosion, FireSpread, Grief, PhantomSpawn, Pvp, Spawn). Each checks `Island.getSetting(IslandSetting)` before allowing the event.

### Database

SQLite with WAL mode, NORMAL synchronous, busy_timeout=5000. Schema migrations use `PRAGMA table_info` to check column existence, then `ALTER TABLE ADD COLUMN` — never recreates tables. All write operations go through `SQLiteManager.executeInTransaction()` for explicit commit/rollback. Repositories (`IslandRepository`, `PlayerRepository`) handle all queries; `SQLiteManager` handles connection lifecycle and migrations.

### Island create/delete: two-phase async

**Create** (`IslandCreateTask`):
1. Async: compute grid position via Ulam Spiral, insert DB row, paste schematics (three worlds), set biome
2. Sync: clear dropped items, teleport player, send welcome message

**Delete** (`IslandDeleteTask`):
1. Async: clear all blocks from all three worlds, remove DB rows
2. Sync: eject all players from island, kick members, teleport to spawn

### Color/message system

`MessageUtil` supports custom tag-based formatting: `<gradient:#from-#to>text</gradient>`, `<rainbow:saturation:brightness>text</rainbow>`, `<transition:#from-#to>text</transition>`. Implemented via regex tag extraction (`TagContentExtractor`) and per-character color interpolation. Console messages use `MessageUtil.consolePrint/warn/error` — not Bukkit's `getLogger()`.

### WorldEdit/FAWE compatibility

`SchematicManager` uses reflection to detect and invoke either FAWE or WorldEdit paste API. All WorldEdit operations use `EditSession`; FAWE path uses reflection to avoid compile-time dependency.

### Third-party integrations (soft-depend)

- **WorldEdit/FAWE** (hard requirement — plugin disables itself if absent)
- **PlaceholderAPI**: `SkyblockExpansion` registers `starmskyblock` placeholder identifier
- **TrMenu**: `StarMSkyblockHook` registers `StarMSkyblockAPI` binding into TrMenu's `JavaScriptAgent`
