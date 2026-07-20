# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Development

```bash
./gradlew build        # Builds the shadow JAR -> build/libs/StarMSkyblock-1.0.0.jar
```

The project uses Gradle (Groovy DSL - `build.gradle`) with Shadow plugin. Output is a fat JAR. Gradle properties enable configuration cache, parallel builds, and build caching.

- **Java toolchain**: 25; bytecode target Java 25 (`options.release = 25`). Runtime requires Java 25 — Paper 26.1.2 stable's paper-api is Java 25 (class major 69), so any Paper 26.1.2 stable server is already Java 25. (Previously targeted Java 21 against a stale Java-17 `spigot-api` snapshot.)
- **API target**: Paper API (`io.papermc.paper:paper-api:26.1.2.build.70-stable`, compileOnly) — superset of the Spigot API; provides Paper-only events (`EntityLoadCrossbowEvent`, `PlayerLaunchProjectileEvent`) used to intercept projectile consumption cleanly. VaultAPI's transitive `org.bukkit:bukkit` is excluded to avoid a capability conflict with paper-api's declared `org.bukkit:bukkit` capability.
- Output JAR is a shadow (fat) JAR - all dependencies are bundled.
- **Tests** - 纯逻辑单元测试（JUnit 5 Jupiter），覆盖 Ulam 螺旋 / 表达式解析器 / 等级公式 / 权限层级 / 坐标位打包；`./gradlew test` 执行（`./gradlew build` 自动跑），不依赖 Bukkit 运行时。纯逻辑抽成无 Bukkit 依赖的 helper 类（`level/ExpressionParser`、`level/LevelFormula`、`grid/UlamSpiral`、`util/BlockCoordKeys`）。
- **Compile-only `libs/` jars** (must exist to compile, provided by the server at runtime via the respective soft-depend plugin):
  - `libs/AuraSkills-2.3.12.jar` - island level skill contribution
  - `libs/mcMMO.jar` - island level skill contribution (alternative to AuraSkills)
  - `libs/TrMenu-3.12.2.jar` - TrMenu JS bridge + bundled menus
- **Adventure API** (`net.kyori`) is used for message Components (compileOnly, provided by Paper).

## Architecture

This is a Minecraft Spigot/Paper skyblock plugin. See `README.md` for the full module tree and command/placeholder reference. This section covers patterns that require reading multiple files.

### Startup sequence (strict ordering in `StarMSkyblock.onEnable()`)

Each step is a private `init*()` method; order encodes the dependency graph.

1. `printLogo()` then `checkWorldEdit()` -> abort (disable plugin) if WorldEdit/FAWE absent
2. `initConfigs()` - initialize **7** config managers: `ConfigManager`, `PermissionConfigManager`, `SettingsConfigManager`, `GeneratorConfigManager`, `UpgradeConfigManager`, `PublicAreaConfigManager`, `LockedAreaConfigManager` (the last two wrap the permission config manager). The old per-feature `sign` config was merged into the main config system.
3. `initLanguage()` - `LanguageManager` (i18n), injected into `MessageUtil` (see i18n section)
4. `extractSchematics()` - extracts built-in `.schem` files to `plugins/StarMSkyblock/schematics/`
5. `initDatabase()` - open SQLite, run migrations; `PlayerRepository` warms up its name cache
6. `initTasks()` - `TaskConfigScanner` + `TaskManager` (must come after DB, before islands)
7. `initSchematicManager()` - load schematics via WorldEdit API
8. `initGridAndIslands()` - `GridManager` + `IslandManager`, loads all islands from DB into in-memory indices
9. `initWorlds()` - create/lazily-load the three worlds (overworld, nether, end) with `VoidChunkGenerator`
10. `initLevelSystem()` - `ExperienceConfig`, `AuraSkillsContributionConfig`, `LevelManager`
11. `initInvitations()` - schedules invitation cleanup task (every 5 min / 6000 ticks)
12. `initPermissions()` - `IslandPermissionManager` (composite, also receives public/locked area managers)
13. `registerListeners()` - border, portal, teleport countdown, settings composite, end protection, block place, boundary, cobblestone generator (if enabled), obsidian-to-lava (if enabled), respawn
14. `registerCommands()` - `/is` (player) and `/isadmin` (admin)
15. `registerIntegrations()` - PlaceholderAPI expansion, skull-texture refresh listener, TrMenu JS bridge (+ late-load `PluginEnableEvent` hook), Vault economy
16. `preWarmWorlds()` - eagerly load the three worlds

`onDisable()` calls `taskManager.saveAll()`, then `HandlerList.unregisterAll(this)` (unregisters every listener this plugin registered, including the composite permission/setting listeners - critical for clean `/reload`), cancels scheduled tasks, and closes the DB.

