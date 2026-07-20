# Island Transfer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `/is transfer <player> [confirm]` command allowing island owners to transfer ownership to an existing member.

**Architecture:** Follows the existing SubCommand pattern with a confirm gate (matching DeleteCommand/TeamCommand). The Island model's `ownerId` moves from `final` to mutable, a transactional DB method handles ownership+membership swap, and IslandManager coordinates index updates.

**Tech Stack:** Java 25, Paper API 26.1.2, Adventure API (Kyori), SQLite

## Global Constraints

- All user-facing strings use i18n keys in `messages/zh_CN.yml`
- Mutation methods follow write-through: memory first, then DB
- Confirm pattern matches `DeleteCommand`: two-step with literal `confirm` argument
- Target must be an existing island member; old owner becomes MEMBER
- Commit messages follow `type: description` convention

---

### Task 1: Make Island.ownerId mutable

**Files:**
- Modify: `src/main/java/team/starm/starmskyblock/island/Island.java:48`

**Interfaces:**
- Produces: `Island.setOwnerId(UUID ownerId)` — allows owner change

- [ ] **Step 1: Change `final` to non-final and add setter**

Edit `src/main/java/team/starm/starmskyblock/island/Island.java`.

Line 48, change:
```java
private final UUID ownerId;
```
to:
```java
private UUID ownerId;
```

After the `getOwnerId()` getter (line 219-221), add the setter:
```java
public void setOwnerId(UUID ownerId) {
    this.ownerId = ownerId;
}
```

- [ ] **Step 2: Verify compilation**

```bash
./gradlew compileJava
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/team/starm/starmskyblock/island/Island.java
git commit -m "feat: make Island.ownerId mutable for transfer support"
```

---

### Task 2: Add transferOwnership to IslandRepository

**Files:**
- Modify: `src/main/java/team/starm/starmskyblock/database/IslandRepository.java`

**Interfaces:**
- Consumes: `Island.setOwnerId(UUID)` from Task 1
- Produces: `IslandRepository.transferOwnership(int islandId, UUID newOwnerUuid, UUID oldOwnerUuid)` — transactional DB swap

- [ ] **Step 1: Add the transferOwnership method**

In `src/main/java/team/starm/starmskyblock/database/IslandRepository.java`, add after the `migrateCoopToMember` method (after line 498):

```java
/**
 * 在事务中完成岛屿所有权转让：更新 owner_uuid、移除新岛主的成员记录、
 * 将旧岛主添加为 MEMBER。调用方需自行更新内存索引。
 */
public void transferOwnership(int islandId, UUID newOwnerUuid, UUID oldOwnerUuid) {
    try {
        sqliteManager.executeInTransaction(conn -> {
            // 1. 更新岛屿 owner_uuid
            try (PreparedStatement pstmt = sqliteManager.prepareCached(
                    "UPDATE islands SET owner_uuid = ? WHERE id = ?")) {
                pstmt.setString(1, newOwnerUuid.toString());
                pstmt.setInt(2, islandId);
                pstmt.executeUpdate();
            }
            // 2. 移除新岛主的成员记录（新岛主不再是普通成员）
            try (PreparedStatement pstmt = sqliteManager.prepareCached(
                    "DELETE FROM island_members WHERE island_id = ? AND player_uuid = ?")) {
                pstmt.setInt(1, islandId);
                pstmt.setString(2, newOwnerUuid.toString());
                pstmt.executeUpdate();
            }
            // 3. 将旧岛主添加为 MEMBER
            try (PreparedStatement pstmt = sqliteManager.prepareCached(
                    "INSERT INTO island_members (island_id, player_uuid, role) VALUES (?, ?, ?)")) {
                pstmt.setInt(1, islandId);
                pstmt.setString(2, oldOwnerUuid.toString());
                pstmt.setString(3, "MEMBER");
                pstmt.executeUpdate();
            }
            return null;
        });
    } catch (SQLException e) {
        MessageUtil.consoleError("岛屿所有权转让失败！岛屿ID: " + islandId
                + ", 旧岛主: " + oldOwnerUuid + ", 新岛主: " + newOwnerUuid, e);
    }
}
```

- [ ] **Step 2: Verify compilation**

```bash
./gradlew compileJava
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/team/starm/starmskyblock/database/IslandRepository.java
git commit -m "feat: add transferOwnership transactional method to IslandRepository"
```

---

### Task 3: Add transferIsland to IslandManager

**Files:**
- Modify: `src/main/java/team/starm/starmskyblock/island/IslandManager.java`

