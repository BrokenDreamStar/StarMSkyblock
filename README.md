# StarMSkyblock

一个功能完善的 Minecraft Spigot/Paper 空岛插件，为玩家提供完整的空岛游戏体验。支持多世界（主世界/下界/末地）、精细的权限管理、可配置的岛屿设置、PlaceholderAPI
集成和 TrMenu 菜单桥接。

## ToDo

### 岛屿

- [x] 岛屿网格系统
- [x] 岛屿模板生成 (使用WE/FAWE粘贴结构文件实现)

### 保护

- [x] 岛屿权限系统
- [x] 岛屿设置
- [x] 非岛屿区域的保护
- [x] 公共区域/未解锁区域权限与设置独立配置

### 传送

- [x] 末地传送门
- [x] 下界传送门（相对岛屿中心偏移缩放，三级边界检查，下界解锁机制）

### 刷石机

- [x] 刷石机矿物生成
- [x] 主世界Y0下替换为深板岩变种

### 升级

- [x] 升级岛屿刷石机
- [x] 升级岛屿大小

### 岛屿等级

- [x] 岛屿方块扫描
- [x] 等级计算
- [x] AuraSkills / mcMMO 成员技能加成（PowerLevel 总和按比例转换为额外等级）
- [x] 等级计算冷却可配置

### 任务

- [x] 任务系统
- [ ] 任务 (设计中)

### 菜单

- [x] 菜单 (目前使用TrMenu+PAPI实现 存在诸多问题)
- [ ] 原生GUI

---

## 信息

| 项目          | 内容                                            |
|-------------|-----------------------------------------------|
| **API 版本**  | 26.1.2 (Paper 26.1.2)                         |
| **Java 版本** | 25 (工具链: 25，字节码目标 Java 25)                    |
| **加载时机**    | POSTWORLD                                     |
| **软依赖**     | PlaceholderAPI, WorldEdit/FAWE, Multiverse-Core, TrMenu, Vault, AuraSkills, mcMMO |
| **构建工具**    | Gradle (Shadow JAR)                           |
| **输出文件**    | `build/libs/StarMSkyblock-1.0.0.jar`                |

## 快速开始

### 构建

```bash
./gradlew build
```

产物位于 `build/libs/StarMSkyblock-1.0.0.jar`，是一个包含所有依赖的 fat JAR。

### 安装

1. 将 `StarMSkyblock-1.0.0.jar` 放入服务器的 `plugins/` 目录
2. 确保已安装 **WorldEdit** 或 **FastAsyncWorldEdit**（必需）
3. （可选）安装 **PlaceholderAPI** 以使用变量占位符
4. （可选）安装 **TrMenu** 以使用菜单桥接功能
5. （可选）安装 **Vault** 以使用经济升级功能
6. （可选）安装 **AuraSkills** 或 **mcMMO** 以使用技能等级加成功能（二选一，由 `level.yml` 的 `skill-contribution.type` 选择）
7. 重启服务器

### 配置文件

插件会在 `plugins/StarMSkyblock/` 下生成以下文件：

