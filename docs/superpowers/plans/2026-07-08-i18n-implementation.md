# i18n 国际化支持实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将所有玩家可见消息（约 400 处 `MessageUtil.sendMessage/broadcast` 调用）从 Java 字面量迁移到 YAML 语言文件，提供 `LanguageManager` + `MessageUtil.send(key, args)` API，支持全局 locale 切换。

**Architecture:** 新增 `LanguageManager` 加载 `messages/messages_<locale>.yml`，扁平化为 `Map<String, String>`。`MessageUtil` 启动期通过 `setLanguageManager()` 注入 LM 引用，新增 `send(sender, key)` / `send(sender, key, Map)` API。当前激活 locale 由 `config.yml` 的 `locale` 字段决定（默认 `zh_CN`），缺失时回退到内置 `zh_CN`。

**Tech Stack:** Java 25, Spigot/Paper API 26.1.2, Bukkit YamlConfiguration, Adventure API

**Spec:** `docs/superpowers/specs/2026-07-08-i18n-design.md`

---

## 文件结构

### 新建文件

- `src/main/java/team/starm/starmskyblock/message/LanguageManager.java` - 加载、缓存、查询语言文件
- `src/main/resources/messages/messages_zh_CN.yml` - 默认中文语言文件（含全部 i18n 键）

### 修改文件

- `src/main/java/team/starm/starmskyblock/message/MessageUtil.java` - 新增 `send/format/broadcast/setLanguageManager` 静态方法
- `src/main/java/team/starm/starmskyblock/StarMSkyblock.java` - 新增 `languageManager` 字段、`getLanguageManager()`、`initLanguage()` 方法
- `src/main/java/team/starm/starmskyblock/config/ConfigKeys.java` - 新增 `LOCALE` 常量
- `src/main/java/team/starm/starmskyblock/config/ConfigManager.java` - 新增 `locale` 字段和 `getLocale()` 方法
- `src/main/resources/config.yml` - 新增 `locale: 'zh_CN'` 字段
- `src/main/java/team/starm/starmskyblock/command/subcommand/ReloadCommand.java` - 新增 `plugin.getLanguageManager().reload()`

### 迁移文件（按批次）

约 30 个 Java 文件中的 `MessageUtil.sendMessage(...)` 调用全部改为 `MessageUtil.send(sender, key)` 或 `MessageUtil.send(sender, key, Map.of(...))`。注意：实施 Task 2 时识别出 `broadcast(String)` 与旧 API 签名冲突，i18n 广播方法已改名为 `broadcastKey(key)` / `broadcastKey(key, Map.of(...))`。代码库当前无 `MessageUtil.broadcast(...)` 调用（所有 400 处都是 `sendMessage`），所以迁移模板 D 仅用于将来扩展，本批次迁移不会触发。

---

## 迁移单元操作标准模板

> **每个 `sendMessage`/`broadcast` 调用都按此模板迁移。**

### 模板 A：纯字面量（无变量）

**Before:**
```java
MessageUtil.sendMessage(player, "&c你还没有岛屿！");
```

**After:**
```java
MessageUtil.send(player, "island.no-island");
```

YAML 新增：
```yaml
island:
  no-island: "&c你还没有岛屿！"
```

### 模板 B：含字符串拼接变量

**Before:**
```java
MessageUtil.sendMessage(player, "&c岛屿重命名冷却中，请等待 &e" + remaining + " &c秒后再试！");
```

**After:**
```java
MessageUtil.send(player, "island.rename.cooldown", Map.of("remaining", remaining));
```

YAML 新增：
```yaml
island:
  rename:
    cooldown: "&c岛屿重命名冷却中，请等待 &e{remaining} &c秒后再试！"
```

### 模板 C：含 String.format

**Before:**
```java
MessageUtil.sendMessage(player, String.format("&e岛屿保护 &f|&c 你没有&e %s &c权限！", permission.getDisplayName()));
```

**After:**
```java
MessageUtil.send(player, "permission.no-permission", Map.of("permission", permission.getDisplayName()));
```

YAML 新增：
```yaml
permission:
  no-permission: "&e岛屿保护 &f|&c 你没有&e {permission} &c权限！"
```

### 模板 D：广播（注意：方法名为 `broadcastKey`，非 `broadcast`）

> 实施时识别出 `broadcast(String)` 与旧字面量 API 签名冲突，i18n 广播方法改名为 `broadcastKey`。代码库当前无 `MessageUtil.broadcast(...)` 调用，此模板仅供将来扩展参考。

**Before:**
```java
MessageUtil.broadcast("&a玩家 &e" + player.getName() + " &a创建了新岛屿！");
```

**After:**
```java
MessageUtil.broadcastKey("island.create.broadcast", Map.of("player", player.getName()));
```

YAML 新增：
```yaml
island:
  create:
    broadcast: "&a玩家 &e{player} &a创建了新岛屿！"
```

### 迁移约束

1. **不修改业务逻辑** - 仅替换消息输出方式
2. **保留颜色代码** - 所有 `&a`、`&c`、`<gradient:...>` 等 ColorUtils 标签原封不动移入 YAML
3. **占位符名与变量名一致** - `remaining` → `{remaining}`，`player.getName()` → `{player}`（取最后一段方法名）
4. **每个字面量调用点单独审视** - 不要批量替换，避免把 `"&c" + x` 误改为 `Map.of("x", x)`
5. **若文件未引入 `java.util.Map`** - 添加 `import java.util.Map;`
6. **YAML 文件追加语义** - 后续批次向 `messages_zh_CN.yml` 追加键时，**合并到已有顶层 section 下**，不要重新定义同名顶层键。例如批次 1 已添加 `island: { no-island: ... }`，批次 5 添加 `island.invitation.*` 时应合并为：

   ```yaml
   island:
     no-island: "&c你还没有岛屿！"  # 已存在
     invitation:                     # 新增子 section
       sent: "&a已邀请..."
       received: "&a你被邀请..."
   ```

   不要写成两个独立的 `island:` 顶层键（YAML 不允许重复顶层键，会导致后定义的覆盖前者）。

