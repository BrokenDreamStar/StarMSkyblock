# StarMSkyblock

一个功能完善的 Minecraft Spigot/Paper 空岛插件，为玩家提供完整的空岛游戏体验。支持多世界（主世界/下界/末地）、精细的权限管理、可配置的岛屿设置、PlaceholderAPI
集成和 TrMenu 菜单桥接。

## 信息

| 项目          | 内容                                     |
|-------------|----------------------------------------|
| **API 版本**  | 26.1.2 (Paper 1.26.x)                  |
| **Java 版本** | 21+ (工具链: 25)                          |
| **加载时机**    | POSTWORLD                              |
| **软依赖**     | PlaceholderAPI, WorldEdit/FAWE, TrMenu |
| **构建工具**    | Gradle (Shadow JAR)                    |
| **输出文件**    | `build/libs/StarMSkyblock.jar`         |

## 快速开始

### 构建

```bash
./gradlew build
```

产物位于 `build/libs/StarMSkyblock.jar`，是一个包含所有依赖的 fat JAR。

### 安装

1. 将 `StarMSkyblock.jar` 放入服务器的 `plugins/` 目录
2. 确保已安装 **WorldEdit** 或 **FastAsyncWorldEdit**（必需）
3. （可选）安装 **PlaceholderAPI** 以使用变量占位符
4. （可选）安装 **TrMenu** 以使用菜单桥接功能
5. 重启服务器

### 配置文件

插件会在 `plugins/StarMSkyblock/` 下生成以下文件：

| 文件                | 说明                                                                                     |
|-------------------|----------------------------------------------------------------------------------------|
| `config.yml`      | 核心配置：世界名称、岛屿半径/间距、结构文件路径、传送偏移、生物群系、功能开关、`default-island-command`                    |
| `permissions.yml` | 权限组定义：MEMBER / MOD / ADMIN / COOP / VISITOR 每个角色的权限集（支持继承）                             |
| `settings.yml`    | 岛屿默认设置：PVP、传送、生物生成、火势蔓延、爆炸破坏等开关                                                        |
| `generator.yml`   | 岛屿刷石机配置：等级阈值、各维度概率权重表、深板岩替换开关、启用/禁用控制                                               |
| `sign.yml`        | 岛屿出生点告示牌文本模板（支持 PAPI 变量）                                                               |
| `schematics/`     | 岛屿结构文件（`.schem`），内置 `default.schem` / `default_nether.schem` / `default_the_end.schem` |

## 命令

### `/is` — 玩家空岛命令（权限: `skyblock.is`，默认所有玩家可用）

| 子命令                                               | 说明                        |
|---------------------------------------------------|---------------------------|
| `create [type] [name]`                            | 创建空岛，可指定结构类型和岛屿名称         |
| `spawn [confirm]`                                 | 传送回自己的岛屿（自定义传送点或默认出生点）    |
| `help`                                            | 显示帮助菜单                    |
| `info`                                            | 查看岛屿详细信息（名称、ID、等级、大小、成员、创建时间） |
| `tp <name> [id] [confirm]`                        | 传送到指定岛屿（重名时可用 ID 区分）      |
| `setspawn`                                        | 设置岛屿传送点（需要 `SET_SPAWN` 权限） |
| `border <true/false/toggle>`                      | 切换岛屿边界显示                  |
| `delete [confirm]`                                | 删除自己的岛屿（有次数限制）            |
| `rename <name>`                                   | 重命名岛屿（有冷却限制）              |
| `generator [dim] [ore] <true/false/toggle>`       | 查看/管理岛屿刷石机各矿石生成开关         |
| `team list`                                       | 查看岛屿成员列表（含在线状态）           |
| `team invite <player>`                            | 邀请玩家加入岛屿                  |
| `team accept`                                     | 接受岛屿邀请                    |
| `team decline`                                    | 拒绝岛屿邀请                    |
| `team remove <player> [confirm]`                  | 移除岛屿成员                    |
| `promote <player>`                                | 提升成员角色                    |
| `demote <player>`                                 | 降级成员角色                    |
| `coops`                                           | 查看合作者列表                   |
| `mycoops`                                         | 查看自己加入的合作岛屿               |
| `myperms`                                         | 查看自己在该岛屿的权限               |
| `role`                                            | 查看自己在岛屿中的角色               |
| `coop add <player>`                               | 添加合作者                     |
| `coop remove <player>`                            | 移除合作者                     |
| `list [next/prev/spawn]`                          | 分页浏览所有岛屿列表                |
| `settings [setting] [true/false]`                 | 查看或修改岛屿设置                 |
| `permission <perm> <level>`                       | 修改权限所需的最低角色等级             |
| `setbiome <biome>`                                | 设置岛屿整体生物群系                |
| `setchunkbiome <biome>`                           | 设置当前区块生物群系                |

