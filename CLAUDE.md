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

### World Structure
The plugin creates three void worlds using `VoidChunkGenerator` (no terrain generation):
- **Normal world**: `starmskyblockworld` — island spawn area
- **Nether world**: `starmskyblockworld_nether` — nether dimension
- **End world**: `starmskyblockworld_the_end` — end dimension

Islands are generated from `.schem` schematic files using FastAsyncWorldEdit (FAWE).

### Island Grid System
`GridManager` places islands in a spiral/Ulam spiral pattern. Island positions are computed mathematically from island ID, no database storage needed. Grid cell size = `(maxRadius * 2) + 1 + spacing`.

### Core Manager Classes
- `IslandManager` — in-memory island instances, coordinates with SQLite via `SQLiteManager`
- `IslandPermissionManager` — coordinator for 12 specialized permission managers (one per action category)
- `SkyblockWorldManager` — creates/loads void worlds and handles portal logic
- `SchematicManager` — handles FAWE schematic operations

### Permission System
`IslandPermissionManager` delegates to specialized managers in `permission/manager/`:
- `BlockPermissionManager`, `ContainerPermissionManager`, `DoorPermissionManager`, `EntityPermissionManager`, `ItemPermissionManager`, `ManagementPermissionManager`, `OtherPermissionManager`, `PickupPermissionManager`, `RedstonePermissionManager`, `ToolPermissionManager`, `VehiclePermissionManager`, `WorkblockPermissionManager`

Each handles events for its category (block place/break, container interaction, etc.). Permission checking respects island boundaries — only players within an island's chunk radius are subject to its permissions.

### Database Schema
SQLite at `{plugin_data_folder}/islands.db`:
- `islands` — island metadata (id, owner_uuid, name, radius, center coords, home positions)
- `island_members` — player island assignments with roles
- `island_permissions` — per-island custom permission overrides
- `player_stats` — deletion counts for delete-limit enforcement

### Async Task Pattern
Long-running operations (island create/delete) run as async Bukkit tasks (`BukkitTask.runTaskAsynchronously()`) to avoid blocking the server. These tasks coordinate with `IslandManager` through thread-safe methods like `removeIslandFromMemory()`.

### Commands
- `/is` — player island commands (create, home, sethome, border, delete, invite, kick, promote, demote, members, role, permission, permissions, accept, decline)
- `/isadmin` — admin commands (setradius)

## Key File Locations

- `src/main/java/team/starm/starmskyblock/StarMSkyblock.java` — plugin entry point, manager initialization
- `src/main/java/team/starm/starmskyblock/island/Island.java` — island entity with permission checking
- `src/main/java/team/starm/starmskyblock/grid/GridManager.java` — island positioning via spiral algorithm
- `src/main/java/team/starm/starmskyblock/generator/VoidChunkGenerator.java` — empty world generation
- `src/main/java/team/starm/starmskyblock/generator/SchematicManager.java` — schematic paste logic
- `src/main/resources/schematics/` — built-in island templates
- `src/main/resources/config.yml` — default configuration