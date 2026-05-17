# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Build the plugin (produces StarMSkyblock.jar in build/libs/)
./gradlew build

# Build with shadow JAR (the runnable plugin)
./gradlew shadowJar

# Clean build artifacts
./gradlew clean
```

## Architecture Overview

### Schematic Paster System (Three-Mode Architecture)
`SchematicManager` delegates to a `SchematicPaster` chosen at startup based on `schematic-mode` in `config.yml`:
- **`SchematicPaster`** (interface) — `pasteSchematic(file, world, x, y, z)` + `clearArea(world, ...)`
- **`VanillaPaster`** — parses `.schem` Sponge v2 NBT files using a standalone `NbtReader` (no external deps), places blocks via Bukkit `World.setBlockData()`, clears areas via chunk iteration. Works on any server.
- **`WorldEditPaster`** — uses WorldEdit API (`com.sk89q.worldedit.*`), works with both WorldEdit and FAWE at runtime. The WE dependency is `compileOnly`; at runtime, `StarMSkyblock.createSchematicPaster()` checks `Class.forName("com.sk89q.worldedit.WorldEdit")` and falls back to VANILLA if unavailable.

### World Structure
The plugin creates three void worlds using `VoidChunkGenerator` (no terrain generation):
- **Normal world**: configurable via `config.yml` (default `StarM_Skyblock`) — island spawn area
- **Nether world**: `{worldName}_nether` — nether dimension
- **End world**: `{worldName}_the_end` — end dimension

Islands are generated from `.schem` schematic files using FastAsyncWorldEdit (FAWE). Each island type (schematicId) can have its own teleport offsets per world type, configured in `config.yml` under `schematics.{id}.teleport-offset.{normal|nether|end}`.

### Island Grid System
`GridManager` places islands in a spiral/Ulam spiral pattern. Island positions are computed mathematically from island ID, no database storage needed. Grid cell size = `(maxRadius * 2) + 1 + spacing`.

### Core Manager Classes
- `IslandManager` — in-memory island instances (HashMap by id and by owner), coordinates with SQLite via `SQLiteManager`
- `IslandPermissionManager` — coordinator for 12 specialized permission managers (one per action category), registered as Bukkit event listeners
- `IslandSettingManager` — coordinator for 5 specialized setting managers (PVP, spawns, fire spread, griefing, explosions), also Bukkit listeners
- `SkyblockWorldManager` — creates/loads void worlds and handles portal logic
- `SchematicManager` — handles FAWE schematic operations (paste, load)
- `InvitationManager` — in-memory pending invitations with 5-minute expiry

### Permission System (Two-Layer Design)

**Layer 1 — the `*` prefix** (what `BasePermissionManager` does): All 12 permission managers inherit from `BasePermissionManager` (which is `Listener`). Its `checkPermission()` method:
1. Filters non-skyblock worlds (always allowed)
2. Finds the island at the chunk location using *max radius* (for border checking)
3. If the chunk is outside the island's actual radius → `area locked` (player near border but on an unexpanded chunk)
4. If no island at all → `public area`
5. Finally delegates to `Island#hasPermission(uuid, permission)` for the role-based check

`BasePermissionManager.sendDenyMessage()` has anti-spam cooldown and different messages for area-locked vs public-area vs no-permission cases.

**Layer 2 — the island instance** (`Island#hasPermission`): Checks the player's role against per-permission minimum-level thresholds stored as `Map<IslandPermission, Integer>` in memory. Permissions are persisted as a JSON column in the `islands` table. When a permission has no custom level set, the system falls back to `IslandPermission.ALL` as catch-all, then to `permissions.yml` defaults (applied on island creation and first-load migration).

### Setting System (Parallel to Permissions)
Mirrors the permission system structure:
- `BaseSettingManager` — common `checkSetting(location, setting)` with world filtering
- `IslandSettingManager` — coordinator registering 5 sub-managers as Bukkit listeners
- Sub-managers: `PvpSettingManager`, `SpawnSettingManager`, `FireSpreadSettingManager`, `GriefSettingManager`, `ExplosionSettingManager`
- Each setting is a boolean (enabled/disabled), persisted as JSON in the `settings` column of `islands`, defaults from `settings.yml`