**Interfaces:**
- Consumes: `Island.setOwnerId(UUID)` (Task 1), `IslandRepository.transferOwnership(int, UUID, UUID)` (Task 2)
- Produces: `IslandManager.transferIsland(int islandId, UUID newOwnerUuid)` — coordinates memory indices and DB persistence

- [ ] **Step 1: Add the transferIsland method**

In `src/main/java/team/starm/starmskyblock/island/IslandManager.java`, add after `updateMemberRole` (after line 549):

```java
/**
 * 转让岛屿所有权：将岛主身份从旧岛主转移到新岛主（必须是现有成员）。
 * 旧岛主降为 MEMBER 留在岛上，同步更新所有内存索引和数据库。
 *
 * @param islandId      岛屿 ID
 * @param newOwnerUuid  新岛主 UUID（必须已是该岛屿成员）
 */
public void transferIsland(int islandId, UUID newOwnerUuid) {
    Island island = islandsById.get(islandId);
    if (island == null) return;

    UUID oldOwnerUuid = island.getOwnerId();

    // 1. 从成员列表中移除新岛主（新岛主不再是普通成员）
    island.removeMember(newOwnerUuid);

    // 2. 将旧岛主加入成员列表（降级为 MEMBER）
    island.addMember(oldOwnerUuid, IslandPermissionLevel.MEMBER);

    // 3. 更新岛屿的 ownerId
    island.setOwnerId(newOwnerUuid);

    // 4. 更新 islandsByOwner 索引
    islandsByOwner.remove(oldOwnerUuid);
    islandsByOwner.put(newOwnerUuid, island);

    // 5. memberToIslandIndex 保持不变 —— 两者都已在索引中
    //    （createIsland / loadIslandsFromDatabase 也会将岛主放入此索引）

    // 6. 持久化到数据库
    islandRepo.transferOwnership(islandId, newOwnerUuid, oldOwnerUuid);

    // 7. 保存新老岛主的玩家名到缓存
    Player newOwner = Bukkit.getPlayer(newOwnerUuid);
    if (newOwner != null) {
        playerRepo.savePlayerName(newOwnerUuid, newOwner.getName());
    }
    Player oldOwner = Bukkit.getPlayer(oldOwnerUuid);
    if (oldOwner != null) {
        playerRepo.savePlayerName(oldOwnerUuid, oldOwner.getName());
    }
}
```

- [ ] **Step 2: Verify compilation**

```bash
./gradlew compileJava
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/team/starm/starmskyblock/island/IslandManager.java
git commit -m "feat: add transferIsland to IslandManager"
```

---

### Task 4: Add i18n message keys

**Files:**
- Modify: `src/main/resources/messages/zh_CN.yml`

**Interfaces:**
- Produces: Message keys `transfer.*` and `help.entry.transfer`

- [ ] **Step 1: Add transfer message keys**

In `src/main/resources/messages/zh_CN.yml`, add after the `team.decline` block (after line 49):

```yaml
transfer:
  owner-only: "&c只有岛主才能转让岛屿！"
  not-member: "&c该玩家不是岛屿成员，无法转让！"
  self: "&c你不能将岛屿转让给自己！"
  usage: "&c用法: /is transfer <玩家名> [confirm]"
  confirm: "&c警告：将把岛屿所有权转让给 &e{name}&c！使用 &e/is transfer {name} confirm &c确认。"
  success: "&a已将岛屿所有权转让给 &e{name}&a！你现在是岛屿成员。"
  new-owner: "&a你已成为岛屿 &e{island}&a 的新主人！"
```

- [ ] **Step 2: Add help entry**

In the `help.entry` section (after line 430), add:

```yaml
    transfer: "&b/is transfer <玩家> &f- 转让岛屿所有权"
```

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/messages/zh_CN.yml
git commit -m "feat: add transfer i18n message keys"
```

---

### Task 5: Create TransferCommand

**Files:**
- Create: `src/main/java/team/starm/starmskyblock/command/subcommand/TransferCommand.java`

**Interfaces:**
- Consumes: `IslandManager.transferIsland(int, UUID)` (Task 3), message keys from Task 4
- Produces: TransferCommand handler for `/is transfer <player> [confirm]`

- [ ] **Step 1: Create TransferCommand.java**

Create file `src/main/java/team/starm/starmskyblock/command/subcommand/TransferCommand.java`:

```java
package team.starm.starmskyblock.command.subcommand;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import team.starm.starmskyblock.StarMSkyblock;
import team.starm.starmskyblock.island.Island;
import team.starm.starmskyblock.message.MessageUtil;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code /is transfer} 子命令 —— 转让岛屿所有权。
 * <p>
 * 岛主可将岛屿转让给任意现有成员。需二次确认（{@code /is transfer <玩家> confirm}），
 * 转让后旧岛主降为 MEMBER，新岛主获得完整所有权。
 */