| 文件                 | 说明                                                               |
|--------------------|------------------------------------------------------------------|
| `config.yml`       | 核心配置：世界名称、岛屿半径/间距/最大半径、结构文件与传送偏移、生物群系、`worldedit-mode`、`obsidian-to-lava`、`teleport-countdown`、`default-island-command`、`level-cooldown`、`show-border-default`、`set-respawn-on-join`/`fallback-spawn`、`public-worlds`、`sign`、`locale` |
| `permissions.yml`  | 权限组定义：MEMBER / MOD / ADMIN / COOP / VISITOR 每个角色的权限集（支持继承），并含 `public-area`（无主公共区域）与 `locked-area`（岛屿未解锁区域）两节的默认权限/设置 |
| `settings.yml`     | 岛屿默认设置：PVP、传送、生物生成、火势蔓延、爆炸破坏等开关                                  |
| `generator.yml`    | 岛屿刷石机配置：等级阈值、各维度概率权重表、深板岩替换开关、启用/禁用控制                            |
| `upgrades.yml`     | 岛屿升级配置：范围升级与刷石机升级的等级目标及费用                                        |
| `level.yml`        | 等级系统配置（合并自原 `block-values.yml` + `experience.yml` + `auraskills-contribution.yml`）：方块经验值、模板基线、数量阈值、超额递减、等级公式、技能加成 |
| `tasks/tasks.yml`  | 任务章节注册文件                                                         |
| `tasks/<Chapter>/` | 章节任务定义文件（`.yml`），如 `Chapter1/Mission1_1.yml`                     |
| `messages/zh_CN.yml` | i18n 消息文件：所有用户可见文本以消息键（dotted key）定义，通过 `config.yml` 的 `locale` 选择激活语言（见 [i18n 系统](#i18n-系统)） |

## 命令

### `/is` — 玩家空岛命令（权限: `skyblock.is`，默认所有玩家可用）

| 子命令                                         | 说明                            |
|---------------------------------------------|-------------------------------|
| `create [type] [name]`                      | 创建空岛，可指定结构类型和岛屿名称             |
| `spawn [confirm]`                           | 传送回自己的岛屿（自定义传送点或默认出生点）        |
| `help`                                      | 显示帮助菜单                        |
| `info`                                      | 查看岛屿详细信息（名称、ID、等级、大小、成员、创建时间） |
| `tp <name> [id] [confirm]`                  | 传送到指定岛屿（重名时可用 ID 区分）          |
| `setspawn`                                  | 设置岛屿传送点（需要 `SET_SPAWN` 权限）    |
| `border <true/false/toggle>`                | 切换岛屿边界显示                      |
| `delete [confirm]`                          | 删除自己的岛屿（有次数限制）                |
| `rename <name>`                             | 重命名岛屿（有冷却限制）                  |
| `generator [dim] [ore] <true/false/toggle>` | 查看/管理岛屿刷石机各矿石生成开关             |
| `team list`                                 | 查看岛屿成员列表（含在线状态）               |
| `team invite <player>`                      | 邀请玩家加入岛屿                      |
| `team accept`                               | 接受岛屿邀请                        |
| `team decline`                              | 拒绝岛屿邀请                        |
| `team remove <player> [confirm]`            | 移除岛屿成员                        |
| `promote <player>`                          | 提升成员角色                        |
| `demote <player>`                           | 降级成员角色                        |
| `coops`                                     | 查看合作者列表                       |
| `mycoops`                                   | 查看自己加入的合作岛屿                   |
| `myperms`                                   | 查看自己在该岛屿的权限                   |
| `role`                                      | 查看自己在岛屿中的角色                   |
| `coop add <player>`                         | 添加合作者                         |
| `coop remove <player>`                      | 移除合作者                         |
| `list [next/prev/spawn]`                    | 分页浏览所有岛屿列表                    |
| `settings [setting] [true/false]`           | 查看或修改岛屿设置                     |
| `permission <perm> <level>`                 | 修改权限所需的最低角色等级                 |
| `setbiome <biome>`                          | 设置岛屿整体生物群系                    |
| `setchunkbiome <biome>`                     | 设置当前区块生物群系                    |
| `upgrade`                                   | 查看岛屿升级信息                      |
| `upgrade radius`                            | 升级岛屿范围（消耗 Vault 货币）           |
| `upgrade generator`                         | 升级刷石机等级（消耗 Vault 货币）          |
| `task list [章节]`                            | 查看任务列表及进度                     |
| `task info <章节> <任务>`                       | 查看任务详情                        |
| `task submit <章节> <任务>`                     | 提交物品完成 ITEM 类型任务              |
| `task claim <章节> <任务>`                      | 领取已完成任务的奖励                    |
| `level`                                     | 扫描全岛方块并计算岛屿等级与总经验值            |
| `portalinfo`                                | 查看当前位置所在的传送门信息（源/目标岛屿、目标位置）   |

`/is` 无参数时默认执行 `/is spawn`（可通过 `config.yml` 的 `default-island-command` 修改）。

所有命令支持 `-s` 静默标志以隐藏反馈消息（如 `/is spawn -s`）。

### `/isadmin` — 管理员命令（权限: `skyblock.admin`，默认 OP 可用）

| 子命令                                          | 说明             |
|----------------------------------------------|----------------|
| `setradius <islandId> <radius>`              | 修改指定岛屿的半径      |
| `setgenerator <islandId> <level>`            | 设置指定岛屿的刷石机等级   |
| `settask <player> <章节> <任务> complete\|reset` | 强制完成或重置指定玩家的任务 |

## 刷石机系统

当岩浆遇水生成圆石/石头（主世界、末地）或玄武岩（下界）时，按概率权重随机替换为其他矿石。概率基于岛屿的 `generator-level`（通过
`/isadmin setgenerator` 管理）。

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

| 等级 | 主世界产物                                                                          | 下界产物                                                                    | 末地产物            |
|----|--------------------------------------------------------------------------------|-------------------------------------------------------------------------|-----------------|
| 1  | 圆石 85%, 石头 10%, 煤矿 3%, 铜矿 2%                                                   | 玄武岩 100%                                                                | 末地石 100%        |
| 2  | 圆石 76%, 石头 10%, 煤矿 5%, 铜矿 4%, 铁矿 3%, 青金石 2%                                    | 玄武岩 80%, 下界岩 10%, 灵魂土 5%, 荧石 5%                                         | 末地石 100%        |
| 3  | 圆石 62%, 石头 10%, 煤矿 7%, 铜矿 6%, 铁矿 5%, 青金石 4%, 金矿 2%, 红石 3%, 钻石 0.7%, 绿宝石 0.3%   | 玄武岩 69%, 下界岩 10%, 灵魂土 5%, 荧石 5%, 下界金矿 5%, 下界石英 5%, 镶金黑石 0.8%, 远古残骸 0.2% | 末地石 90%, 沙子 10% |
| 4  | 圆石 51%, 石头 10%, 煤矿 7%, 铜矿 7%, 铁矿 7%, 青金石 6%, 金矿 5%, 红石 5%, 钻石 1.4%, 绿宝石 0.6%   | 玄武岩 64.5%, 下界岩 10%, 灵魂土 5%, 荧石 5%, 下界金矿 7%, 下界石英 7%, 镶金黑石 1%, 远古残骸 0.5% | 末地石 80%, 沙子 20% |
| 5  | 圆石 47%, 石头 10%, 煤矿 7%, 铜矿 7%, 铁矿 7%, 青金石 6%, 金矿 6%, 红石 6.5%, 钻石 2.3%, 绿宝石 1.2% | 玄武岩 60%, 下界岩 10%, 灵魂土 5%, 荧石 5%, 下界金矿 9%, 下界石英 9%, 镶金黑石 1%, 远古残骸 1%     | 末地石 70%, 沙子 30% |

## 升级系统

在安装 Vault 经济插件的前提下，岛屿成员可通过消耗货币升级岛屿功能。

### 命令

| 命令                      | 说明       |
|-------------------------|----------|
| `/is upgrade`           | 查看当前升级信息 |
| `/is upgrade radius`    | 升级岛屿范围   |
| `/is upgrade generator` | 升级刷石机等级  |

仅岛主可执行升级操作，权限位于 `MANAGEMENT` 类（`SET_GENERATOR` 级别）。

### 配置文件 (`upgrades.yml`)

```yaml
# 岛屿范围升级
island-radius-upgrades:
  1:
    radius: 10       # 目标半径（区块）
    money: 10000.0   # 费用
  2:
    radius: 15
    money: 20000.0

# 刷石机等级升级
generator-upgrades:
  1:
    generator-level: 2   # 目标等级
    money: 15000.0
  2:
    generator-level: 3
    money: 25000.0
  3:
    generator-level: 4
    money: 50000.0
  4:
    generator-level: 5
    money: 100000.0
```

升级采用阶梯配置，每个升级项包含目标值和费用。范围升级的目标半径不得超过 `config.yml` 中的 `island-max-radius`，刷石机等级不得超过
`generator.yml` 的最大等级。

## 等级系统

StarMSkyblock 拥有完整的岛屿等级系统，基于玩家在空岛上放置的方块的稀有度和数量自动计算等级，为玩家提供长期发展目标。

### 命令

| 命令          | 说明                                 |
|-------------|------------------------------------|
| `/is level` | 异步扫描全岛所有方块，计算总经验值并换算为岛屿等级，同时显示扫描结果 |

扫描包括岛屿半径内的**所有三个维度**（主世界 / 下界 / 末地）。扫描完成后会保存到数据库，PAPI 变量即时反映最新值。

### 配置文件 (`level.yml`)

`level.yml` 合并了原 `block-values.yml`（方块价值/阈值/递减）、`experience.yml`（等级公式）与 `auraskills-contribution.yml`（技能加成）三份配置：

```yaml
# 方块经验值
blocks:
  DIAMOND_BLOCK: 350
  EMERALD_BLOCK: 450
  IRON_BLOCK: 50
  STONE: 1
  OAK_LOG: 1
  # ... 所有支持方块的完整价值表（未列出者默认为 1）

# 模板基线（岛屿创建时扫描 schematic 方块的原始数量/经验值，计算时扣除）
baseline:
  enabled: true

# 方块数量上限（阈值）
limits:
  NETHERITE_BLOCK: 30000
  EMERALD_BLOCK: 30000
  DIAMOND_BLOCK: 50000
  # ... 超过阈值的超额部分按递减公式计算（未列出者无上限）

# 超额经验值递减配置
diminishing:
  enabled: true
  decay: 0.03         # 递减系数（越大下降越快）
  minimum: 1          # 单个方块的最低经验值

# 等级经验值消耗（幂函数增长）
level-cost:
  base: 50            # 升到 1 级所需基础经验值
  power: 1.2          # 指数系数控制每级增长幅度

# 技能加成（见下文"技能加成"小节）
skill-contribution:
  type: mcmmo         # 技能插件来源: auraskills | mcmmo
  enabled: true
  coefficient: 45.0
  max-bonus-level: 0
```

#### 方块经验值

每种方块独立配置经验值。稀有/高级方块（钻石块 350、信标 500、刷怪笼 500）经验值高；基础建材（圆石 1、石头 1）经验值低。未配置的方块默认经验值为 `1`。

#### 数量阈值与超额递减

如果某种方块（如圆石）大量堆积，超出 `limits` 阈值后超额部分会按递减公式处理：

```
value = baseValue / (1 + decay × i)    # i = 超额数量
```

即超额方块越多，单个方块的边际价值越低，但最终不低于 `minimum` 值。

#### 等级换算

等级使用幂函数公式计算：

```
cost(L) = round(base × L^power)
```

- `base`：基础经验值消耗（升 1 级的门槛）
- `power`：增长指数，控制每级所需的经验增量
- 总经验值 = 所有方块经验值（阈值内全额 + 超额递减部分） - 模板基线（创建时 schematic 方块的原始经验值）

#### 模板基线（Baseline）

岛屿创建时自动扫描并保存 schematic 中每种方块的数量和总经验值作为基线。等级计算时：

```
有效方块数 = 世界实际方块数 - 基线方块数
有效经验值 = max(0, 世界总经验 - 基线总经验)
```

这确保了玩家**只有真正新增的方块才会计入等级**，模板已有的方块不会被重复计算。

### 技能加成（可选）

如果服务器安装了技能插件（**AuraSkills** 或 **mcMMO**，二选一），等级计算结果中会额外添加基于岛屿成员技能的加成等级：

```
加成等级 = min(全体成员 PowerLevel 总和 / coefficient, max-bonus-level)
```

技能加成在 `level.yml` 的 `skill-contribution` 章节配置：

- **type**: 技能插件来源，`auraskills` 或 `mcmmo`（决定查询哪个插件的 PowerLevel）
- **enabled**: 是否启用技能加成
- **coefficient**: PowerLevel 转换系数（值越大加成越难获得）
- **max-bonus-level**: 最大加成等级上限（0 = 不限制）

两种集成返回相同的数据结构，`LevelManager` 对其一视同仁：

- **AuraSkills**：异步查询全体成员 PowerLevel 总和
- **mcMMO**：同步查询离线玩家 PowerLevel（mcMMO 原生支持离线查询）

等级扫描结果会分别显示方块等级和技能加成等级，以及每位成员的贡献明细。

### 等级计算冷却

`/is level` 命令有冷却时间，由 `config.yml` 中的 `level-cooldown` 控制（默认 300 秒），设为 0 可禁用冷却。

### 计算流程

```
/is level
  → 异步遍历岛屿半径内所有区块（含三个维度）
  → 逐方块统计种类和数量（计入递减）
  → 减去模板基线
  → 按 level-cost 公式换算方块等级
  -> [可选] 按配置的 type 获取全体成员技能 PowerLevel 总和（AuraSkills 异步 / mcMMO 同步）
  -> 计算技能加成等级（方块等级 + 加成 = 最终等级）
  → 结果持久化到数据库
  → 向玩家展示扫描摘要（含加成明细）
```

扫描结果包含：总经验值、岛屿等级（方块+加成）、方块等级、扫描方块总量、各维度统计、技能加成明细。

## 传送门系统

StarMSkyblock 完全接管了空岛世界内的下界传送门和末地传送门逻辑，确保跨维度传送始终落在岛屿范围内。

### 下界传送门

#### 坐标计算：相对岛屿中心偏移缩放

原版 Minecraft 使用绝对坐标缩放（主世界坐标 ÷8 → 下界，下界坐标 ×8 → 主世界）。由于空岛使用乌拉姆螺旋分布在远离世界原点 (
0,0) 的位置（例如岛屿 1 的中心区块在 (38, 0)），绝对坐标缩放会导致下界目标偏移到岛屿范围之外的虚空区域。

因此插件改用**相对岛屿中心的偏移缩放**：

```
主世界 → 下界:  下界目标 = 岛屿中心区块Block坐标 + (玩家坐标 - 岛屿中心区块Block坐标) / 8.0
下界 → 主世界:  主世界目标 = 岛屿中心区块Block坐标 + (玩家坐标 - 岛屿中心区块Block坐标) * 8.0
```

其中岛屿中心区块 Block 坐标为 `(centerChunkX * 16 + 8, centerChunkZ * 16 + 8)`，即中心区块的正中心点。三个维度的岛屿结构粘贴在
**相同的区块坐标**处，因此岛屿中心坐标在三世界通用。

这种设计保证了：

- 岛内任意位置的传送门目标始终落在岛屿范围内
- 岛内传送门之间的 1:8 相对空间关系完整保留（双维度机器的传送门链接不受影响）
- `event.getTo()`（原版计算的目标）会被完全覆盖，确保不会落入虚空

#### 传送流程

**主世界 → 下界**（`handleToNether`）：

1. 定位玩家所属岛屿（先按 owner/member 查找，再按区块位置查找 coops/访客）
2. 如果非成员且岛屿未解锁下界 → 取消传送，提示"该岛屿尚未解锁下界"
3. 如果是岛屿成员且**首次进入下界**（`firstNetherJoin` 标记）：
    - 取消传送门事件（`event.setCancelled(true)`）
    - 直接 `player.teleport()` 到岛屿下界出生点（中心区块 + 配置的传送偏移）
    - 清除首次进入标记，解锁岛屿下界
4. 非首次进入：计算目标位置 → 边界检查 → 设置传送目标 → 尝试解锁下界

**下界 → 主世界**（`handleFromNether`）：

1. 计算目标位置（偏移 ×8）→ 边界检查 → 设置传送目标
2. 无首次进入逻辑，直接按坐标换算

#### 边界检查

传送门目标位置经过**三级边界检查**（`checkIslandBounds`）：

| 检查结果 | 条件                                                             | 提示消息                    |
|------|----------------------------------------------------------------|-------------------------|
| 通过   | `isChunkWithinIsland` → true（目标在已解锁半径内）                        | 无，正常传送                  |
| 拦截   | `isChunkWithinMaxRange` → true 但 `isChunkWithinIsland` → false | `无法创建传送门：目标位置岛屿区域未解锁！`  |
| 拦截   | 两者均 false                                                      | `无法创建传送门：目标位置超出你的岛屿范围！` |

- `isChunkWithinIsland`：`abs(chunkX - centerChunkX) <= radius`，radius 为当前已解锁半径
- `isChunkWithinMaxRange`：`abs(chunkX - centerChunkX) <= maxRadius`，maxRadius 为服务器配置的最大可扩展半径

#### 非玩家实体传送门（`onEntityPortal`）

非玩家实体（生物、矿车、掉落物等）进入下界传送门时：

1. 按实体所在区块查找所属岛屿（`getIslandAt` → `getIslandAtMaxRange`）
2. 岛屿未找到 → 取消事件，阻止传送
3. 末地传送门 → 传送到岛屿出生点
4. 下界未解锁 → 阻止进入
5. 计算目标位置（同上偏移缩放）→ 边界检查
6. 超出范围 → 取消事件，并**通知岛屿所有在线成员**（包括岛主）实体试图越界传送

#### 下界解锁机制

- 岛屿首次有成员通过下界传送门进入下界时，`netherUnlocked` 标记置为 `true` 并持久化到数据库
- 下界未解锁时：非成员和实体均被阻止进入
- 解锁后：任何有传送门权限的玩家均可使用

### 末地传送门

末地传送门处理相对简单——直接传送到岛屿出生点，不进行坐标缩放：

**进入末地**（主世界/下界的 END_PORTAL）：

- 岛屿成员 → 传送到岛屿在末地的出生点（`getIslandLocation`）
- 非成员 → 传送到末地世界出生点附近 `(100, 50, 0)`

**离开末地**（末地的 END_PORTAL）：

- 岛屿成员 → 传送到岛屿在主世界的出生点
- 非成员 → 传送到主世界世界出生点

末地传送门事件会被取消（`event.setCancelled(true)`），改用 `player.teleport()` 直接传送。

#### Paper 兼容：末地返回主世界

Paper 服务端在末地维度**不会触发** `PlayerPortalEvent`（即使玩家站在末地传送门内）。因此插件额外监听
`EntityPortalEnterEvent`：

1. 检测玩家踏入末地世界中的 `END_PORTAL` 方块
2. 设置 2 秒冷却防止重复触发
3. 直接 `player.teleport()` 到目标位置
4. 设置传送门冷却 40 tick，并在下一 tick 二次确认传送成功

## 任务系统

玩家可通过完成任务获取奖励。任务按章节组织，每个任务由 YAML 配置文件定义。

### 命令

| 命令                          | 说明                       |
|-----------------------------|--------------------------|
| `/is task list [章节]`        | 列出所有任务或指定章节任务，显示状态与进度百分比 |
| `/is task info <章节> <任务>`   | 查看任务详情：名称、类型、需求、前置、奖励    |
| `/is task submit <章节> <任务>` | 提交背包物品（仅 `ITEM` 类型任务）    |
| `/is task claim <章节> <任务>`  | 领取已完成的奖励                 |

### 任务类型

| 类型            | 说明           | 自动跟踪                  |
|---------------|--------------|-----------------------|
| `BLOCK_BREAK` | 破坏方块         | ✅ 事件监听                |
| `BLOCK_PLACE` | 放置方块         | ✅ 事件监听                |
| `ITEM`        | 提交物品（手动）     | ❌ 需 `/is task submit` |
| `ENTITY_KILL` | 击杀实体         | ✅ 事件监听                |
| `FARMING`     | 种植/收获农作物     | ✅ 事件监听                |
| `FISHING`     | 钓鱼           | ✅ 事件监听                |
| `CRAFTING`    | 合成物品         | ✅ 事件监听                |
| `EARN_MONEY`  | 赚钱（Vault 经济） | ✅ 定时检查                |

### 任务状态

| 状态   | 说明          | 图标 |
|------|-------------|----|
| 章节锁定 | 前置章节任务未全部完成 | 🔒 |
| 任务锁定 | 前置任务未完成     | 🔒 |
| 进行中  | 前置已完成，进度未满  | ✘  |
| 可领取  | 进度已满，奖励待领取  | ⚠  |
| 已完成  | 奖励已领取       | ✔  |

### 配置文件格式

任务配置位于 `tasks/tasks.yml`，注册章节与任务列表：

```yaml
Chapters:
  1:
    directory: Chapter1
    name: "第一章 · 初入空岛"
    tasks:
      - Mission1_1
      - Mission1_2
      - Mission1_3
  2:
    directory: Chapter2
    name: "第二章 · 空岛发展"
    required: # 前置章节（需全部完成后方可解锁本章）
      - Chapter1
    tasks:
      - Mission2_1
```

- `directory` — 章节目录名，同时也是章节唯一标识
- `name` — 章节显示名称
- `required` — 可选，前置章节目录列表，所有前置章节任务全部领取奖励后本章解锁
- `tasks` — 该章节包含的任务文件名列表（不含 `.yml` 后缀）

每个任务文件（如 `tasks/Chapter1/Mission1_1.yml`）定义具体内容：

```yaml
name: '空岛 启动!'
description: '制造刷石机并刷取一些圆石'
task_type: BLOCK_BREAK
only-natural: true
request:
  1:
    types: [ COBBLESTONE ]
    amount: 64
reward:
  command:
    - 'server: give %player_name% iron_ingot 3'
```

- `task_type` — 任务类型
- `task_required` — 前置任务 ID 列表（支持字符串或列表）
- `only-natural` — 仅统计自然生成的方块（`BLOCK_BREAK` 专用）
- `request` — 需求组（同组 types 为"或"关系，不同组为"与"关系），每组可指定 `potion_type`（仅 `ITEM` 类型有效，匹配药水效果）
- `reward.command` — 奖励命令列表，支持 `server:`（控制台执行，默认）和 `player:`（玩家执行）前缀，变量 `%player_name%` 自动替换

### ITEM 类型与药水效果

`ITEM` 类型任务支持可选的 `potion_type` 字段，用于要求提交特定药水效果的物品（如要求提交"跳跃药水"而非任意药水）：

```yaml
name: '药水商人'
task_type: ITEM
request:
  1:
    types: [ POTION ]
    amount: 3
    potion_type: JUMP
```

## 权限系统

StarMSkyblock 拥有精细的权限体系，每种操作（如破坏方块、打开容器、使用工具）都是一个独立的权限点，每种权限可设置最低角色等级。

### 角色等级

| 角色      | 等级值 | 颜色 |
|---------|-----|----|
| OWNER   | 5   | 金色 |
| ADMIN   | 4   | 红色 |
| MOD     | 3   | 深绿 |
| MEMBER  | 2   | 绿色 |
| COOP    | 1   | 天蓝 |
| VISITOR | 0   | 白色 |

### 权限节点

权限分 12 大类，共 80 个权限点：

| 类别                   | 权限                       | 说明      |
|----------------------|--------------------------|---------|
| **MANAGEMENT**       | `RENAME_ISLAND`          | 修改岛屿名称  |
|                      | `EDIT_PERMISSIONS`       | 修改岛屿权限  |
|                      | `EDIT_SETTINGS`          | 修改岛屿设置  |
|                      | `INVITE_MEMBER`          | 邀请成员    |
|                      | `REMOVE_MEMBER`          | 移除成员    |
|                      | `SET_ROLE`               | 设置成员权限组 |
|                      | `INVITE_COOP`            | 邀请合作者   |
|                      | `REMOVE_COOP`            | 移除合作者   |
|                      | `SET_SPAWN`              | 设置传送点   |
|                      | `SET_BIOME`              | 设置生物群系  |
|                      | `SET_GENERATOR`          | 设置岛屿刷石机 |
| **ITEM_DROP_PICKUP** | `ITEM_DROP`              | 丢弃物品    |
|                      | `ITEM_PICKUP`            | 拾取物品    |
|                      | `EXP_PICKUP`             | 吸取经验球   |
| **BLOCK**            | `BREAK`                  | 破坏方块    |
|                      | `BUILD`                  | 建造/放置方块 |
| **WORKBLOCK**        | `CRAFTING_TABLE_USE`     | 使用工作台   |
|                      | `ENCHANTING_TABLE_USE`   | 使用附魔台   |
|                      | `BEACON_USE`             | 使用信标    |
|                      | `ANVIL_USE`              | 使用铁砧    |
|                      | `GRINDSTONE_USE`         | 使用砂轮    |
|                      | `CARTOGRAPHY_TABLE_USE`  | 使用制图台   |
|                      | `STONECUTTER_USE`        | 使用切石机   |
|                      | `LOOM_USE`               | 使用织布机   |
|                      | `SMITHING_TABLE_USE`     | 使用锻造台   |
|                      | `CAMPFIRE_USE`           | 使用营火    |
| **CONTAINER**        | `FURNACE_OPEN`           | 使用熔炉    |
|                      | `CHEST_OPEN`             | 打开箱子    |
|                      | `BARREL_OPEN`            | 打开木桶    |
|                      | `ENDER_CHEST_OPEN`       | 打开末影箱   |
|                      | `SHULKER_BOX_OPEN`       | 打开潜影盒   |
|                      | `HOPPER_OPEN`            | 打开漏斗    |
|                      | `DISPENSER_OPEN`         | 打开发射器   |
|                      | `DROPPER_OPEN`           | 打开投掷器   |
|                      | `CRAFTER_OPEN`           | 打开自动合成器 |
|                      | `BREWING_STAND_OPEN`     | 打开酿造台   |
|                      | `SHELF_USE`              | 使用展示架   |
|                      | `ITEM_FRAME_USE`         | 使用物品展示框 |
|                      | `JUKEBOX_USE`            | 使用唱片机   |
|                      | `LECTERN_USE`            | 使用讲台    |
|                      | `CHISELED_BOOKSHELF_USE` | 使用雕纹书架  |
|                      | `DECORATED_POT_USE`      | 使用陶罐    |
|                      | `COMPOSTER_USE`          | 使用堆肥桶   |
|                      | `FLOWER_POT_USE`         | 使用花盆    |
|                      | `ANIMAL_INVENTORY_OPEN`  | 打开生物背包  |
| **REDSTONE**         | `BUTTON_PRESS`           | 按按钮     |
|                      | `LEVER_USE`              | 拉拉杆     |
|                      | `REPEATER_USE`           | 切换红石中继器 |
|                      | `COMPARATOR_USE`         | 切换红石比较器 |
|                      | `DAYLIGHT_DETECTOR_USE`  | 切换阳光探测器 |
|                      | `PRESSURE_PLATE_TRIGGER` | 触发压力板   |
|                      | `TRIPWIRE_HOOK_TRIGGER`  | 触发绊线钩   |
|                      | `SCULK_SENSOR_TRIGGER`   | 触发幽匿感测体 |
|                      | `BELL_RING`              | 敲击钟     |
|                      | `NOTE_BLOCK_USE`         | 使用音符盒   |
| **DOOR**             | `DOOR_OPEN`              | 开关门     |
|                      | `FENCE_GATE_OPEN`        | 开关栅栏门   |
|                      | `TRAPDOOR_OPEN`          | 开关活板门   |
| **VEHICLE**          | `MINECART_DAMAGE`        | 破坏矿车    |
|                      | `MINECART_ENTER`         | 乘坐矿车    |
|                      | `MINECART_PLACE`         | 放置矿车    |
|                      | `BOAT_DAMAGE`            | 破坏船     |
|                      | `BOAT_ENTER`             | 乘坐船     |
|                      | `BOAT_PLACE`             | 放置船     |
| **TOOL**             | `BOW_USE`                | 使用弓/弩   |
|                      | `AXE_USE`                | 使用斧     |
|                      | `SHOVEL_USE`             | 使用锹     |
|                      | `HOE_USE`                | 使用锄     |
|                      | `BUCKET_USE`             | 使用桶     |
|                      | `GLASS_BOTTLE_USE`       | 使用玻璃瓶   |
|                      | `BOWL_USE`               | 使用碗     |
|                      | `FISHING_ROD_USE`        | 钓鱼      |
|                      | `FLINT_AND_STEEL_USE`    | 点火      |
|                      | `SHEARS_USE`             | 使用剪刀    |
|                      | `BRUSH_USE`              | 使用刷子    |
|                      | `LEASH_USE`              | 使用拴绳    |
| **ITEM**             | `FIREWORK_USE`           | 使用烟花    |
|                      | `NAME_TAG_USE`           | 使用命名牌   |
|                      | `POTION_THROW`           | 投掷药水    |
|                      | `WATER_BOTTLE_USE`       | 使用水瓶    |
|                      | `BONE_MEAL_USE`          | 使用骨粉    |
|                      | `DYE_USE`                | 使用染料    |
|                      | `INK_SAC_USE`            | 使用墨囊    |
|                      | `HONEYCOMB_USE`          | 涂蜡      |
|                      | `CHORUS_FRUIT_EAT`       | 食用紫颂果   |
|                      | `ENDER_PEARL_USE`        | 使用末影珍珠  |
|                      | `ENDER_EYE_USE`          | 使用末影之眼  |
|                      | `WIND_CHARGE_USE`        | 使用风弹    |
|                      | `SNOWBALL_THROW`         | 丢雪球     |
|                      | `EGG_THROW`              | 丢鸡蛋     |
| **ENTITY**           | `ANIMAL_FEED`            | 喂食动物    |
|                      | `ENTITY_RIDE`            | 骑乘生物    |
|                      | `ENTITY_EQUIP`           | 装备生物    |
|                      | `ANIMAL_DAMAGE`          | 攻击动物    |
|                      | `MONSTER_DAMAGE`         | 攻击怪物    |
|                      | `VILLAGER_DAMAGE`        | 攻击村民    |
|                      | `VILLAGER_TRADE`         | 村民交易    |
|                      | `BARTERING`              | 以物易物    |
|                      | `ALLAY_INTERACT`         | 与悦灵交互   |
|                      | `ARMOR_STAND_DAMAGE`     | 攻击盔甲架   |
|                      | `ARMOR_STAND_INTERACT`   | 与盔甲架交互  |
| **OTHER**            | `ENTER_NETHER_PORTAL`    | 进入下界传送门 |
|                      | `ENTER_END_PORTAL`       | 进入末地传送门 |
|                      | `SPAWN_EGG_USE`          | 使用刷怪蛋   |
|                      | `FARMLAND_TRAMPLE`       | 踩踏耕地    |
|                      | `TURTLE_EGG_TRAMPLE`     | 踩踏海龟蛋   |
|                      | `SWEET_BERRY_HARVEST`    | 采摘浆果    |
|                      | `CAKE_EAT`               | 食用蛋糕    |
|                      | `SIGN_EDIT`              | 编辑告示牌   |
|                      | `BED_USE`                | 睡觉      |
|                      | `RESPAWN_ANCHOR_USE`     | 使用重生锚   |
|                      | `END_CRYSTAL_DAMAGE`     | 破坏末地水晶  |
|                      | `RAID_TRIGGER`           | 触发袭击    |

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
| `%starmskyblock_level%`                                   | 玩家自己的岛屿等级（来自 `/is level` 扫描结果）                                    |
| `%starmskyblock_level_here%`                              | 当前位置所在岛屿的等级                                                       |
| `%starmskyblock_total_points%`                            | 玩家岛屿的总经验值（浮点数）                                                    |
| `%starmskyblock_total_points_here%`                       | 当前位置所在岛屿的总经验值                                                     |
| `%starmskyblock_experience%`                              | 玩家岛屿的有效经验值（总经验 - 基线经验，同 total_points）                             |
| `%starmskyblock_experience_here%`                         | 当前位置所在岛屿的有效经验值                                                    |
| `%starmskyblock_blocks_counted%`                          | 玩家岛屿扫描到的方块总数                                                      |
| `%starmskyblock_blocks_counted_here%`                     | 当前位置所在岛屿扫描到的方块总数                                                  |
| `%starmskyblock_generator_level%`                         | 玩家岛屿的刷石机等级                                                        |
| `%starmskyblock_generator_level_here%`                    | 当前岛屿的刷石机等级                                                        |
| `%starmskyblock_generator_<normal/nether/end>%`           | 玩家岛屿指定维度的刷石机概率表（含启停状态）                                            |
| `%starmskyblock_generator_<矿石名>%`                         | 玩家在当前维度下某矿石是否启用的刷石机开关状态（支持中文/英文矿石名）                               |
| `%starmskyblock_generator_level_<N>%`                     | 指定等级 N 的完整刷石机概率表                                                  |
| `%starmskyblock_generator_level_<N>_<dim>%`               | 指定等级 N 在某维度的刷石机概率表                                                |
| `%starmskyblock_generator_level_next%`                    | 下一等级的刷石机概率表（已是最高级时提示）                                             |
| `%starmskyblock_generator_level_next_<dim>%`              | 下一等级在某维度的刷石机概率表                                                   |
| `%starmskyblock_creationtime%`                            | 岛屿创建时间                                                            |
| `%starmskyblock_dimension%`                               | 玩家当前所在维度（主世界/下界/末地）                                               |
| `%starmskyblock_own_island%`                              | 玩家是否在自己岛屿上（true/false）                                            |
| `%starmskyblock_has_island%`                              | 玩家是否拥有岛屿（true/false）                                             |
| `%starmskyblock_island_list_<slot>_<field>%`              | 分页岛屿列表（slot: 0-27, field: name/id/owner/level/members/memberlist） |
| `%starmskyblock_permission_<perm>_level_weight%`          | 某权限的最低等级权值                                                        |
| `%starmskyblock_permission_<perm>_level_<role>%`          | 某角色是否拥有某权限                                                        |
| `%starmskyblock_haspermission_<perm>%`                    | 玩家是否拥有某权限（布尔值）                                                    |
| `%starmskyblock_islandsettings_<setting>%`                | 岛屿设置的开关状态（yes/no）                                                 |
| `%starmskyblock_upgrades_generator_next_level_money%`     | 刷石机下一级所需费用（已达最高级时提示）                                              |
| `%starmskyblock_upgrades_island_radius_next_level_money%` | 范围下一级所需费用（已达最高级时提示）                                               |
| `%starmskyblock_upgrades_generator_has_money%`            | 玩家是否有足够余额升级下一级刷石机（true/false）                                     |
| `%starmskyblock_upgrades_island_radius_has_money%`        | 玩家是否有足够余额升级下一级范围（true/false）                                      |

### 任务相关变量

任务占位符中 `<chapter>` 为章节编号、`<mission>` 为章节内任务编号（如 `1_1` 表示第一章第 1 个任务）：

| 变量                                                            | 说明                                  |
|---------------------------------------------------------------|-------------------------------------|
| `%starmskyblock_task_completed_count%`                        | 已完成的任务总数                            |
| `%starmskyblock_task_total_count%`                            | 任务总数                                |
| `%starmskyblock_task_<chapter>_<mission>_progress%`           | 任务进度百分比（0-100）                     |
| `%starmskyblock_task_<chapter>_progress%`                     | 章节整体进度百分比（0-100）                   |
| `%starmskyblock_task_<chapter>_<mission>_completed%`          | 任务是否已完成（true/false）                 |
| `%starmskyblock_task_<chapter>_completed%`                     | 章节是否全部完成（true/false）                |
| `%starmskyblock_task_<chapter>_<mission>_claimed%`            | 任务奖励是否已领取（true/false）              |
| `%starmskyblock_task_<chapter>_<mission>_count%`               | 任务累计完成次数                            |
| `%starmskyblock_task_<chapter>_<mission>_percentage_<key>%`   | 某需求项的进度百分比（key 为 request 中的 type）  |
| `%starmskyblock_task_<chapter>_<mission>_value_<key>%`        | 某需求项的当前数值（key 为 request 中的 type）    |

## TrMenu 集成

插件向 TrMenu 的 JavaScript 引擎注册了 `StarMSkyblockAPI` 对象，可在菜单配置中调用：

```yaml
material: 'source:JS:StarMSkyblockAPI.getPlayerHead(player.getName())'
```

可用方法：

- `getPlayerHead(playerName)` — 获取玩家头颅
- `getPlayerHead(player, value)` — 获取带指定纹理的玩家头颅
- `getHeadByTexture(base64)` — 通过 Base64 纹理值获取头颅

## i18n 系统

插件内置国际化（i18n）支持。所有用户可见文本以**消息键**（dotted key）形式定义在 `messages/<locale>.yml` 中，调用方通过 `MessageUtil.send(sender, "key", args)` 发送，运行时按 `{placeholder}` 占位符替换。

### 配置

`config.yml`：

```yaml
locale: 'zh_CN'   # 激活的语言代码，需对应 messages/<locale>.yml
```

### 消息文件

`messages/zh_CN.yml` 是内置默认语言文件，首启时自动释放到 `plugins/StarMSkyblock/messages/`。外部文件若存在则覆盖内置版本。占位符使用 `{name}` 形式：

```yaml
command:
  admin:
    reload-success: "配置重载完成，耗时 {elapsed}ms"
```

### 调用约定

- `MessageUtil.send(sender, "dotted.key")` - 无参消息
- `MessageUtil.send(sender, "dotted.key", Map.of("name", value))` - 带占位符消息
- `MessageUtil.format("key", args)` - 仅格式化不发送（用于 PAPI 变量等）
- 控制台输出使用 `MessageUtil.consolePrint / warn / error`，而非 Bukkit 原生 `getLogger()`

### 缺失键与回退

- 缺失的 locale 自动回退到 `zh_CN`；若 `zh_CN` 也缺失则禁用插件
- 缺失键返回键名原文，并按键去重告警一次（避免日志刷屏）
- `/isadmin reload` 会同时重载 `LanguageManager`，新增消息键后需重载生效

### 颜色标签

`MessageUtil` 支持自定义标签格式（由 `color/ColorUtils` 解析）：`<gradient:#from-#to>text</gradient>`、`<rainbow:saturation:brightness>text</rainbow>`、`<transition:#from-#to>text</transition>`，以及 `&` 旧式颜色码和 `&#RRGGBB` 十六进制色。需要客户端翻译（如方块名的 `TranslatableComponent`）时使用 `MessageUtil.sendMessage(Component)`。

## 架构概览

### 核心模块

```
StarMSkyblock
├── StarMSkyblock.java          — 主入口，单例，初始化所有子系统
├── command/                    — 命令分发与子命令模式
│   ├── IslandCommand.java      — /is 命令分发器
│   ├── AdminCommand.java       — /isadmin 命令分发器
│   ├── IslandPermissionCommand.java  — /is permission 子命令
│   └── subcommand/             — 具体子命令实现（含帮助、队伍、任务、等级等）
├── config/                     - YAML 配置管理
│   ├── ConfigManager.java          - config.yml（含 sign/public-worlds/locale/worldedit-mode 等）
│   ├── ConfigKeys.java             - 配置键常量集中定义
│   ├── SchematicConfig.java        - 结构文件与传送偏移配置
│   ├── PermissionConfigManager.java - permissions.yml（角色权限组 + public-area + locked-area）
│   ├── SettingsConfigManager.java   - settings.yml
│   ├── GeneratorConfigManager.java  - generator.yml（刷石机等级与概率权重）
│   ├── UpgradeConfigManager.java    - upgrades.yml（升级等级与费用）
│   ├── ExperienceConfig.java        - level.yml 方块经验值/阈值/等级公式
│   ├── AuraSkillsContributionConfig.java - level.yml 技能加成（type 选择 auraskills|mcmmo）
│   ├── PublicAreaConfigManager.java      - permissions.yml 的 public-area 章节（无主公共区域）
│   └── LockedAreaConfigManager.java      - permissions.yml 的 locked-area 章节（未解锁区域）
├── database/                   — SQLite 持久层
│   ├── SQLiteManager.java      — 连接管理与建表
│   ├── IslandRepository.java   — 岛屿 CRUD
│   └── PlayerRepository.java   — 玩家数据
├── generator/                  — 世界生成与结构
│   ├── VoidChunkGenerator.java — 虚空区块生成器
│   ├── GeneratorType.java      — 玄武岩生成器类型检测
│   └── SchematicManager.java   — WorldEdit/FAWE 结构加载与粘贴
├── grid/                       — 空间索引
│   └── GridManager.java        — 乌拉姆螺旋算法分配岛屿坐标
├── island/                     - 岛屿领域模型
│   ├── Island.java             - 岛屿实体（含权限/设置/刷石机检查）
│   ├── IslandManager.java      - 岛屿业务逻辑与多重索引
│   ├── IslandCreateTask.java   - 异步岛屿创建
│   ├── IslandDeleteTask.java   - 异步岛屿删除
│   ├── InvitationManager.java  - 邀请管理（含 5 分钟过期）
│   ├── IslandSerializer.java   - JSON 序列化
│   ├── HomeLocation.java       - 自定义传送点序列化载体（Gson）
│   └── GridKeys.java           - 区块键编码工具
├── level/                      — 等级系统
│   ├── LevelManager.java       — 等级核心管理器（异步扫描+计算调度）
│   ├── IslandLevelCalculator.java — 方块扫描与经验值计算逻辑
│   └── LevelResults.java       — 扫描结果数据模型
├── task/                       — 任务系统
│   ├── TaskType.java           — 任务类型枚举
│   ├── TaskDefinition.java     — 任务定义（需求组、前置、奖励）
│   ├── TaskCategory.java       — 章节定义
│   ├── TaskProgress.java       — 玩家进度（JSON 持久化）
│   ├── TaskManager.java        — 核心管理器（进度跟踪、监听器注册、自动保存）
│   ├── config/
│   │   └── TaskConfigScanner.java — 扫描 tasks/ 目录加载任务配置
│   ├── command/
│   │   └── TaskCommand.java    — /is task 子命令
│   ├── placeholder/
│   │   └── TaskPlaceholderHandler.java - 任务相关 PAPI 变量
│   ├── listener/
│   │   ├── BaseTaskListener.java       — 监听器抽象基类
│   │   ├── BlockBreakTaskListener.java — 破坏方块跟踪
│   │   ├── BlockPlaceTaskListener.java — 放置方块跟踪
│   │   ├── EntityKillTaskListener.java — 击杀实体跟踪
│   │   ├── FarmingTaskListener.java    — 种植/收获跟踪
│   │   ├── FishingTaskListener.java    — 钓鱼跟踪
│   │   ├── CraftingTaskListener.java   — 合成跟踪
│   │   └── MoneyTaskListener.java      — 赚钱跟踪
│   └── reward/
│       └── TaskReward.java     — 奖励命令模型
├── listener/                   — Bukkit 事件监听
│   ├── BorderListener.java     — 动态世界边界
│   ├── CobblestoneGeneratorListener.java — 刷石机矿石替换（权重随机、深板岩、启用/禁用）
│   ├── ObsidianToLavaListener.java       — 黑曜石→熔岩转化（支持公共/锁定区域权限检查）
│   ├── PortalListener.java     — 下界/末地传送门处理
│   ├── TeleportCountdownListener.java — 传送倒计时
│   ├── EndProtectionListener.java     — 末地龙/水晶防护
│   ├── BlockPlaceListener.java        — 方块放置限制
│   ├── IslandBoundaryListener.java    — 岛屿边界保护（阻止水流/活塞/枝叶生长越界）
│   └── RespawnListener.java           — 重生点管理
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
├── placeholder/                - PlaceholderAPI 集成
│   ├── SkyblockExpansion.java  - PAPI 扩展主类（精确派发表 + 前缀回退链）
│   └── handler/                - 变量处理器
│       ├── IslandListHandler.java  - 岛屿列表变量
│       ├── PermissionHandler.java  - 权限变量
│       └── SettingsHandler.java   - 设置变量
├── bridge/                     — 第三方桥接
│   └── StarMSkyblockHook.java  — TrMenu JS 桥接
├── integration/                - 第三方技能集成
│   ├── AuraSkillsIntegration.java    - AuraSkills API 封装（异步 PowerLevel 汇总）
│   ├── McMMOIntegration.java         - mcMMO API 封装（同步 PowerLevel 汇总，含离线玩家）
│   ├── AuraSkillsIslandResult.java   - 加成计算结果（两种集成共用）
│   └── MemberSkillData.java          - 成员技能数据记录
├── message/                    - i18n 与消息/颜色工具
│   ├── LanguageManager.java    - i18n 语言管理（locale 加载、键值扁平化、缺失键回退）
│   ├── MessageUtil.java        - 消息发送/广播/日志（send/format/consolePrint）
│   ├── NameTranslator.java     - 名称翻译
│   ├── color/                  - 渐变/彩虹/过渡色解析（ColorUtils 等）
│   └── tag/                    - 标签内容抽取（TagContentExtractor/TagContentInfo）
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
- **刷石机权重随机**: `CobblestoneGeneratorListener` 按 `generator.yml`
  的权重表随机选择生成物，支持按岛屿的禁用列表排除特定矿石，Y < 0 时自动替换为深层变种
- **反射兼容**: FAWE API、GameProfile、末地龙禁用等均使用反射以跨版本兼容
- **继承式权限配置**: YAML 中角色可继承父角色权限，支持 additional/excluded 的差量配置
- **三级区域保护**: 权限/设置系统支持三种区域的独立配置：已解锁岛屿区域（使用岛屿自身权限/设置）、未解锁锁定区域（`permissions.yml` 的 `locked-area` 章节）、无主公共区域（`permissions.yml` 的 `public-area` 章节）
- **岛屿边界保护**: `IslandBoundaryListener` 阻止水流、活塞推动、树木生长等连锁环境变化扩散到岛屿解锁区域之外
- **技能等级加成**: 可选集成（AuraSkills 或 mcMMO，由 `level.yml` 的 `type` 选择），根据岛屿全体成员的技能 PowerLevel 总和按系数转换为额外岛屿等级

### 数据库表

| 表名               | 说明                                                    |
|------------------|-------------------------------------------------------|
| `islands`        | 岛屿数据（ID、名称、所属者、坐标、半径、设置、权限、刷石机禁用列表、等级、经验值、方块计数、模板基线、技能加成等） |
| `island_members` | 成员关联（UUID、角色、加入时间）                                    |
| `island_coops`   | 合作者关联                                                 |
| `players`        | 玩家数据（边界显示偏好、首次进入下界标志、任务进度 JSON）                         |
| `player_stats`   | 玩家统计（岛屿删除次数）                                          |
| `skin_textures`  | 皮肤纹理缓存                                                |

## 贡献

作者: BrokenDream_Star, DeepSeek, Gemini, Grok  
开发工具: Claude Code, OpenCode, IntelliJ IDEA  
网站: https://starm.team/