### Role Hierarchy
Six levels defined in `IslandPermissionLevel`: OWNER(5) > ADMIN(4) > MOD(3) > MEMBER(2) > COOP(1) > VISITOR(0).

Member roles use MEMBER/MOD/ADMIN (not COOP). Coops are stored in a **separate** `Set<UUID>` (`island_coops` table), not in the members map. The coop role only applies to players who are not members but have been temporarily added.

### Database Schema
SQLite at `{plugin_data_folder}/islands.db`:
- `islands` — island metadata (id, owner_uuid, name, level, radius, center coords, home_data JSON, permissions JSON, settings JSON)
- `island_members` — player island assignments with roles (island_id, player_uuid, role)
- `island_coops` — cooperative players (island_id, player_uuid) — separate from members
- `player_stats` — deletion counts for delete-limit enforcement (player_uuid, delete_count)
- `player_names` — cached player name lookups (uuid, name)

### Async Task Pattern
Long-running operations (island create/delete) run as async Bukkit tasks (`BukkitTask.runTaskAsynchronously()`). `IslandCreateTask` runs synchronously on main thread for FAWE schematic paste (must be sync). `IslandDeleteTask` coordinates with `IslandManager` through thread-safe `removeIslandFromMemory()`.

### PlaceholderAPI Integration
`SkyblockExpansion` (identifier: `starmskyblock`) provides placeholders:
- `%starmskyblock_island%` — island name at player's location
- `%starmskyblock_role%` — player's role at current island (includes coop visitor check)
- `%starmskyblock_permission_{PERM}_level_weight%` — min level for a permission
- `%starmskyblock_permission_{PERM}_level_{ROLE}%` — whether a role has a permission (colored ✔/✘)

### Tag Helper Classes
- `EntityTags` — `EnumSet<EntityType>` for chest boats, rideable entities, leashable entities, animals with inventory
- `ItemTags` — `EnumSet<Material>` for horse armor, nautilus armor, buckets, minecarts, dyes, waxable copper blocks, copper variants

### Message System
- `ColorUtil` — color translation (`&`→`§`), silent mode per-player UUID (suppresses messages when `-s` flag used), console methods
- `MessageUtil` — thin wrapper over `ColorUtil` providing `sendMessage()`, `broadcast()`, `consolePrint()`, parsing to Adventure `Component`

### Config Files
- `ConfigManager` — loads `config.yml` (island params, schematics with per-world teleport offsets, world names, biomes, miscellaneous settings)
- `PermissionConfigManager` — loads `permissions.yml` (default permission min-level values applied to new islands)
- `SettingsConfigManager` — loads `settings.yml` (default boolean values for island settings applied to new islands)
- `SchematicConfig` — data class holding schematic file name + 3 sets of teleport offsets (normal/nether/end)

### Commands
- `/is` — player island commands handled by `IslandCommand`:
  - `create [type] [name]` — async island creation (checks island limit)
  - `home [confirm]` — teleport (custom home or default schematic offset; unsafe-location warning)
  - `tp <name> [confirm]` — teleport to any island by name (respects TP setting)
  - `sethome` — set custom home (must be on own island, solid block below, respects config per-world)
  - `border [true|false|toggle]` — toggle visual island border
  - `delete [confirm]` — async deletion (respects max-delete limit)
  - `invite <player>` / `accept` / `decline` — invitation flow (5-min expiry, `InvitationManager`)
  - `remove <player> [confirm]` — kick member
  - `promote` / `demote` — cycle between MEMBER↔MOD↔ADMIN (hierarchical permission check)
  - `coop add <player>` / `coop remove <player>` — add/remove coop (coop player must have their own island)
  - `members` / `coops` / `mycoops` / `role` / `myperms` — info commands
  - `rename <name>` — rename island
  - `permission <perm> [role/level/cycle/rcycle]` — set permission min level (`IslandPermissionCommand`)
  - `settings [key] [true|false|toggle]` — toggle island settings
  - `-s` suffix on any command suppresses output messages (silent mode)
