# 统一代码风格并补全项目代码注释

## 目标
1. 给约 **58 个零注释文件**补全中文 Javadoc / 行内注释，注释密度**对齐现有优秀文件**（`BasePermissionManager`、`GridKeys`、`PermissionCheckResult`、`IslandPermissionLevel`）。
2. 把代码体内出现的**内联全限定类名改为 import 引用**（约 57 个文件），如 `team.starm.starmskyblock.StarMSkyblock` → import + `StarMSkyblock`；`org.bukkit.event.Listener` → import + `Listener`。

**不做**：import 分组顺序重排、`var` 用法统一、业务逻辑/行为变更。

## 范围

### IN
- **补注释（0% 文件，按包统计）**：`command/subcommand`(26) · `task/` 含 listener/command/config/reward(≈13) · `listener`(5: EndProtection/BlockPlace/TeleportCountdown/CobblestoneGenerator/ObsidianToLava) · `placeholder/handler`(3) · `util/reflection`(3) · `config`(2: Upgrade/Generator) · `command`(2: AdminCommand/IslandCommand)
- **内联 FQN → import（57 文件）**：代码体内全限定类型 → import + 简名。重点样本：`StarMSkyblock.java`(8 处，含匿名 `Listener` 类、`@EventHandler`、`net.milkbowl.vault.economy.Economy`、`team.starm...IslandCommand`)、`SubCommand.java`(3)、`task/TaskManager.java`(3)、各 `command/subcommand/*`(各 1)

### OUT（冻结，一律不动）
- `database/SQLiteManager.java`、`database/IslandRepository.java`、`database/PlayerRepository.java`、`level/LevelManager.java` —— 按既有约束 DB 代码冻结。已确认这 4 个文件均无内联 FQN，跳过无影响。
- 其余已有注释的文件：仅在"含内联 FQN"时做 FQN 改 import，**不重写其注释、不改 import 顺序、不动逻辑**。

## 注释风格规范（对齐现有优秀文件）
- 语言：**中文**，与现有代码一致。
- **类级 Javadoc**：`/** 类名一句话 + <p> 职责/协作说明，必要时 <ul><li> */`，贴在 `class/interface/enum` 声明上一行。
- **公开方法 Javadoc**：一句话 + 必要 `<p>` 展开 + `@param`/`@return`。
- **关键私有方法、非显而易见字段**：`/** */` 或行内 `//`。
- **跳过**平凡 getter / 仅赋值的构造器（与 `StarMSkyblock`、`BasePermissionManager` 一致——它们的 getter 无 Javadoc）。
- 区域分隔符 `// ========== XXX ==========` 仅在字段分组时使用，沿用 `StarMSkyblock` 既有写法。
- 行内 `//` 只解释"为什么"，不复述代码本身。
- 参考 exemplar：`permission/BasePermissionManager.java`、`island/GridKeys.java`、`permission/PermissionCheckResult.java`、`permission/IslandPermissionLevel.java`、`permission/manager/BuildPermissionManager.java`。

## 风格规范：内联 FQN → import
- 代码体内全限定类型 → import + 简名。
- 已 import 的类型不重复添加；新增 import 追加到现有 import 区末尾，**不重排已有 import 顺序**。
- `StarMSkyblock.java` 注意点：匿名 `new org.bukkit.event.Listener(){...}`、`@org.bukkit.event.EventHandler`、`org.bukkit.event.player.PlayerJoinEvent`、`org.bukkit.event.server.PluginEnableEvent`、`org.bukkit.event.HandlerList`、`net.milkbowl.vault.economy.Economy`、`team.starm.starmskyblock.command.IslandCommand` 全部改 import。
- **编辑含中文标点行的注意**：Edit 的 `old_string` 若包含中文破折号 `—`/全角括号 `（）` 会匹配失败。改 import 时锚点用纯代码行；若必须触及"代码 + 中文注释"同行，改用 `Write` 整文件或代码锚点。补类级 Javadoc 时以 `public class X` 代码行作锚点（无中文，安全）。

## 执行方式
按包分簇，并行子 agent 各自认领一簇；**簇内同时做补注释 + FQN 改 import**，保证一个文件只被一个 agent 改动（无冲突）。每个 agent 严格遵循上述规范与 exemplar。

簇划分（暂定 7 个 agent）：
- **A1**：`command/subcommand/` 前半（SubCommand 基类 + 约 13 命令）
- **A2**：`command/subcommand/` 后半（约 12 命令）
- **B**：`task/` 全部子包（含 listener/command/config/reward，跳过任何冻结项——本包无冻结）
- **C**：`listener/` + `world/` + `generator/`
- **D**：`placeholder/` + `config/`
- **E**：`message/` + `util/` + `tag/` + `bridge/` + `integration/`
- **F**：`permission/` + `setting/` + `island/` + `grid/` + `StarMSkyblock.java` + `level/`(仅 IslandLevelCalculator、LevelResults；跳过冻结的 LevelManager)

全部完成后，我亲自执行 `./gradlew build` 验证编译；任何编译错误由我直接修复（通常是漏 import / import 冲突）。最后抽查若干文件确认风格一致、无逻辑改动。

## 验证
- `./gradlew build` 编译通过（本项目无单元测试，编译即主要校验）。
- 抽查：注释风格与 exemplar 一致；import 仅新增不重排；业务逻辑零改动（git diff 应只见注释与 import/类型名变化）。