---

## Task 1: LanguageManager 类 + ConfigManager locale 字段

**Files:**
- Create: `src/main/java/team/starm/starmskyblock/message/LanguageManager.java`
- Modify: `src/main/java/team/starm/starmskyblock/config/ConfigKeys.java`
- Modify: `src/main/java/team/starm/starmskyblock/config/ConfigManager.java`
- Modify: `src/main/resources/config.yml`

- [ ] **Step 1: 在 `ConfigKeys.java` 添加 `LOCALE` 常量**

在 `// 公共世界` 注释前插入：

```java
    // i18n
    public static final String LOCALE = "locale";
```

- [ ] **Step 2: 在 `ConfigManager.java` 添加 `locale` 字段**

在 `private volatile Set<String> publicWorlds = Collections.emptySet();` 之后添加：

```java
    private volatile String locale = "zh_CN"; // 当前激活的 i18n locale
```

- [ ] **Step 3: 在 `ConfigManager.loadConfig()` 末尾加载 `locale` 字段**

在 `this.publicWorlds = new HashSet<>(config.getStringList(ConfigKeys.PUBLIC_WORLDS));` 之后添加：

```java
        // ====================== i18n locale ======================
        String rawLocale = config.getString(ConfigKeys.LOCALE, "zh_CN");
        if (!rawLocale.matches("[a-z]{2}_[A-Z]{2}")) {
            MessageUtil.consoleWarn("locale 字段格式非法（期望 xx_XX 如 zh_CN），已回退到 zh_CN");
            rawLocale = "zh_CN";
        }
        this.locale = rawLocale;
```

- [ ] **Step 4: 在 `ConfigManager.java` 添加 `getLocale()` 方法**

在 `isPublicWorld(String worldName)` 方法之后添加：

```java
    /**
     * @return 当前激活的 i18n locale（如 zh_CN、en_US）
     */
    public String getLocale() {
        return locale;
    }
```

- [ ] **Step 5: 在 `config.yml` 末尾添加 `locale` 字段**

在文件末尾（`public-worlds: - 'world'` 之后）添加：

```yaml

# i18n 设置
# 当前激活的语言代码，需对应 messages/messages_<locale>.yml 文件
# 内置仅 zh_CN，可自行添加其他语言文件
locale: 'zh_CN'
```

- [ ] **Step 6: 创建 `LanguageManager.java`**

完整文件内容：