### Island indexing (in `IslandManager`)

Six `ConcurrentHashMap` indices for O(1) lookup:
- `islandsById` (int -> Island)
- `islandsByOwner` (UUID -> Island)
- `islandGridIndex` (Long encoded chunk-key -> island ID)
- `memberToIslandIndex` (UUID -> island ID)
- `coopToIslandsIndex` (UUID -> List<Integer>)
- `deleteCountCache` (UUID -> int)

New indices must be updated in `loadIslandsFromDatabase()` and all mutation methods.

### Commands: dispatch pattern

Both `IslandCommand` and `AdminCommand` use the same dispatch pattern: strip `-s` silent flag, then dispatch via `Map<String, SubCommand>` (`-s` sets a silent flag on the player UUID via `MessageUtil.setSilent`, auto-cleared in a `finally`). `IslandCommand` routes 25+ player subcommands; `AdminCommand` routes 4 admin subcommands (`setradius`, `setgenerator`, `settask`, `reload`). Each subcommand extends `SubCommand` (player commands) or `AdminSubCommand` (admin commands). Both abstract classes provide `getIsland()`, `getPlayerName()`, `isLocationSafe()`, `assertMaxArgs()`. Tab completion delegates to each subcommand's `onTabComplete()`.

`/isadmin reload` (`ReloadCommand`) reloads every config manager in a fixed order, then `LanguageManager.reload()`, and reports elapsed time - new config files must be added here to be reloadable.

### i18n system

`LanguageManager` loads the active locale from `config.yml` (`locale: 'zh_CN'`) into `messages/<locale>.yml`, flattens the YAML into a `Map<String, String>` (full dotted path -> string). The bundled `messages/zh_CN.yml` is extracted on first start; external file overrides the bundled one. Missing locale falls back to `zh_CN`; if that is also missing the plugin disables itself.

- **Call pattern**: `MessageUtil.send(sender, "dotted.key", Map.of("name", value))`. Placeholders use `{name}` substitution. `MessageUtil.send(sender, "key")` for no-arg messages. Console uses `MessageUtil.consolePrint/warn/error` - not Bukkit's `getLogger()`.
- **Missing key**: returns the literal key text and warns once per key (deduped via `warnedMissingKeys`) to avoid log flooding.
- Message keys are the source of truth - user-facing strings are being migrated from hardcoded Chinese to keys (see recent git history). When adding a message, add the key to `messages/zh_CN.yml` and reference it via `MessageUtil.send`.

### Task system

Tasks are organized by **chapters** (directories under `tasks/`), each containing individual task YAML files. `TaskManager` handles progress tracking, registers per-type Bukkit event listeners, and persists player progress as JSON in the `players` table.

**Chapter unlock flow**: A chapter can declare `required` predecessor chapters. All predecessor tasks must be fully claimed before the chapter unlocks. Individual tasks can declare `task_required` predecessor task IDs within the same chapter. Progress is only recorded when both the chapter and all task prerequisites are satisfied.

**Task types** (`TaskType` enum): `BLOCK_BREAK`, `BLOCK_PLACE`, `ITEM` (manual submit), `ENTITY_KILL`, `FARMING`, `FISHING`, `CRAFTING`, `EARN_MONEY`. Each type has a corresponding listener in `task/listener/`. ITEM tasks support optional `potion_type` matching.

**Request format**: Multi-group `request` map - types within a group are "OR", different groups are "AND". Example: group 1 requires 64 cobblestone OR 64 stone, group 2 requires 10 iron ingots.

**Task lifecycle**: Locked (prerequisites unmet) -> In Progress (tracking) -> Claimable (progress full) -> Completed (reward claimed). `/is task submit` manually submits items for ITEM-type; `/is task claim` collects rewards. Admin command `/isadmin settask <player> <chapter> <task> complete|reset` manages progress.

### Level system

`/is level` triggers `LevelManager.calculateIsland()`, which checks a per-owner cooldown (from `config.yml`, 0 = no cooldown) then runs `IslandLevelCalculator` as a `runTaskTimer` task scanning the island's blocks across all three worlds (`CHUNKS_PER_TICK = 16`).

- **Block level**: each block material has an experience value and a count cap (threshold); counts above the cap decay by a configured diminishing factor. Total experience is converted to a level via a power-function level-cost curve (`level.yml`). All configured in `level.yml` (consolidated from the former `block-values.yml` + `auraskills-contribution.yml`).
- **Template baseline**: when an island is created, `LevelManager.saveBaseline()` reads the pasted schematic's blocks directly from the `SchematicManager` clipboard (pure memory, no chunk load) and persists baseline counts/experience; these are subtracted so only player-placed blocks count. Toggle via `ExperienceConfig.isBaselineEnabled()`.
- **Skill contribution (optional)**: `AuraSkillsContributionConfig` selects `auraskills` or `mcmmo` as the skill source. If the chosen plugin is present, the sum of all island members' PowerLevel is divided by a coefficient to produce a bonus level, capped at `maxBonusLevel`. `McMMOIntegration` queries offline players synchronously (mcMMO supports it natively); `AuraSkillsIntegration` is async. Both return the same `AuraSkillsIslandResult` shape so `LevelManager` treats them interchangeably. The bonus is added to the block level and persisted alongside it.