- `/isadmin setradius <owner> <radius>` — admin command to resize an island's radius

### Permission Manager Categories (12 sub-managers of `IslandPermissionManager`)
Block, Container, Door, DropPickup, Entity, Item, Management, Other, Redstone, Tool, Vehicle, Workblock. Each handles Bukkit events for its category. `ManagementPermissionManager` also provides the static `lacksPermission()` helper used by commands.

### Setting Manager Categories (5 sub-managers of `IslandSettingManager`)
Pvp, Spawn (animal/monster/spawner), FireSpread, Grief (enderman, ghast, wither), Explosion (creeper, TNT). Each checks `island.getSetting()` before allowing the event.

## Key File Locations

- `src/main/java/team/starm/starmskyblock/StarMSkyblock.java` — plugin entry point, all manager initialization
- `src/main/java/team/starm/starmskyblock/island/Island.java` — island entity with role/permission/setting checks
- `src/main/java/team/starm/starmskyblock/island/IslandManager.java` — CRUD, perms/settings persistence, chunk-queries
- `src/main/java/team/starm/starmskyblock/island/InvitationManager.java` — 5-min cooldown invitation flow
- `src/main/java/team/starm/starmskyblock/island/IslandCreateTask.java` — async island creation pipeline
- `src/main/java/team/starm/starmskyblock/island/IslandDeleteTask.java` — async island deletion pipeline
- `src/main/java/team/starm/starmskyblock/permission/BasePermissionManager.java` — common permission check logic
- `src/main/java/team/starm/starmskyblock/permission/IslandPermissionManager.java` — permission coordinator
- `src/main/java/team/starm/starmskyblock/setting/BaseSettingManager.java` — common setting check logic
- `src/main/java/team/starm/starmskyblock/setting/IslandSettingManager.java` — setting coordinator
- `src/main/java/team/starm/starmskyblock/command/IslandCommand.java` — `/is` command handler (~1140 lines)
- `src/main/java/team/starm/starmskyblock/command/IslandPermissionCommand.java` — `/is permission` subcommand
- `src/main/java/team/starm/starmskyblock/command/AdminCommand.java` — `/isadmin` command handler
- `src/main/java/team/starm/starmskyblock/grid/GridManager.java` — island positioning via spiral algorithm
- `src/main/java/team/starm/starmskyblock/generator/VoidChunkGenerator.java` — empty world generation
- `src/main/java/team/starm/starmskyblock/generator/SchematicManager.java` — FAWE schematic paste logic
- `src/main/java/team/starm/starmskyblock/database/SQLiteManager.java` — SQLite connection pool
- `src/main/java/team/starm/starmskyblock/config/ConfigManager.java` — config.yml loader
- `src/main/java/team/starm/starmskyblock/config/PermissionConfigManager.java` — permissions.yml loader
- `src/main/java/team/starm/starmskyblock/config/SettingsConfigManager.java` — settings.yml loader
- `src/main/java/team/starm/starmskyblock/listener/BorderListener.java` — visual island border rendering
- `src/main/java/team/starm/starmskyblock/listener/PortalListener.java` — nether/end portal travel
- `src/main/java/team/starm/starmskyblock/world/SkyblockWorldManager.java` — void world creation/loading
- `src/main/java/team/starm/starmskyblock/placeholder/SkyblockExpansion.java` — PlaceholderAPI expansion
- `src/main/java/team/starm/starmskyblock/message/MessageUtil.java` — message sending wrapper
- `src/main/java/team/starm/starmskyblock/util/ColorUtil.java` — color/silent mode utilities
- `src/main/java/team/starm/starmskyblock/tag/EntityTags.java` — entity type tag sets
- `src/main/java/team/starm/starmskyblock/tag/ItemTags.java` — material tag sets
- `src/main/resources/schematics/` — built-in island templates (3 files: default.schem, default_nether.schem, default_the_end.schem)
- `src/main/resources/config.yml` — default configuration
- `src/main/resources/permissions.yml` — default permission min-levels
- `src/main/resources/settings.yml` — default island settings