```java
package team.starm.starmskyblock.message;

import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import team.starm.starmskyblock.StarMSkyblock;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 加载、缓存、查询 i18n 消息。
 * <p>
 * 启动期从 config.yml 读取 locale，加载 messages/messages_<locale>.yml，
 * 扁平化为 Map<String, String>（key 全路径 -> 字符串值）。
 * 缺失 key 时返回字面 key 文本并一次性 consoleWarn。
 * <p>
 * 线程安全：messages map 用 ConcurrentHashMap，reload 用 volatile 引用替换。
 */
public final class LanguageManager {

    private static final Pattern LOCALE_PATTERN = Pattern.compile("[a-z]{2}_[A-Z]{2}");
    private static final String DEFAULT_LOCALE = "zh_CN";
    private static final String MESSAGES_DIR = "messages";
    private static final String MESSAGES_FILE_PREFIX = "messages_";
    private static final String MESSAGES_FILE_SUFFIX = ".yml";

    private final StarMSkyblock plugin;

    /** 当前激活 locale，volatile 保证 reload 后立即可见 */
    private volatile String locale = DEFAULT_LOCALE;
    /** 扁平化后的 key -> value 映射，volatile 保证 reload 后引用原子替换 */
    private volatile Map<String, String> messages = Collections.emptyMap();
    /** 已警告过的缺失 key 集合，避免日志洪水 */
    private final Set<String> warnedMissingKeys = ConcurrentHashMap.newKeySet();

    public LanguageManager(@NotNull StarMSkyblock plugin) {
        this.plugin = plugin;
    }

    /**
     * 启动期初始化：解压内置 zh_CN，加载激活 locale 文件，扁平化。
     * 失败时回退 zh_CN；zh_CN 也缺失则禁用插件。
     */
    public void initialize() {
        extractBundledZhCN();
        loadLocale(plugin.getConfigManager().getLocale());
    }

    /**
     * 重载：重新读取 config.yml locale 字段，重新加载文件。
     * 由 /isadmin reload 调用。
     */
    public void reload() {
        warnedMissingKeys.clear();
        loadLocale(plugin.getConfigManager().getLocale());
        MessageUtil.consolePrint("i18n 已重载：locale=" + locale);
    }

    /**
     * 取已格式化消息：查 key，替换占位符 {name} -> 值。
     * 缺失 key 返回字面 key 文本并一次性 consoleWarn。
     */
    public String format(@NotNull String key, @Nullable Map<String, ?> args) {
        String template = messages.get(key);
        if (template == null) {
            if (warnedMissingKeys.add(key)) {
                MessageUtil.consoleWarn("i18n 缺失键: " + key);
            }
            return key;
        }
        if (args == null || args.isEmpty()) {
            return template;
        }
        for (Map.Entry<String, ?> e : args.entrySet()) {
            template = template.replace("{" + e.getKey() + "}", String.valueOf(e.getValue()));
        }
        return template;
    }

    public String getLocale() {
        return locale;
    }

    public Set<String> getKeySet() {
        return Collections.unmodifiableSet(messages.keySet());
    }

    // ==================== 内部加载逻辑 ====================

    private void extractBundledZhCN() {
        File messagesDir = new File(plugin.getDataFolder(), MESSAGES_DIR);
        if (!messagesDir.exists()) {
            messagesDir.mkdirs();
        }
        File target = new File(messagesDir, MESSAGES_FILE_PREFIX + DEFAULT_LOCALE + MESSAGES_FILE_SUFFIX);
        if (target.exists()) {
            return;
        }
        try (InputStream in = plugin.getResource(MESSAGES_DIR + "/" + MESSAGES_FILE_PREFIX + DEFAULT_LOCALE + MESSAGES_FILE_SUFFIX)) {
            if (in == null) {
                MessageUtil.consoleError("内置语言文件缺失: messages/messages_" + DEFAULT_LOCALE + ".yml");
                return;
            }
            Files.copy(in, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            MessageUtil.consoleError("解压内置语言文件失败: " + target.getName(), e);
        }
    }

    private void loadLocale(@NotNull String requestedLocale) {
        String validated = validateLocale(requestedLocale);
        if (!validated.equals(requestedLocale)) {
            this.locale = validated;
        } else {
            this.locale = requestedLocale;
        }

        YamlConfiguration yaml = loadYamlFile(this.locale);
        if (yaml == null && !this.locale.equals(DEFAULT_LOCALE)) {
            MessageUtil.consoleWarn("语言文件缺失，回退到 " + DEFAULT_LOCALE + ": messages/messages_" + this.locale + ".yml");
            this.locale = DEFAULT_LOCALE;
            yaml = loadYamlFile(DEFAULT_LOCALE);
        }
        if (yaml == null) {
            MessageUtil.consoleError("内置语言文件缺失，i18n 系统不可用：messages/messages_" + DEFAULT_LOCALE + ".yml");
            plugin.getServer().getPluginManager().disablePlugin(plugin);
            return;
        }

        Map<String, String> flat = flatten(yaml);
        this.messages = flat;
        MessageUtil.consolePrint("已加载 i18n 语言文件: " + this.locale + "（" + flat.size() + " 个键）");
    }

    private String validateLocale(@NotNull String locale) {
        if (LOCALE_PATTERN.matcher(locale).matches()) {
            return locale;
        }
        MessageUtil.consoleWarn("locale 格式非法（期望 xx_XX 如 zh_CN），使用默认 " + DEFAULT_LOCALE);
        return DEFAULT_LOCALE;
    }

    /**
     * 优先加载 plugins/StarMSkyblock/messages/messages_<locale>.yml，
     * 不存在则尝试 jar 内 messages/messages_<locale>.yml。
     * 返回 null 表示两者都缺失。
     */
    private YamlConfiguration loadYamlFile(@NotNull String locale) {
        File external = new File(plugin.getDataFolder(),
                MESSAGES_DIR + "/" + MESSAGES_FILE_PREFIX + locale + MESSAGES_FILE_SUFFIX);
        if (external.exists()) {
            try {
                return YamlConfiguration.loadConfiguration(external);
            } catch (Exception e) {
                MessageUtil.consoleError("语言文件 YAML 语法错误: " + external.getName() + " - " + e.getMessage());
                return null;
            }
        }
        try (InputStream in = plugin.getResource(MESSAGES_DIR + "/" + MESSAGES_FILE_PREFIX + locale + MESSAGES_FILE_SUFFIX)) {
            if (in == null) {
                return null;
            }
            return YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException e) {
            MessageUtil.consoleError("读取内置语言文件失败: messages_" + locale + ".yml - " + e.getMessage());
            return null;
        }
    }

    /**
     * 递归扁平化 YAML 为 Map<String, String>。
     * 仅保留 string 叶节点，跳过 section/list/number 等。
     */
    private Map<String, String> flatten(@NotNull YamlConfiguration yaml) {
        Map<String, String> result = new ConcurrentHashMap<>();
        for (String key : yaml.getKeys(true)) {
            if (yaml.isConfigurationSection(key)) {
                continue;
            }
            Object value = yaml.get(key);
            if (value == null) {
                continue;
            }
            result.put(key, value.toString());
        }
        return result;
    }
}
```

- [ ] **Step 7: 编译验证**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add src/main/java/team/starm/starmskyblock/message/LanguageManager.java \
        src/main/java/team/starm/starmskyblock/config/ConfigKeys.java \
        src/main/java/team/starm/starmskyblock/config/ConfigManager.java \
        src/main/resources/config.yml
git commit -m "feat(i18n): add LanguageManager and locale config field"
```

---

## Task 2: MessageUtil 新增 send/format/broadcast API + 初始 messages_zh_CN.yml

**Files:**
- Modify: `src/main/java/team/starm/starmskyblock/message/MessageUtil.java`
- Create: `src/main/resources/messages/messages_zh_CN.yml`

- [ ] **Step 1: 在 `MessageUtil.java` 添加 `import java.util.Map;`**

修改文件头部的 import 块，在 `import java.util.Set;` 之后添加：

```java
import java.util.Map;
```

- [ ] **Step 2: 在 `MessageUtil` 类中添加 `languageManager` 字段**

在 `private static final Set<UUID> SILENT_PLAYERS = ...;` 之后添加：

```java
    /** 启动期由 StarMSkyblock 注入；未初始化时 send(key) 返回字面 key 文本 */
    private static volatile LanguageManager languageManager;

    /**
     * 启动期注入 LanguageManager 引用。
     * 由 StarMSkyblock.onEnable() 在 LanguageManager.initialize() 之后调用。
     */
    public static void setLanguageManager(@org.jetbrains.annotations.Nullable LanguageManager lm) {
        languageManager = lm;
    }
