# AGENTS.md — StarMSkyblock

## Language

- **始终使用中文回复。** 所有与用户的交流、代码注释、说明文档等均须使用中文。

## Build

- `./gradlew build` — 产出 `build/libs/StarMSkyblock.jar`（fat JAR，无版本后缀）
- 编译需要 `libs/TrMenu-3.12.2.jar` 存在（文件依赖，`compileOnly files(...)`）。克隆后若缺失，从项目某处复制或注释 TrMenu 相关代码再编译。
- Java 工具链 25，`-Xlint:all -Xlint:-classfile`，UTF-8 编译
- Gradle 配置缓存/并行/构建缓存均已开启（`gradle.properties`）
- 无测试，`./gradlew test` 无意义

## Runtime

- **目标服务端:** Spigot/Paper API `26.1.2-R0.1-SNAPSHOT`，`load: POSTWORLD`
- **软依赖:** `PlaceholderAPI`, `WorldEdit`（FAWE 也可）, `TrMenu`
- **必需:** WorldEdit 或 FAWE（没有则插件拒绝启动）
- **主类:** `team.starm.starmskyblock.StarMSkyblock`（单例 `getInstance()`）

## Key Compile-Only Dependencies

按 `build.gradle`，以下依赖均为 `compileOnly`（运行时由服务端提供）：
- `spigot-api:26.1.2-R0.1-SNAPSHOT`
- `adventure-api:4.19.0`, `adventure-text-serializer-legacy:4.19.0`
- `gson:2.11.0`, `sqlite-jdbc:3.46.1.0`, `authlib:6.0.54`
- `worldedit-bukkit:7.4.2`, `placeholderapi:2.11.6`
- 文件依赖 `libs/TrMenu-3.12.2.jar`

## Entry Point & Wiring

`StarMSkyblock.onEnable()` 按序调用：
1. `checkWorldEdit()` — 必须
2. `initConfigs()` — config.yml, permissions.yml, settings.yml, sign.yml, generator.yml
3. `extractSchematics()` — 内置 `.schem` 释放到 `<dataFolder>/schematics/`（不覆盖已有）
4. `initDatabase()` — SQLite
5. `initSchematicManager()` — WorldEdit/FAWE 结构操作
6. `initGridAndIslands()` — Ulam Spiral 网格 + IslandManager 双重索引
7. `initWorlds()` — 三世界懒加载（正常/下界/末地）
8. `initInvitations()` — 5 分钟过期清理
9. `initPermissions()` — 12 个域权限监听器注册
10. `registerIntegrations()` — PAPI 扩展 + TrMenu JS 桥接
11. `preWarmWorlds()` — 立即创建/加载三世界
12. `registerListeners()` — 8 个监听器（含 CobblestoneGenerator、ObsidianToLava 等条件注册）
13. `registerCommands()` — `/is` + `/isadmin`

## Code Conventions

- 包根 `team.starm.starmskyblock`
- 世界名判断使用 `SkyblockWorldManager.isSkyblockWorld(World)` / `isSkyblockWorldName(String)` / `isNormalWorld` / `isNetherWorld` / `isEndWorld`，不要直接比较字符串
- 消息发送用 `MessageUtil`（`team.starm.starmskyblock.message.MessageUtil`）
- 子命令模式: `IslandCommand` 通过 `Map<String, SubCommand>` 分发；每个子命令独立 class 在 `command/subcommand/`
- `/is permission` 单独委托给 `IslandPermissionCommand`
- 权限 12 域 + 设置 6 域，各自由独立 Manager 类处理对应 Bukkit 事件
- **数据库 schema：禁止迁移代码。** 所有列必须直接写在 `CREATE TABLE` 语句中。新增列时直接修改建表 DDL 即可，不需要也不允许写 `ALTER TABLE ADD COLUMN` 迁移逻辑。旧数据库文件删除重建。

## Git Workflow

- **每次任务完成立即提交 git。** 先 `git add -A` 暂存所有变更，再用 `git commit -m "type: 描述"` 提交。提交信息使用中文，遵循现有 commit history 的风格（如 `feat:` / `refactor:` / `docs:` / `fix:` 前缀）。

## README Reference

`README.md`（327 行）是详细文档源，涵盖：
- 所有命令及子命令列表
- PlaceholderAPI 全部变量（标识符 `starmskyblock`）
- 80+ 权限节点（12 大类）及角色等级（OWNER 5 → VISITOR 0）
- 12 项岛屿设置及其默认值
- 刷石机等级权重表与概率机制
- TrMenu JS 桥接 API
- SQLite 表结构
- 完整包结构树

遇到命令/权限/设置/PAPI 细节时，应查阅 `README.md` 而非自行推断。