`/is` 无参数时默认执行 `/is spawn`（可通过 `config.yml` 的 `default-island-command` 修改）。

所有命令支持 `-s` 静默标志以隐藏反馈消息（如 `/is spawn -s`）。

### `/isadmin` — 管理员命令（权限: `skyblock.admin`，默认 OP 可用）

| 子命令                                    | 说明          |
|----------------------------------------|-------------|
| `setradius <islandId> <radius>`        | 修改指定岛屿的半径 |
| `setgenerator <islandId> <level>`      | 设置指定岛屿的刷石机等级 |

## 刷石机系统

当岩浆遇水生成圆石/石头（主世界、末地）或玄武岩（下界）时，按概率权重随机替换为其他矿石。概率基于岛屿的 `generator-level`（通过 `/isadmin setgenerator` 管理）。

### 配置文件 (`generator.yml`)

- **levels**: 按生成器等级门槛定义三个维度的替换表（normal / nether / end）
- **权重制**: 每个方块的数值代表相对权重，实际概率 = 该方块权重 / 总权重 × 100%
- **deepslate**: 主世界 Y < 0 时自动将圆石/石头/矿石替换为深层变种

### 玩家管理

`/is generator [dimension] [ore] <true/false/toggle>`

- 无参数：查看所有维度生成状态及概率
- 带维度：查看指定维度的矿石生成状态
- 带矿石和开关：启用/禁用特定矿石的生成（默认产物不可禁用）

需要 `SET_GENERATOR` 权限（MANAGEMENT 类）。

### 默认等级表

| 等级 | 主世界产物                        | 下界产物                                          | 末地产物        |
|----|-------------------------------|------------------------------------------------|-------------|
| 1  | 圆石 85%, 石头 10%, 煤矿 3%, 铜矿 2% | 玄武岩 100%                                     | 末地石 100%   |
| 2  | +铁矿 3%, 青金石 2%               | 下界岩 10%, 灵魂土 5%, 荧石 5%                      | 末地石 100%   |
| 3  | +金矿 2%, 红石 3%, 钻石 0.7%, 绿宝石 0.3% | +下界金矿 5%, 下界石英 5%                          | +沙子 10%    |
| 4  | 概率上调                                    | +镶金黑石 1%                                      | 沙子 20%     |
| 5  | 概率继续上调                                 | +远古残骸 0.5%                                    | 沙子 30%     |

## 权限系统

StarMSkyblock 拥有精细的权限体系，每种操作（如破坏方块、打开容器、使用工具）都是一个独立的权限点，每种权限可设置最低角色等级。

### 角色等级

| 角色      | 等级值 | 颜色 |
|---------|-----|----|
| OWNER   | 5   | 红色 |
| ADMIN   | 4   | 金色 |
| MOD     | 3   | 绿色 |
| MEMBER  | 2   | 白色 |
| COOP    | 1   | 灰色 |
| VISITOR | 0   | 深红 |

### 权限节点

权限分 12 大类，共 80+ 个权限点：