```

- [ ] **Step 3: 在 `MessageUtil` 类中添加 `send` / `format` / `broadcast(key)` 系列方法**

在 `public static void broadcast(@Nullable String message)` 方法之后添加：

```java

    // ==================== i18n key-based API ====================

    /**
     * 发送 i18n 消息（无占位符）。
     * 静默模式下自动跳过玩家消息。
     */
    public static void send(@NotNull CommandSender sender, @NotNull String key) {
        send(sender, key, null);
    }

    /**
     * 发送 i18n 消息（带命名占位符 {name}）。
     * 静默模式下自动跳过玩家消息。
     * LanguageManager 未初始化时回退到字面 key 文本。
     */
    public static void send(@NotNull CommandSender sender, @NotNull String key, @Nullable Map<String, ?> args) {
        if (sender instanceof Player player && SILENT_PLAYERS.contains(player.getUniqueId())) return;
        if (languageManager == null) {
            sender.sendMessage(key);
            return;
        }
        sender.sendMessage(colorize(languageManager.format(key, args)));
    }

    /**
     * 广播 i18n 消息（无占位符）。
     */
    public static void broadcast(@NotNull String key) {
        broadcast(key, null);
    }

    /**
     * 广播 i18n 消息（带命名占位符）。
     */
    public static void broadcast(@NotNull String key, @Nullable Map<String, ?> args) {
        if (languageManager == null) {
            Bukkit.broadcastMessage(key);
            return;
        }
        Bukkit.broadcastMessage(colorize(languageManager.format(key, args)));
    }

    /**
     * 取已格式化 i18n 字符串（不发送），用于 TrMenu JS bridge 或 PAPI 等场景。
     */
    public static String format(@NotNull String key) {
        return format(key, null);
    }

    /**
     * 取已格式化 i18n 字符串（不发送）。
     */
    public static String format(@NotNull String key, @Nullable Map<String, ?> args) {
        if (languageManager == null) return key;
        return languageManager.format(key, args);
    }
```

- [ ] **Step 4: 创建初始 `messages/messages_zh_CN.yml`**

完整文件内容（仅含一个示例键，后续任务追加更多键）：

```yaml
# StarMSkyblock i18n 默认语言文件（zh_CN）
# 键命名约定：层级点分式 kebab-case，最多 4 层
# 占位符：{name} 命名式

general:
  placeholder: "&7i18n 系统已就绪"
```

- [ ] **Step 5: 编译验证**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add src/main/java/team/starm/starmskyblock/message/MessageUtil.java \
        src/main/resources/messages/messages_zh_CN.yml
git commit -m "feat(i18n): add MessageUtil.send/format/broadcast key API"
```

---

## Task 3: StarMSkyblock 启动期注入 + ReloadCommand 扩展

**Files:**
- Modify: `src/main/java/team/starm/starmskyblock/StarMSkyblock.java`
- Modify: `src/main/java/team/starm/starmskyblock/command/subcommand/ReloadCommand.java`

- [ ] **Step 1: 在 `StarMSkyblock.java` 添加 `LanguageManager` import**

在 `import team.starm.starmskyblock.message.MessageUtil;` 之后添加：

```java
import team.starm.starmskyblock.message.LanguageManager;
```

- [ ] **Step 2: 在 `StarMSkyblock.java` 添加 `languageManager` 字段**

在 `// ========== 公共区域 ==========` 注释块之前（即 `private SkyblockExpansion skyblockExpansion;` 之后）添加：

```java
    // ========== i18n ==========
    private LanguageManager languageManager;
```

- [ ] **Step 3: 在 `StarMSkyblock.onEnable()` 调用 `initLanguage()`**

修改 `onEnable()` 方法，在 `initConfigs();` 之后、`Bukkit.getScheduler().runTaskAsynchronously(this, this::extractSchematics);` 之前插入 `initLanguage();`：

```java
        if (!checkWorldEdit()) return;
        initConfigs();
        initLanguage();
        // extractSchematics 是纯文件 IO,异步执行不阻塞主线程启动链
        Bukkit.getScheduler().runTaskAsynchronously(this, this::extractSchematics);
```

- [ ] **Step 4: 在 `StarMSkyblock.java` 添加 `initLanguage()` 方法**

在 `private void initConfigs() { ... }` 方法之后添加：

```java
    private void initLanguage() {
        languageManager = new LanguageManager(this);
        languageManager.initialize();
        MessageUtil.setLanguageManager(languageManager);
    }
```

- [ ] **Step 5: 在 `StarMSkyblock.java` 添加 `getLanguageManager()` 方法**

在 `public UpgradeConfigManager getUpgradeConfigManager() { return upgradeConfigManager; }` 之后添加：

```java
    public LanguageManager getLanguageManager() {
        return languageManager;
    }
```

- [ ] **Step 6: 在 `ReloadCommand.java` 添加 LanguageManager reload**

修改 `execute` 方法，在 `plugin.getPermissionConfigManager().reloadPermissionsConfig();` 之后添加：

```java
        plugin.getLanguageManager().reload();
```

最终 ReloadCommand.execute 完整代码：

```java
    @Override
    public boolean execute(CommandSender sender, String[] args) {
        long start = System.currentTimeMillis();

        plugin.getConfigManager().reload();
        plugin.getGeneratorConfigManager().reload();
        plugin.getUpgradeConfigManager().reload();
        plugin.getSignConfigManager().reloadSignConfig();
        plugin.getSettingsConfigManager().reloadSettingsConfig();
        plugin.getPermissionConfigManager().reloadPermissionsConfig();
        plugin.getLanguageManager().reload();

        long elapsed = System.currentTimeMillis() - start;
        MessageUtil.sendMessage(sender, "&a所有配置文件已重载！(耗时 " + elapsed + "ms)");
        return true;
    }
```

- [ ] **Step 7: 编译验证**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add src/main/java/team/starm/starmskyblock/StarMSkyblock.java \
        src/main/java/team/starm/starmskyblock/command/subcommand/ReloadCommand.java
