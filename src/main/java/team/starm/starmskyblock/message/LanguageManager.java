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
        MessageUtil.consolePrint("i18n 系统已就绪 | 已加载语言文件: " + this.locale + "（" + flat.size() + " 个键）");
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
        InputStream in = plugin.getResource(MESSAGES_DIR + "/" + MESSAGES_FILE_PREFIX + locale + MESSAGES_FILE_SUFFIX);
        if (in == null) {
            return null;
        }
        try (InputStream resource = in;
             java.io.InputStreamReader reader = new java.io.InputStreamReader(resource, java.nio.charset.StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
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
            if (!(value instanceof String)) {
                continue;
            }
            result.put(key, (String) value);
        }
        return result;
    }
}