### Vault economy

Optional Vault integration for island upgrades. `StarMSkyblock.getEconomy()` lazily resolves the Vault `Economy` service. Two upgrade paths: island radius (`/is upgrade radius`) and generator level (`/is upgrade generator`), configured in `upgrades.yml` with per-tier cost. Upgrade availability depends on Vault presence - if Vault is absent, upgrade commands return an error.

### Permissions: composite pattern

`IslandPermissionManager` registers 12 domain-specific `BasePermissionManager` subclasses (Block, Container, Door, Redstone, Vehicle, Tool, Item, Entity, Workblock, DropPickup, Management, Other). Each listens for specific Bukkit events and delegates to `Island.hasPermission(UUID, IslandPermission)` for the permission check. Role hierarchy: OWNER(5) > ADMIN(4) > MOD(3) > MEMBER(2) > COOP(1) > VISITOR(0). The coordinator also receives `PublicAreaConfigManager` and `LockedAreaConfigManager` so permission checks can be overridden per-region.

### Settings: composite pattern

`IslandSettingManager` registers 6 domain listeners (Explosion, FireSpread, Grief, PhantomSpawn, Pvp, Spawn). Each checks `Island.getSetting(IslandSetting)` before allowing the event. Like the permission manager, it also consults the public/locked area managers for per-region overrides.

### Public / locked areas

`PublicAreaConfigManager` and `LockedAreaConfigManager` define regions whose permission/setting rules are independent of the owning island. They are threaded into the permission coordinator, the settings manager, and several listeners (e.g. `ObsidianToLavaListener`) so that protection logic can short-circuit or override based on region membership. See `docs/superpowers/specs/2026-06-16-public-worlds-design.md` for the design rationale.

### Database

SQLite with WAL mode, NORMAL synchronous, busy_timeout=5000. Schema migrations use `PRAGMA table_info` to check column existence, then `ALTER TABLE ADD COLUMN` - never recreates tables. All write operations go through `SQLiteManager.executeInTransaction()` for explicit commit/rollback. Repositories (`IslandRepository`, `PlayerRepository`) handle all queries; `SQLiteManager` handles connection lifecycle and migrations.

### Island create/delete: two-phase async

**Create** (`IslandCreateTask`):
1. Async: compute grid position via Ulam Spiral, insert DB row, paste schematics (three worlds), set biome
2. Sync: clear dropped items, teleport player, send welcome message

**Delete** (`IslandDeleteTask`):
1. Async: clear all blocks from all three worlds, remove DB rows
2. Sync: eject all players from island, kick members, teleport to spawn

### Color/message system

`MessageUtil` (Adventure-based) supports custom tag-based formatting via `ColorUtils`: `<gradient:#from-#to>text</gradient>`, `<rainbow:saturation:brightness>text</rainbow>`, `<transition:#from-#to>text</transition>`, plus `&` legacy codes and `&#RRGGBB` hex. Implemented via regex tag extraction (`TagContentExtractor`) and per-character color interpolation. For client-side translation (e.g. block names via `TranslatableComponent`), use `MessageUtil.sendMessage(Component)`.

### WorldEdit/FAWE compatibility

`SchematicManager` uses reflection to detect and invoke either FAWE or WorldEdit paste API. All WorldEdit operations use `EditSession`; the FAWE path uses reflection to avoid a compile-time dependency. `config.yml`'s `use-fawe` flag controls which path is selected.

### Third-party integrations (soft-depend)

- **WorldEdit/FAWE** (hard requirement - plugin disables itself if absent)
- **PlaceholderAPI**: `SkyblockExpansion` registers the `starmskyblock` placeholder identifier
- **TrMenu**: `StarMSkyblockHook` registers the `StarMSkyblockAPI` binding into TrMenu's `JavaScriptAgent`; bundled menus are extracted to `plugins/TrMenu/menus/skyblockmenu` on first run. Hooked both eagerly and via a `PluginEnableEvent` late-load listener; `hookTrMenu()` is idempotent and catches `Throwable` to tolerate TrMenu class changes.
- **Vault**: economy for upgrades (see Vault economy section)
- **AuraSkills / mcMMO**: skill-plugin source for island level contribution (see Level system). Either, both optional; selected via `level.yml` `type`. Compile-only via the `libs/` jars.