| 类别                   | 包含权限                                  |
|----------------------|---------------------------------------|
| **MANAGEMENT**       | 管理类：邀请/移除成员、设置角色/权限/传送点/生物群系/刷石机、重命名、删除岛屿 |
| **ITEM_DROP_PICKUP** | 物品丢弃、拾取、经验球吸取                         |
| **BLOCK**            | 方块破坏和建造                               |
| **WORKBLOCK**        | 工作台、附魔台、铁砧、锻造台、织布机、切石机等               |
| **CONTAINER**        | 箱子、熔炉、漏斗、发射器、潜影盒、末影箱、唱片机等             |
| **REDSTONE**         | 按钮、拉杆、压力板、红石中继器/比较器、钟、音符盒、幽匿感测体       |
| **DOOR**             | 门、栅栏门、活板门                             |
| **VEHICLE**          | 矿车和船（放置/乘坐/破坏）                        |
| **TOOL**             | 弓/弩、斧、锹、锄、桶、剪刀、刷子、拴绳、钓鱼竿              |
| **ITEM**             | 烟花、刷怪蛋、命名牌、药水、骨粉、末影珍珠、风弹等             |
| **ENTITY**           | 动物喂食/攻击、怪物攻击、村民交易、以物易物、骑乘、悦灵等         |
| **OTHER**            | 踩踏耕地/海龟蛋、采摘浆果、蛋糕、睡觉、告示牌编辑、袭击触发等       |

### 默认权限组配置文件 (`permissions.yml`)

支持继承关系，例如 `ADMIN` 继承 `MOD`，`MOD` 继承 `MEMBER`。每个组可设置 `additional`（额外权限）和 `excluded`（排除权限），
`OWNER` 角色默认拥有所有权限。

## 岛屿设置

每个岛屿可独立切换以下设置：

| 设置                     | 说明         | 默认值   |
|------------------------|------------|-------|
| `pvp`                  | 允许 PVP     | true  |
| `tp`                   | 允许其他玩家传送至此 | true  |
| `animal_spawn`         | 动物自然生成     | true  |
| `monster_spawn`        | 怪物自然生成     | true  |
| `phantom_spawn`        | 幻翼生成       | true  |
| `spawner_spawn`        | 刷怪笼生成生物    | true  |
| `fire_spread`          | 火势蔓延       | true  |
| `enderman_grief`       | 末影人搬运方块    | false |
| `ghast_fireball_grief` | 恶魂火球破坏     | false |
| `creeper_explosion`    | 苦力怕爆炸破坏    | false |
| `tnt_explosion`        | TNT 爆炸破坏   | true  |
| `wither_grief`         | 凋灵破坏方块     | false |

## PlaceholderAPI 变量

标识符: `starmskyblock`

| 变量                                                        | 说明                                                                |
|-----------------------------------------------------------|-------------------------------------------------------------------|
| `%starmskyblock_island_name%`                             | 玩家自己的岛屿名称                                                         |
| `%starmskyblock_island_name_here%`                        | 当前位置所在岛屿的名称                                                       |
| `%starmskyblock_role%`                                    | 玩家在岛屿中的角色                                                         |
| `%starmskyblock_role_here%`                               | 玩家在当前岛屿中的角色                                                       |
| `%starmskyblock_level%`                                   | 玩家自己的岛屿等级                                                         |
| `%starmskyblock_level_here%`                              | 当前岛屿的等级                                                           |
| `%starmskyblock_generator_level%`                         | 玩家岛屿的刷石机等级                                                        |
| `%starmskyblock_generator_level_here%`                    | 当前岛屿的刷石机等级                                                        |
| `%starmskyblock_generator_<normal/nether/end>%`           | 玩家岛屿指定维度的刷石机概率表（含启停状态）                                            |
| `%starmskyblock_generator_<矿石名>%`                       | 玩家在当前维度下某矿石是否启用的刷石机开关状态（支持中文/英文矿石名）                                |
| `%starmskyblock_generator_level_<N>%`                     | 指定等级 N 的完整刷石机概率表                                                    |
| `%starmskyblock_generator_level_<N>_<dim>%`               | 指定等级 N 在某维度的刷石机概率表                                                 |
| `%starmskyblock_generator_level_next%`                    | 下一等级的刷石机概率表（已是最高级时提示）                                             |
| `%starmskyblock_generator_level_next_<dim>%`              | 下一等级在某维度的刷石机概率表                                                    |
| `%starmskyblock_creationtime%`                            | 岛屿创建时间                                                            |
| `%starmskyblock_dimension%`                               | 玩家当前所在维度（主世界/下界/末地）                                              |
| `%starmskyblock_own_island%`                              | 玩家是否在自己岛屿上（true/false）                                              |
| `%starmskyblock_island_list_<slot>_<field>%`              | 分页岛屿列表（slot: 0-27, field: name/id/owner/level/members/memberlist） |
| `%starmskyblock_permission_<perm>_level_weight%`          | 某权限的最低等级权值                                                        |
| `%starmskyblock_permission_<perm>_level_<role>%`          | 某角色是否拥有某权限                                                        |
| `%starmskyblock_haspermission_<perm>%`                    | 玩家是否拥有某权限（布尔值）                                                    |
| `%starmskyblock_islandsettings_<setting>%`                | 岛屿设置的开关状态（yes/no）                                                 |