git commit -m "feat(i18n): wire LanguageManager into plugin lifecycle + reload"
```

---

## Task 4: 迁移批次 1 - command/subcommand/* (269 调用点)

**Files (按调用数排序):**
- Modify: `src/main/java/team/starm/starmskyblock/command/subcommand/TeamCommand.java` (35)
- Modify: `src/main/java/team/starm/starmskyblock/command/subcommand/CoopCommand.java` (24)
- Modify: `src/main/java/team/starm/starmskyblock/command/subcommand/UpgradeCommand.java` (22)
- Modify: `src/main/java/team/starm/starmskyblock/command/subcommand/MembersInfoCommand.java` (19)
- Modify: `src/main/java/team/starm/starmskyblock/command/subcommand/GeneratorCommand.java` (16)
- Modify: `src/main/java/team/starm/starmskyblock/command/subcommand/PromoteDemoteCommand.java` (13)
- Modify: `src/main/java/team/starm/starmskyblock/command/subcommand/SettingsCommand.java` (12)
- Modify: `src/main/java/team/starm/starmskyblock/command/subcommand/SetChunkBiomeCommand.java` (11)
- Modify: `src/main/java/team/starm/starmskyblock/command/subcommand/SetBiomeCommand.java` (11)
- Modify: `src/main/java/team/starm/starmskyblock/command/subcommand/TpCommand.java` (10)
- Modify: `src/main/java/team/starm/starmskyblock/command/subcommand/SetSpawnCommand.java` (9)
- Modify: `src/main/java/team/starm/starmskyblock/command/subcommand/InfoCommand.java` (9)
- Modify: `src/main/java/team/starm/starmskyblock/command/subcommand/PortalInfoCommand.java` (8)
- Modify: `src/main/java/team/starm/starmskyblock/command/subcommand/ListCommand.java` (8)
- Modify: `src/main/java/team/starm/starmskyblock/command/subcommand/SpawnCommand.java` (7)
- Modify: `src/main/java/team/starm/starmskyblock/command/subcommand/SetTaskCommand.java` (7)
- Modify: `src/main/java/team/starm/starmskyblock/command/subcommand/SetRadiusCommand.java` (7)
- Modify: `src/main/java/team/starm/starmskyblock/command/subcommand/SetGeneratorCommand.java` (7)
- Modify: `src/main/java/team/starm/starmskyblock/command/subcommand/RenameCommand.java` (7)
- Modify: `src/main/java/team/starm/starmskyblock/command/subcommand/DeleteCommand.java` (7)
- Modify: `src/main/java/team/starm/starmskyblock/command/subcommand/CreateCommand.java` (6)
- Modify: `src/main/java/team/starm/starmskyblock/command/subcommand/AcceptDeclineCommand.java` (6)
- Modify: `src/main/java/team/starm/starmskyblock/command/subcommand/BorderCommand.java` (5)
- Modify: `src/main/java/team/starm/starmskyblock/command/subcommand/LevelCommand.java` (1)
- Modify: `src/main/resources/messages/messages_zh_CN.yml` (累积追加键)

- [ ] **Step 1: 迁移 TeamCommand.java**

打开文件，按"迁移单元操作标准模板"逐个调用点迁移。

**示例迁移**（取一个有变量插值的调用作为示范）：

Before:
```java
MessageUtil.sendMessage(player, "&c玩家 &e" + target.getName() + " &c不是你的岛屿成员！");
```

After:
```java
MessageUtil.send(player, "team.kick.not-member", Map.of("player", target.getName()));
```

YAML 追加到 `messages_zh_CN.yml` 顶部 `general:` 之前：

```yaml
team:
  kick:
    not-member: "&c玩家 &e{player} &c不是你的岛屿成员！"
```

若文件未引入 `java.util.Map`，在文件顶部 import 块添加 `import java.util.Map;`。

- [ ] **Step 2: 迁移 CoopCommand.java**

按模板迁移全部 24 处调用。每个调用点单独审视，确认占位符语义和上下文。

- [ ] **Step 3: 迁移 UpgradeCommand.java**

按模板迁移全部 22 处调用。注意 `showUpgradeInfo` 方法中含多行列表消息，每条独立翻译为 `upgrade.info.*` 子键。

- [ ] **Step 4: 迁移 MembersInfoCommand.java**

按模板迁移全部 19 处调用。

- [ ] **Step 5: 迁移 GeneratorCommand.java**

按模板迁移全部 16 处调用。

- [ ] **Step 6: 迁移 PromoteDemoteCommand.java**

按模板迁移全部 13 处调用。

- [ ] **Step 7: 迁移 SettingsCommand.java**

按模板迁移全部 12 处调用。

- [ ] **Step 8: 迁移 SetChunkBiomeCommand.java**

按模板迁移全部 11 处调用。

- [ ] **Step 9: 迁移 SetBiomeCommand.java**

按模板迁移全部 11 处调用。

- [ ] **Step 10: 迁移 TpCommand.java**

按模板迁移全部 10 处调用。

- [ ] **Step 11: 迁移 SetSpawnCommand.java**

按模板迁移全部 9 处调用。

- [ ] **Step 12: 迁移 InfoCommand.java**

按模板迁移全部 9 处调用。

- [ ] **Step 13: 迁移 PortalInfoCommand.java**

按模板迁移全部 8 处调用。

- [ ] **Step 14: 迁移 ListCommand.java**

按模板迁移全部 8 处调用。

- [ ] **Step 15: 迁移 SpawnCommand.java**

按模板迁移全部 7 处调用。

- [ ] **Step 16: 迁移 SetTaskCommand.java**

按模板迁移全部 7 处调用。

- [ ] **Step 17: 迁移 SetRadiusCommand.java**

按模板迁移全部 7 处调用。

- [ ] **Step 18: 迁移 SetGeneratorCommand.java**

按模板迁移全部 7 处调用。

- [ ] **Step 19: 迁移 RenameCommand.java**

按模板迁移全部 7 处调用。示例：

Before:
```java
MessageUtil.sendMessage(player, "&c岛屿重命名冷却中，请等待 &e" + remaining + " &c秒后再试！");
```

After:
```java
MessageUtil.send(player, "island.rename.cooldown", Map.of("remaining", remaining));
```

YAML 追加：
```yaml
island:
  rename:
    cooldown: "&c岛屿重命名冷却中，请等待 &e{remaining} &c秒后再试！"
    usage: "&c用法: /is rename <新名称>"
    too-long: "&c岛屿名称不能超过 &e{max} &c个字符（颜色代码不计入）！"
    success: "&a岛屿名称已修改为: &e{name}"
    failed: "&c修改失败，请稍后重试。"
    no-permission: "&c你没有权限修改岛屿名称！"
  no-island: "&c你还没有岛屿！"
