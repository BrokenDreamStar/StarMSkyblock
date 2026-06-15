# TODO

## 岛屿等级计算：Baseline 方块被替换后的计数问题

### 问题描述

玩家拆除岛屿模板（schematic）方块后，在原地或其他位置放置同种方块，该方块**不会计入岛屿等级计算**。

**原因**: `baselineBlockCounts` 在岛屿创建时固定（扫描 schematic 中每种方块的数量），等级计算时用 `worldCount - baselineCount` 作为有效数量。模板方块被拆除后被玩家方块替换，worldCount 不变，baselineCount 不变，玩家的替换视为 0 贡献。

### 场景演算

```
岛屿创建 → baseline[STONE] = 1000, 世界中有 1000 石头
玩家破坏 10 个模板石头 → 世界中 990 石头
玩家放置 10 个新石头 → 世界中 1000 石头
等级计算: 1000 - 1000 = 0  ← 玩家的 10 个方块被完全忽略
```

### 推荐方案：跟踪模板方块移除（方案A）

利用 `BlockBreakTaskListener` 已有的 player-placed 追踪 Map，判断被破坏方块是否模板原生方块。

| 文件 | 改动 |
|------|------|
| `Island.java` | 新增 `Map<String, Long> baselineRemovals` + 读写方法 |
| `IslandRepository.java` | 新增 `updateBaselineRemovals()` 和 DB 列 `baseline_removals TEXT` |
| `SQLiteManager.java` | migration: `ALTER TABLE islands ADD COLUMN baseline_removals` |
| `IslandLevelCalculator.java` | `finishPhase()`: `effectiveBaseline = max(0, baselineCount - removals)` |
| 新建 Listener 或扩展现有 | `BlockBreakEvent` → 非 player-placed 且属 baseline 材料 → 递增 removals |

**正确性演算**：
```
baseline=1000, removals={}, world=1000 → adjusted=0
破坏10模板(not player-placed) → removals={stone:10}, effectiveBaseline=990
  adjusted = max(0, 990-990) = 0 ✓
放置10新(player-placed记录) → removals={stone:10}, effectiveBaseline=990
  adjusted = max(0, 1000-990) = 10 ✓
```

**重启安全**: `baselineRemovals` 持久化到 DB，重启后数据仍在。

### 备选方案B：每破坏都递减 Baseline

不区分是否 player-placed，任何同种方块被破坏就递减 baseline。

- 优点：实现极简
- 缺点：玩家放置方块 → 再破坏(自己放置的)会产生 phantom credit（虚高经验值）

### 思考：是否真的需要解决

- 该问题影响所有材料的基线扣减，在长期运营中会累积
- 但对于多数玩家来说，替换模板方块是常规操作
- 如果等级系统主要激励"新增方块"而非"替换方块"，当前行为是可接受的
- **决定**: 暂缓实现，观察玩家反馈后再决定优先级