public class TransferCommand extends SubCommand {

    public TransferCommand(StarMSkyblock plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(Player player, String[] args) {
        if (!assertMaxArgs(player, args, 3, "/is transfer <玩家名> [confirm]")) return true;

        // 1. 获取岛屿并校验岛主身份
        Optional<Island> optionalIsland = plugin.getIslandManager().getIslandByPlayer(player.getUniqueId());
        if (optionalIsland.isEmpty()) {
            MessageUtil.send(player, "general.island-not-found");
            return true;
        }

        Island island = optionalIsland.get();
        if (!island.getOwnerId().equals(player.getUniqueId())) {
            MessageUtil.send(player, "transfer.owner-only");
            return true;
        }

        // 2. 需要目标玩家名
        if (args.length < 2) {
            MessageUtil.send(player, "transfer.usage");
            return true;
        }

        // 3. 查找目标玩家（从岛屿成员中按名称匹配，不区分大小写）
        UUID targetUuid = island.getMembers().keySet().stream()
                .filter(uuid -> {
                    String name = getPlayerName(uuid);
                    return name != null && name.equalsIgnoreCase(args[1]);
                })
                .findFirst().orElse(null);

        if (targetUuid == null) {
            MessageUtil.send(player, "transfer.not-member");
            return true;
        }

        // 4. 不能转让给自己
        if (targetUuid.equals(player.getUniqueId())) {
            MessageUtil.send(player, "transfer.self");
            return true;
        }

        String targetName = getPlayerName(targetUuid);

        // 5. 确认机制
        if (args.length < 3 || !args[2].equalsIgnoreCase("confirm")) {
            MessageUtil.send(player, "transfer.confirm", Map.of("name", targetName));
            return true;
        }

        // 6. 执行转让
        plugin.getIslandManager().transferIsland(island.getId(), targetUuid);

        // 7. 通知双方
        String islandName = island.getName().isEmpty() ? String.valueOf(island.getId()) : island.getName();
        MessageUtil.send(player, "transfer.success", Map.of("name", targetName));
        Player targetPlayer = Bukkit.getPlayer(targetUuid);
        if (targetPlayer != null) {
            MessageUtil.send(targetPlayer, "transfer.new-owner", Map.of("island", islandName));
        }

        return true;
    }

    /**
     * Tab 补全：补全当前岛屿成员中权限等级低于岛主的玩家名（用于转让目标选择）
     */
    @Override
    public List<String> onTabComplete(Player player, String[] args) {
        if (args.length != 2) return List.of();

        var islandOpt = plugin.getIslandManager().getIslandByPlayer(player.getUniqueId());
        if (islandOpt.isEmpty()) return List.of();
        Island island = islandOpt.get();

        // 只有岛主才能看到补全
        if (!island.getOwnerId().equals(player.getUniqueId())) return List.of();

        String prefix = args[1].toLowerCase();
        return island.getMembers().keySet().stream()
                .map(uuid -> {
                    var name = plugin.getPlayerRepo().getPlayerName(uuid);
                    return name.orElse(uuid.toString());
                })
                .filter(name -> name.toLowerCase().startsWith(prefix))
                .toList();
    }
}
```

- [ ] **Step 2: Verify compilation**

```bash
./gradlew compileJava
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/team/starm/starmskyblock/command/subcommand/TransferCommand.java
git commit -m "feat: add TransferCommand for /is transfer"
```

---

### Task 6: Register transfer command in IslandCommand

**Files:**
- Modify: `src/main/java/team/starm/starmskyblock/command/IslandCommand.java`

**Interfaces:**
- Consumes: `TransferCommand` from Task 5

- [ ] **Step 1: Register the transfer subcommand**

In `src/main/java/team/starm/starmskyblock/command/IslandCommand.java`, add the registration line after `subCommands.put("top", ...)` (line 55):

```java
subCommands.put("transfer", new TransferCommand(plugin));
```

- [ ] **Step 2: Verify compilation**

```bash
./gradlew compileJava
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/team/starm/starmskyblock/command/IslandCommand.java
git commit -m "feat: register /is transfer subcommand"
```

---

### Final Verification

- [ ] **Step 1: Full build**

```bash
./gradlew build
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Run tests**

```bash
./gradlew test
```
Expected: All tests pass