```

- [ ] **Step 20: 迁移 DeleteCommand.java**

按模板迁移全部 7 处调用。

- [ ] **Step 21: 迁移 CreateCommand.java**

按模板迁移全部 6 处调用。

- [ ] **Step 22: 迁移 AcceptDeclineCommand.java**

按模板迁移全部 6 处调用。

- [ ] **Step 23: 迁移 BorderCommand.java**

按模板迁移全部 5 处调用。

- [ ] **Step 24: 迁移 LevelCommand.java**

按模板迁移 1 处调用。

- [ ] **Step 25: 编译验证**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 26: 自检 - 确认 subcommand 目录无遗漏**

Run: `grep -rn "MessageUtil.sendMessage\|MessageUtil.broadcast" src/main/java/team/starm/starmskyblock/command/subcommand/ | wc -l`
Expected: 0

若非 0，逐个检查未迁移的调用点并补迁移。

- [ ] **Step 27: Commit**

```bash
git add src/main/java/team/starm/starmskyblock/command/subcommand/ \
        src/main/resources/messages/messages_zh_CN.yml
git commit -m "refactor(i18n): migrate command/subcommand/* to message keys (269 calls)"
```

---

## Task 5: 迁移批次 2 - command dispatch (66 调用点)

**Files:**
- Modify: `src/main/java/team/starm/starmskyblock/command/IslandCommand.java` (34)
- Modify: `src/main/java/team/starm/starmskyblock/command/IslandPermissionCommand.java` (26)
- Modify: `src/main/java/team/starm/starmskyblock/command/AdminCommand.java` (6)
- Modify: `src/main/resources/messages/messages_zh_CN.yml` (追加键)

- [ ] **Step 1: 迁移 IslandCommand.java**

按"迁移单元操作标准模板"迁移全部 34 处调用。

dispatch 层多为帮助信息、参数错误提示、未知子命令等通用消息。建议归入 `command.is.*` 命名空间：

示例 YAML 结构（追加到 messages_zh_CN.yml）：

```yaml
command:
  is:
    usage: "&c用法: /is <create|spawn|border|help>"
    unknown-subcommand: "&c未知子命令。使用 /is help 查看帮助。"
    no-permission: "&c你没有权限执行此命令。"
    player-only: "&c该命令只能由玩家执行。"
  admin:
    usage: "&c用法: /isadmin <setradius|setgenerator|settask|reload>"
    unknown-subcommand: "&c未知管理员子命令。"
    no-permission: "&c你没有管理员权限。"
  permission:
    usage: "&c用法: /islandpermission <...>"
```

若文件未引入 `java.util.Map`，添加 import。

- [ ] **Step 2: 迁移 IslandPermissionCommand.java**

按模板迁移全部 26 处调用。归入 `command.permission.*` 命名空间。

- [ ] **Step 3: 迁移 AdminCommand.java**

按模板迁移全部 6 处调用。归入 `command.admin.*` 命名空间。

注意：`ReloadCommand` 不在此批次（其成功提示是面向管理员的，但已纳入 Task 3 的 `plugin.getLanguageManager().reload();` 一同处理，ReloadCommand 中现有 `MessageUtil.sendMessage(sender, "&a所有配置文件已重载！(耗时 " + elapsed + "ms)")` 也需迁移为 `MessageUtil.send(sender, "command.admin.reload-success", Map.of("elapsed", elapsed))`）。

实际上 `ReloadCommand` 不在批次 2 的文件清单中。若批次 2 完成后 ReloadCommand 中仍有未迁移调用，作为 Step 4 单独处理。

- [ ] **Step 4: 迁移 ReloadCommand 的成功提示**

修改 `ReloadCommand.java` 第 25 行：

Before:
```java
MessageUtil.sendMessage(sender, "&a所有配置文件已重载！(耗时 " + elapsed + "ms)");
```

After:
```java
MessageUtil.send(sender, "command.admin.reload-success", Map.of("elapsed", elapsed));
```

YAML 追加：
```yaml
command:
  admin:
    reload-success: "&a所有配置文件已重载！(耗时 {elapsed}ms)"
```

若文件未引入 `java.util.Map`，添加 import。

- [ ] **Step 5: 编译验证**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: 自检**

Run: `grep -rn "MessageUtil.sendMessage\|MessageUtil.broadcast" src/main/java/team/starm/starmskyblock/command/IslandCommand.java src/main/java/team/starm/starmskyblock/command/IslandPermissionCommand.java src/main/java/team/starm/starmskyblock/command/AdminCommand.java src/main/java/team/starm/starmskyblock/command/subcommand/ReloadCommand.java | wc -l`
Expected: 0

- [ ] **Step 7: Commit**

```bash
git add src/main/java/team/starm/starmskyblock/command/IslandCommand.java \
        src/main/java/team/starm/starmskyblock/command/IslandPermissionCommand.java \
        src/main/java/team/starm/starmskyblock/command/AdminCommand.java \
        src/main/java/team/starm/starmskyblock/command/subcommand/ReloadCommand.java \
        src/main/resources/messages/messages_zh_CN.yml