## TrMenu 集成

插件向 TrMenu 的 JavaScript 引擎注册了 `StarMSkyblockAPI` 对象，可在菜单配置中调用：

```yaml
material: 'source:JS:StarMSkyblockAPI.getPlayerHead(player.getName())'
```

可用方法：

- `getPlayerHead(playerName)` — 获取玩家头颅
- `getPlayerHead(player, value)` — 获取带指定纹理的玩家头颅
- `getHeadByTexture(base64)` — 通过 Base64 纹理值获取头颅

## 架构概览

### 核心模块

```
StarMSkyblock
├── StarMSkyblock.java          — 主入口，单例，初始化所有子系统
├── command/                    — 命令分发与子命令模式
│   ├── IslandCommand.java      — /is 命令分发器
│   ├── AdminCommand.java       — /isadmin 命令分发器
│   ├── IslandPermissionCommand.java  — /is permission 子命令
│   └── subcommand/             — 20 个具体子命令实现
├── config/                     — YAML 配置管理
│   ├── ConfigManager.java      — config.yml
│   ├── PermissionConfigManager.java — permissions.yml
│   ├── SettingsConfigManager.java   — settings.yml
│   ├── GeneratorConfigManager.java  — generator.yml（刷石机等级与概率权重）
│   └── SignConfigManager.java       — sign.yml
├── database/                   — SQLite 持久层
│   ├── SQLiteManager.java      — 连接管理与表迁移
│   ├── IslandRepository.java   — 岛屿 CRUD
│   └── PlayerRepository.java   — 玩家数据
├── generator/                  — 世界生成与结构
│   ├── VoidChunkGenerator.java — 虚空区块生成器
│   ├── GeneratorType.java      — 玄武岩生成器类型检测
│   └── SchematicManager.java   — WorldEdit/FAWE 结构加载与粘贴
├── grid/                       — 空间索引
│   └── GridManager.java        — 乌拉姆螺旋算法分配岛屿坐标
├── island/                     — 岛屿领域模型
│   ├── Island.java             — 岛屿实体（含权限/设置/刷石机检查）
│   ├── IslandManager.java      — 岛屿业务逻辑与多重索引
│   ├── IslandCreateTask.java   — 异步岛屿创建
│   ├── IslandDeleteTask.java   — 异步岛屿删除
│   ├── InvitationManager.java  — 邀请管理（含 5 分钟过期）
│   └── IslandSerializer.java   — JSON 序列化
├── listener/                   — Bukkit 事件监听
│   ├── BorderListener.java     — 动态世界边界
│   ├── CobblestoneGeneratorListener.java — 刷石机矿石替换（权重随机、深板岩、启用/禁用）
│   ├── ObsidianToLavaListener.java       — 黑曜石→熔岩转化
│   ├── PortalListener.java     — 下界/末地传送门处理
│   ├── TeleportCountdownListener.java — 传送倒计时
│   ├── EndProtectionListener.java     — 末地龙/水晶防护
│   └── BlockPlaceListener.java        — 方块放置限制
├── permission/                 — 权限子系统
│   ├── IslandPermission.java   — 权限枚举（80+）
│   ├── IslandPermissionLevel.java     — 角色等级枚举
│   ├── BasePermissionManager.java     — 权限检查抽象
│   ├── IslandPermissionManager.java   — 12 个子管理器注册
│   └── manager/                — 12 个域权限监听器
├── setting/                    — 岛屿设置子系统
│   ├── IslandSetting.java      — 设置枚举
│   ├── BaseSettingManager.java  — 设置检查抽象
│   ├── IslandSettingManager.java    — 6 个子管理器注册
│   └── manager/                — 6 个域设置监听器
├── world/                      — 世界管理
│   └── SkyblockWorldManager.java    — 三世界懒加载与管理
├── placeholder/                — PlaceholderAPI 集成
│   ├── SkyblockExpansion.java  — PAPI 扩展主类
│   ├── IslandListHandler.java  — 岛屿列表变量
│   ├── PermissionHandler.java  — 权限变量
│   └── SettingsHandler.java    — 设置变量
├── bridge/                     — 第三方桥接
│   └── StarMSkyblockHook.java  — TrMenu JS 桥接
├── message/                    — 消息与颜色工具
│   ├── MessageUtil.java        — 消息发送/广播/日志
│   └── color/                  — 渐变/彩虹/过渡色解析
├── tag/                        — 静态分类集合
│   ├── EntityTags.java         — 实体类型分类
│   └── ItemTags.java           — 物品材质分类
└── util/                       — 工具类
    ├── SkullManager.java       — 玩家头颅管理与纹理缓存
    ├── OreDisplayName.java     — 刷石机矿石中英文映射
    └── SkyblockBiome.java      — 生物群系枚举与中文名
```

