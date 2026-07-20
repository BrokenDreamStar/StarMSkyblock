# Island Transfer Design

**Date:** 2026-07-20
**Status:** approved

## Overview

Add `/is transfer <player> [confirm]` subcommand allowing an island owner to transfer ownership to an existing island member. After transfer, the old owner becomes a regular MEMBER, and the new owner gains full ownership.

## Design Decisions

| Decision | Choice |
|----------|--------|
| Old owner's new role | MEMBER (not ADMIN) |
| Transfer target | Must be an existing island member |
| Confirmation | Two-step: `/is transfer <name>` prompts, `/is transfer <name> confirm` executes |

## Changes

### 1. `Island.java` — Make ownerId mutable

- Change `private final UUID ownerId` → `private UUID ownerId`
- Add `setOwnerId(UUID ownerId)` setter

### 2. `IslandRepository.java` — New DB write method

Add `transferOwnership(int islandId, UUID newOwnerUuid, UUID oldOwnerUuid)` that runs in a transaction:
1. `UPDATE islands SET owner_uuid = ? WHERE id = ?` (new owner)
2. `DELETE FROM island_members WHERE island_id = ? AND player_uuid = ?` (remove new owner from members)
3. `INSERT INTO island_members (island_id, player_uuid, role) VALUES (?, ?, 'MEMBER')` (add old owner as member)

### 3. `IslandManager.java` — Core transfer logic

Add `transferIsland(int islandId, UUID newOwnerUuid)` method:
1. Get Island object and old owner UUID
2. Update Island object: `setOwnerId(newOwnerUuid)`, remove new owner from members, add old owner as MEMBER
3. Update `islandsByOwner` index: remove old owner → put new owner
4. `memberToIslandIndex` stays unchanged — both old and new owner remain indexed (consistent with `createIsland`/`loadIslandsFromDatabase` which put owners in this index too)
5. Call `islandRepo.transferOwnership()` to persist

### 4. `TransferCommand.java` — New subcommand (new file)

Extends `SubCommand`. Flow:
1. Assert max 2 args (`<player> [confirm]`)
2. Get sender's island; error if no island or not owner
3. Resolve target player by name (online first, then DB cache)
4. Validate: target is a member (not owner, not self, not visitor/coop)
5. If no `confirm`: send warning prompt with target name
6. If `confirm`: execute transfer via `islandManager.transferIsland()`, notify both parties

### 5. `IslandCommand.java` — Register command

Add in `registerCommands()`:
```java
subCommands.put("transfer", new TransferCommand(plugin));
```

### 6. `messages/zh_CN.yml` — Message keys

```yaml
transfer:
  usage: "&c用法: /is transfer <玩家名> [confirm]"
  owner-only: "&c只有岛主才能转让岛屿！"
  not-member: "&c该玩家不是岛屿成员，无法转让！"
  self: "&c你不能将岛屿转让给自己！"
  confirm: "&c警告：将把岛屿所有权转让给 &e{name}&c！使用 &e/is transfer {name} confirm &c确认。"
  success: "&a已将岛屿所有权转让给 &e{name}&a！你现在是岛屿成员。"
  new-owner: "&a你已成为岛屿 &e{island}&a 的新主人！"
  player-not-found: "&c未找到玩家: {name}"

help:
  entry:
    transfer: "&b/is transfer <玩家> &f- 转让岛屿所有权"
```

## Edge Cases

- **Target offline**: Allowed — operates on UUID, no online requirement
- **Target not a member**: Rejected with error message
- **Self-transfer**: Rejected
- **Sender has no island**: Rejected with standard no-island message
- **Sender is member (not owner)**: Rejected with owner-only message
- **Old owner post-transfer**: Remains on island as MEMBER, can play normally
- **New owner post-transfer**: Immediately gets OWNER role and all permissions