git commit -m "refactor(i18n): migrate command dispatch + ReloadCommand (66 calls)"
```

---

## Task 6: 迁移批次 3 - task/* (31 调用点)

**Files:**
- Modify: `src/main/java/team/starm/starmskyblock/task/command/TaskCommand.java` (16)
- Modify: `src/main/java/team/starm/starmskyblock/task/TaskManager.java` (15)
- Modify: `src/main/resources/messages/messages_zh_CN.yml` (追加键)

- [ ] **Step 1: 迁移 TaskCommand.java**

按"迁移单元操作标准模板"迁移全部 16 处调用。归入 `task.command.*` 命名空间。

示例 YAML 结构：

```yaml
task:
  command:
    usage: "&c用法: /is task <submit|claim|info>"
    no-permission: "&c你没有权限执行任务命令。"
  submit:
    no-progress: "&c该任务尚未完成，无法提交。"
    success: "&a任务提交成功！"
    failed: "&c任务提交失败：{reason}"
  claim:
    no-reward: "&c该任务没有可领取的奖励。"
    success: "&a奖励已领取！"
  info:
    header: "&a=== 任务信息 ==="
    locked: "&7该任务尚未解锁。"
```

- [ ] **Step 2: 迁移 TaskManager.java**

按模板迁移全部 15 处调用。归入 `task.*` 命名空间（与上面合并）。

- [ ] **Step 3: 编译验证**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 自检**

Run: `grep -rn "MessageUtil.sendMessage\|MessageUtil.broadcast" src/main/java/team/starm/starmskyblock/task/ | wc -l`
Expected: 0

- [ ] **Step 5: Commit**

```bash
git add src/main/java/team/starm/starmskyblock/task/ \
        src/main/resources/messages/messages_zh_CN.yml
git commit -m "refactor(i18n): migrate task/* to message keys (31 calls)"
```

---

## Task 7: 迁移批次 4 - level/* (16 调用点)

**Files:**
- Modify: `src/main/java/team/starm/starmskyblock/level/LevelManager.java` (16)
- Modify: `src/main/resources/messages/messages_zh_CN.yml` (追加键)

- [ ] **Step 1: 迁移 LevelManager.java**

按"迁移单元操作标准模板"迁移全部 16 处调用。归入 `level.*` 命名空间。

示例 YAML 结构：

```yaml
level:
  calculating: "&a正在计算岛屿等级..."
  cooldown: "&c等级计算冷却中，请等待 &e{remaining} &c秒后再试。"
  result:
    header: "&a=== 岛屿等级 ==="
    level: "&7等级: &e{level}"
    points: "&7点数: &e{points}"
  failed: "&c等级计算失败：{reason}"
```

- [ ] **Step 2: 编译验证**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 自检**

Run: `grep -rn "MessageUtil.sendMessage\|MessageUtil.broadcast" src/main/java/team/starm/starmskyblock/level/ | wc -l`
Expected: 0

- [ ] **Step 4: Commit**

```bash
git add src/main/java/team/starm/starmskyblock/level/LevelManager.java \
        src/main/resources/messages/messages_zh_CN.yml
git commit -m "refactor(i18n): migrate level/LevelManager to message keys (16 calls)"
```

---

## Task 8: 迁移批次 5 - island/* (12 调用点)

**Files:**
- Modify: `src/main/java/team/starm/starmskyblock/island/InvitationManager.java` (12)
- Modify: `src/main/resources/messages/messages_zh_CN.yml` (追加键)

- [ ] **Step 1: 迁移 InvitationManager.java**

按"迁移单元操作标准模板"迁移全部 12 处调用。归入 `island.invitation.*` 命名空间。

示例 YAML 结构：

```yaml
island:
  invitation:
    sent: "&a已邀请 &e{player} &a加入岛屿。"
    received: "&a你被邀请加入 &e{owner} &a的岛屿！使用 /is accept 接受。"
    expired: "&c邀请已过期。"
    declined: "&c你已拒绝邀请。"
    already-member: "&c该玩家已经是岛屿成员。"
    not-invited: "&c你没有收到该岛屿的邀请。"
    sender-offline: "&c邀请发送失败：对方不在线。"
    full: "&c岛屿成员已满。"
```

- [ ] **Step 2: 编译验证**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 自检**

Run: `grep -rn "MessageUtil.sendMessage\|MessageUtil.broadcast" src/main/java/team/starm/starmskyblock/island/ | wc -l`
Expected: 0

- [ ] **Step 4: Commit**

```bash
git add src/main/java/team/starm/starmskyblock/island/InvitationManager.java \
        src/main/resources/messages/messages_zh_CN.yml
git commit -m "refactor(i18n): migrate island/InvitationManager to message keys (12 calls)"
```

---

## Task 9: 迁移批次 6 - permission + setting + listener (6 调用点)

**Files:**
- Modify: `src/main/java/team/starm/starmskyblock/permission/BasePermissionManager.java` (4)
- Modify: `src/main/java/team/starm/starmskyblock/setting/manager/PvpSettingManager.java` (1)
- Modify: `src/main/java/team/starm/starmskyblock/listener/TeleportCountdownListener.java` (1)
- Modify: `src/main/resources/messages/messages_zh_CN.yml` (追加键)

- [ ] **Step 1: 迁移 BasePermissionManager.java**

按"迁移单元操作标准模板"迁移全部 4 处调用。归入 `permission.*` 命名空间。

示例 YAML 结构（含 String.format 转换示例）：

```yaml
permission:
  protected: "&e岛屿保护 &f|&c 当前区域未解锁，请升级岛屿！"
  public-area: "&e岛屿保护 &f|&c 公共区域不允许进行操作！"
  no-permission: "&e岛屿保护 &f|&c 你没有&e {permission} &c权限！"
  locked: "&e岛屿保护 &f|&c 该区域已锁定：{reason}"