### 关键设计

- **单例模式**: `StarMSkyblock.getInstance()` 提供全局访问
- **命令分发器模式**: `IslandCommand` 通过 `Map<String, SubCommand>` 路由子命令
- **组合模式**: 权限/设置系统分别由 12 和 6 个独立监听器组成，每个负责特定事件域
- **仓储模式**: `IslandRepository` / `PlayerRepository` 封装 SQLite 数据访问
- **双重索引**: `IslandManager` 维护多重 `ConcurrentHashMap` 实现 O(1) 查询（按 ID/所属者/区块坐标/成员/合作者）
- **乌拉姆螺旋**: `GridManager` 用 Ulam Spiral 算法将岛屿 ID 映射到唯一的区块坐标
- **两阶段异步任务**: 岛屿创建/删除分为 async（数据库/结构粘贴）和 sync（实体清理/传送）阶段
- **刷石机权重随机**: `CobblestoneGeneratorListener` 按 `generator.yml` 的权重表随机选择生成物，支持按岛屿的禁用列表排除特定矿石，Y < 0 时自动替换为深层变种
- **反射兼容**: FAWE API、GameProfile、末地龙禁用等均使用反射以跨版本兼容
- **继承式权限配置**: YAML 中角色可继承父角色权限，支持 additional/excluded 的差量配置

### 数据库表

| 表名               | 说明                           |
|------------------|------------------------------|
| `islands`        | 岛屿数据（ID、名称、所属者、坐标、半径、设置、权限、刷石机禁用列表等） |
| `island_members` | 成员关联（UUID、角色、加入时间）           |
| `island_coops`   | 合作者关联                        |
| `players`        | 玩家数据（边界偏好、首次进入下界标志）          |
| `player_stats`   | 玩家统计（岛屿删除次数）                 |
| `skin_textures`  | 皮肤纹理缓存                       |

## 贡献

作者: BrokenDream_Star, DeepSeek, Gemini, Grok  
网站: https://starm.team/