```

调用示例（BasePermissionManager.java:262）：

Before:
```java
MessageUtil.sendMessage(player, String.format("&e岛屿保护 &f|&c 你没有&e %s &c权限！", permission.getDisplayName()));
```

After:
```java
MessageUtil.send(player, "permission.no-permission", Map.of("permission", permission.getDisplayName()));
```

若文件未引入 `java.util.Map`，添加 import。

- [ ] **Step 2: 迁移 PvpSettingManager.java**

按模板迁移 1 处调用。归入 `setting.pvp.*` 命名空间。

- [ ] **Step 3: 迁移 TeleportCountdownListener.java**

迁移第 44 行调用。

打开文件查看上下文：

```java
MessageUtil.sendMessage(player, successMessage);
```

观察 `successMessage` 变量来源（应为方法参数或局部变量）。若 `successMessage` 是动态构造的字面量（如来自其他配置或方法返回），保留为字面量发送 - 使用 `MessageUtil.sendMessage(player, successMessage)`（不迁移）。

若 `successMessage` 是固定常量或可参数化的字面量，迁移为 `MessageUtil.send(player, "teleport.countdown.success", ...)`。

**默认行为**：先打开文件确认 `successMessage` 来源。若是动态字面量，跳过此 Step；否则按下文迁移。

假设 `successMessage` 是固定字符串，YAML 追加：

```yaml
teleport:
  countdown:
    success: "&a传送成功！"
```

After:
```java
MessageUtil.send(player, "teleport.countdown.success");
```

- [ ] **Step 4: 编译验证**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 自检**

Run: `grep -rn "MessageUtil.sendMessage\|MessageUtil.broadcast" src/main/java/team/starm/starmskyblock/permission/ src/main/java/team/starm/starmskyblock/setting/manager/ src/main/java/team/starm/starmskyblock/listener/ | wc -l`
Expected: 0 或仅剩 TeleportCountdownListener.java 中动态字面量（如 Step 3 确认跳过）

- [ ] **Step 6: Commit**

```bash
git add src/main/java/team/starm/starmskyblock/permission/BasePermissionManager.java \
        src/main/java/team/starm/starmskyblock/setting/manager/PvpSettingManager.java \
        src/main/java/team/starm/starmskyblock/listener/TeleportCountdownListener.java \
        src/main/resources/messages/messages_zh_CN.yml
git commit -m "refactor(i18n): migrate permission + setting + listener (6 calls)"
```

---

## Task 10: 全量验证 + 最终自检

**Files:** 无（仅验证）

- [ ] **Step 1: 全量编译验证**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL，生成 `build/libs/StarMSkyblock.jar`

- [ ] **Step 2: 全量自检 - 整个代码库无遗漏**

Run: `grep -rn "MessageUtil.sendMessage\|MessageUtil.broadcast" src/main/java/team/starm/starmskyblock/ | wc -l`
Expected: 0

若非 0，列出剩余调用点：

Run: `grep -rn "MessageUtil.sendMessage\|MessageUtil.broadcast" src/main/java/team/starm/starmskyblock/`

逐个评估：若是控制台诊断、内部诊断、或 `MessageUtil.broadcast(String)` 旧 API（已废弃但保留），可保留并加注释说明；否则补迁移。

- [ ] **Step 3: 验证 i18n 系统启动**

启动服务器（或在 CI 中部署），检查控制台启动日志应包含：

```
[StarMSkyblock] 已加载 i18n 语言文件: zh_CN（N 个键）
```

其中 N 应大于 100（粗略估算约 400 处调用压缩后的键数）。

- [ ] **Step 4: 验证 i18n 重载**

服务器运行中执行：
```
/isadmin reload
```

控制台应输出：
```
[StarMSkyblock] i18n 已重载：locale=zh_CN
[StarMSkyblock] 所有配置文件已重载！(耗时 XXXms)
```

- [ ] **Step 5: 验证缺失 key 兜底**

临时修改一个调用点的 key 为不存在的值，例如把 `MessageUtil.send(player, "island.no-island")` 改为 `MessageUtil.send(player, "island.does-not-exist")`，重新编译部署，触发该命令路径：

- 玩家应看到字面 key 文本：`island.does-not-exist`
- 控制台应输出一次：`[StarMSkyblock] [WARN] i18n 缺失键: island.does-not-exist`

验证完毕后恢复该 key。

- [ ] **Step 6: 验证 locale 回退**

修改 `plugins/StarMSkyblock/config.yml` 中 `locale: 'xx_XX'`（不存在的语言代码），执行 `/isadmin reload`：

- 控制台应输出警告：`locale 格式非法（期望 xx_XX 如 zh_CN），使用默认 zh_CN`
- 玩家消息应仍正常显示（回退到 zh_CN）

验证完毕后恢复 `locale: 'zh_CN'`。

- [ ] **Step 7: 最终 Commit（如有自检发现的补充迁移）**

```bash
git add -A
git commit -m "chore(i18n): final self-check fixes"
```

若无补充修改，跳过此 Step。

---

## 完成标准

- [ ] 所有 Task 1-10 的 checkbox 全部勾选
- [ ] `./gradlew build` 通过
- [ ] `grep -rn "MessageUtil.sendMessage\|MessageUtil.broadcast" src/main/java/team/starm/starmskyblock/` 返回 0 或仅剩已确认的合理保留点
- [ ] 服务器启动日志包含 `已加载 i18n 语言文件: zh_CN`
- [ ] `/isadmin reload` 输出 `i18n 已重载：locale=zh_CN`
- [ ] 缺失 key 兜底正常（玩家看到字面 key + 控制台一次性警告）
- [ ] locale 回退正常（非法格式回退 zh_CN）

---

## 关键决策参考表

| 决策 | 选择 | 出处 |
|---|---|---|
| Locale 来源 | 全局统一（config.yml `locale` 字段） | spec §1 |
| 文件格式 | YAML | spec §1 |
| 内置语言 | 仅 `zh_CN` | spec §1 |
| 键命名 | 4 层 kebab-case | spec §2 |
| 占位符 | 命名式 `{name}` | spec §2 |
| 缺失 key 兜底 | 返回字面 key 文本 + 一次性 consoleWarn | spec §4 |
| 控制台日志 | 保留字面量（不迁移） | spec §1 |
| `sendMessage(String)` 旧 API | 保留（迁移后无调用方） | spec §1 |
| `LanguageManager` 访问 | `MessageUtil` 静态注入 | spec §3 |
| 重载命令 | 扩展现有 `/isadmin reload` | spec §3 |
| 迁移批次 | 6 批，按目录分组 | spec §5 |
