package team.starm.starmskyblock.generator;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.block.BlockType;
import com.sk89q.worldedit.world.block.BlockTypes;
import org.bukkit.World;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import team.starm.starmskyblock.message.MessageUtil;
import team.starm.starmskyblock.util.reflection.FaweReflection;

/**
 * 结构文件管理器
 * <p>
 * 负责加载、缓存与粘贴 WorldEdit/FAWE schematic 文件（岛屿初始结构）。
 * 自动检测 FAWE 是否安装，FAWE 路径走异步高性能引擎，否则回退到标准 WorldEdit。
 * 加载时扫描结构中的基岩与告示牌位置，以基岩为原点将告示牌偏移打包进缓存条目，
 * 供岛屿创建时定位告示牌。读取遇到 FAWE 不兼容时自动回退到 Sponge v2 格式。
 * </p>
 */
public class SchematicManager {

    /** 结构文件存放目录 */
    private final File schematicFolder;
    /** 已加载结构缓存：文件名 -> clipboard + 告示牌偏移，原子化打包避免半加载态 */
    private final Map<String, SchematicEntry> schematicCache = new ConcurrentHashMap<>();
    /** 配置是否启用 FAWE 模式 */
    private final boolean faweMode;
    /** 服务器是否实际安装了 FAWE */
    private final boolean faweAvailable;

    /** FAWE 反射句柄，启用且可用时初始化 */
    private FaweReflection faweReflection;

    /** 结构文件缓存条目 —— 把 clipboard 与 signOffsets 打包成单一对象，确保两者要么同时可见要么同时不可见 */
    private static final class SchematicEntry {
        final Clipboard clipboard;
        final List<BlockVector3> signOffsets;

        SchematicEntry(Clipboard clipboard, List<BlockVector3> signOffsets) {
            this.clipboard = clipboard;
            this.signOffsets = signOffsets;
        }
    }

    /**
     * 构造管理器并初始化 FAWE 模式：确保结构目录存在、检测 FAWE 安装情况，
     * 启用且可用时创建 {@link FaweReflection} 句柄，否则回退到标准 WorldEdit 并告警。
     */
    public SchematicManager(File schematicFolder, boolean faweMode) {
        this.schematicFolder = schematicFolder;
        this.faweMode = faweMode;
        this.faweAvailable = isFaweInstalled();

        if (!schematicFolder.exists()) {
            schematicFolder.mkdirs();
        }

        if (faweMode && faweAvailable) {
            faweReflection = new FaweReflection();
            MessageUtil.consolePrint("FAWE 模式已启用 — 使用 FastAsyncWorldEdit 高性能引擎");
        } else if (faweMode && !faweAvailable) {
            MessageUtil.consoleWarn("配置为 FAWE 模式但服务器未安装 FAWE，已自动回退到标准 WorldEdit");
        }
    }

    /**
     * 指示当前是否启用 FAWE 异步引擎。非 FAWE 路径调用 {@link EditSession} /
     * {@link Operations#complete} 会从调用线程直接改 chunk —— 异步线程调用会导致区块损坏，
     * 调用方必须据此把操作调度回主线程。
     */
    public boolean isFaweActive() {
        return faweMode && faweAvailable && faweReflection != null && faweReflection.isAvailable();
    }

    /** 通过反射检测服务器是否安装了 FAWE（避免编译期硬依赖） */
    private static boolean isFaweInstalled() {
        try {
            Class.forName("com.fastasyncworldedit.core.Fawe");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /** 获取结构文件的 clipboard，未加载或加载失败时返回 null */
    @SuppressWarnings("deprecation")
    public Clipboard getSchematic(String fileName) {
        SchematicEntry entry = getEntry(fileName);
        return entry == null ? null : entry.clipboard;
    }

    /** 获取结构文件中告示牌相对基岩原点的偏移量列表，供岛屿创建后定位告示牌 */
    public List<BlockVector3> getSignOffsets(String fileName) {
        SchematicEntry entry = getEntry(fileName);
        return entry == null ? null : entry.signOffsets;
    }

    /**
     * 原子地获取结构缓存条目。{@link #schematicCache} 单独维护 clipboard 与 signOffsets，
     * 原实现两次独立 {@code put} 让 {@code getSignOffsets} 可观察 schematic 已缓存但 offset 为空 → NPE/漏 sign。
     * 改用 {@link ConcurrentHashMap#computeIfAbsent} 串行化加载，二者打包为 {@link SchematicEntry}。
     */
    private SchematicEntry getEntry(String fileName) {
        SchematicEntry cached = schematicCache.get(fileName);
        if (cached != null) return cached;
        return schematicCache.computeIfAbsent(fileName, this::loadEntry);
    }

    /**
     * 从磁盘加载结构文件并构造缓存条目。
     * <p>扫描结构底部 6 层方块，定位首个基岩作为粘贴原点并收集所有告示牌位置，
     * 将告示牌转为相对基岩的偏移量。未找到基岩时使用默认原点并告警。</p>
     */
    @SuppressWarnings("deprecation")
    private SchematicEntry loadEntry(String fileName) {
        File file = new File(schematicFolder, fileName);
        if (!file.exists()) {
            MessageUtil.consoleWarn("找不到岛屿结构文件: " + file.getAbsolutePath());
            return null;
        }

        ClipboardFormat format = ClipboardFormats.findByFile(file);
        if (format == null) {
            MessageUtil.consoleWarn("无法识别的结构文件格式: " + fileName);
            return null;
        }

        Clipboard clipboard = readSchematicWithFallback(file, format, fileName);
        if (clipboard == null) {
            return null;
        }

        BlockVector3 min = clipboard.getRegion().getMinimumPoint();
        BlockVector3 max = clipboard.getRegion().getMaximumPoint();
        int minY = min.y();

        BlockVector3 bedrockLoc = null;
        List<BlockVector3> signPositions = new ArrayList<>();

        for (int y = minY; y <= Math.min(minY + 5, max.y()); y++) {
            for (int x = min.x(); x <= max.x(); x++) {
                for (int z = min.z(); z <= max.z(); z++) {
                    BlockVector3 pos = BlockVector3.at(x, y, z);
                    BlockType type = clipboard.getBlock(pos).getBlockType();
                    if (type.equals(BlockTypes.BEDROCK) && bedrockLoc == null) {
                        bedrockLoc = pos;
                    }
                    if (isSignBlockType(type)) {
                        signPositions.add(pos);
                    }
                }
            }
        }

        if (bedrockLoc == null) {
            for (BlockVector3 pos : clipboard.getRegion()) {
                BlockType type = clipboard.getBlock(pos).getBlockType();
                if (type.equals(BlockTypes.BEDROCK)) {
                    bedrockLoc = pos;
                    break;
                }
            }
        }

        List<BlockVector3> signOffsets;
        if (bedrockLoc != null) {
            clipboard.setOrigin(bedrockLoc);
            signOffsets = new ArrayList<>(signPositions.size());
            for (BlockVector3 signPos : signPositions) {
                signOffsets.add(signPos.subtract(bedrockLoc));
            }
        } else {
            MessageUtil.consoleWarn("在结构文件 " + fileName + " 中未找到基岩方块！将使用默认原点进行粘贴。");
            signOffsets = signPositions;
        }

        return new SchematicEntry(clipboard, signOffsets);
    }

    /**
     * 尝试读取 schematic 文件，遇到 FAWE FastSchematicReaderV3 不兼容时自动回退
     * 到标准 Sponge v2 格式。
     */
    private Clipboard readSchematicWithFallback(File file, ClipboardFormat primaryFormat, String fileName) {
        // 首次尝试：FAWE FastSchematicReaderV3 在遇到无法解析的方块类型时可能抛 NPE，
        // 此处捕获并显式记录一行警告后回退到 Sponge（v2）格式，避免静默吞掉
        // 与 schematic 无关的 NPE 造成排障困难。
        try (ClipboardReader reader = primaryFormat.getReader(new FileInputStream(file))) {
            Clipboard clipboard = reader.read();
            if (clipboard == null) {
                MessageUtil.consoleWarn("读取结构文件返回 null，将尝试回退格式: " + fileName);
            } else {
                return clipboard;
            }
        } catch (NullPointerException e) {
            MessageUtil.consoleWarn("读取结构文件时遇到 NPE（疑似 FAWE 不兼容的方块类型），将尝试回退: "
                    + fileName + " - " + e.getMessage());
        } catch (IOException e) {
            MessageUtil.consoleError("读取结构文件时发生错误: " + fileName, e);
            return null;
        }

        // 回退：用 Sponge 格式重试
        ClipboardFormat spongeFormat = ClipboardFormats.findByAlias("sponge");
        if (spongeFormat != null && !spongeFormat.getName().equals(primaryFormat.getName())) {
            try (ClipboardReader reader = spongeFormat.getReader(new FileInputStream(file))) {
                MessageUtil.consoleWarn("FAWE 格式读取失败，已自动回退到 Sponge 格式: " + fileName);
                return reader.read();
            } catch (Exception fallbackException) {
                MessageUtil.consoleError("FAWE 格式和 Sponge 回退格式均无法读取结构文件: " + fileName, fallbackException);
            }
        } else {
            MessageUtil.consoleError("FAWE 格式读取失败且无可用回退格式: " + fileName);
        }
        return null;
    }

    /** 判断方块类型是否为告示牌（按 id 中是否含 sign/SIGN 判定，兼容各木种告示牌） */
    @SuppressWarnings("removal")
    private static boolean isSignBlockType(BlockType type) {
        String id = type.getId();
        return id.contains("sign") || id.contains("SIGN");
    }

    /**
     * 创建 EditSession，FAWE 模式下启用 fastmode + 跳过内存检查。
     */
    private EditSession createEditSession(com.sk89q.worldedit.world.World weWorld) {
        if (faweMode && faweAvailable && faweReflection != null && faweReflection.isAvailable()) {
            return faweReflection.createEditSession(weWorld);
        }
        return WorldEdit.getInstance().newEditSession(weWorld);
    }

    /**
     * FAWE 模式下显式刷新队列，确保所有变更被提交。
     */
    private void flushIfFawe(EditSession session) {
        if (faweMode && faweAvailable && faweReflection != null) {
            faweReflection.flush(session);
        }
    }

    /** 粘贴结构文件（保留空气方块，即仅覆盖非空气位置） */
    public boolean pasteSchematic(String fileName, World world, int x, int y, int z) {
        return pasteSchematic(fileName, world, x, y, z, false);
    }

    /**
     * 粘贴结构文件到指定坐标。
     * <p>FAWE 模式走异步引擎，标准模式走同步 EditSession；粘贴完成后刷新 FAWE 队列确保提交。</p>
     *
     * @param fileName         结构文件名
     * @param world            目标 Bukkit 世界
     * @param x y z            粘贴原点坐标
     * @param ignoreAirBlocks  是否跳过空气方块（true 时不覆盖已有非空气方块）
     * @return 粘贴成功返回 true，结构不存在或发生异常返回 false
     */
    public boolean pasteSchematic(String fileName, World world, int x, int y, int z, boolean ignoreAirBlocks) {
        Clipboard clipboard = getSchematic(fileName);
        if (clipboard == null) {
            return false;
        }

        com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(world);

        try (EditSession editSession = createEditSession(weWorld)) {
            @SuppressWarnings("resource")
            Operation operation = new ClipboardHolder(clipboard)
                    .createPaste(editSession)
                    .to(BlockVector3.at(x, y, z))
                    .ignoreAirBlocks(ignoreAirBlocks)
                    .build();

            Operations.complete(operation);
            flushIfFawe(editSession);
            return true;
        } catch (WorldEditException e) {
            MessageUtil.consoleError("粘贴岛屿结构时发生 WorldEdit 错误！", e);
            return false;
        }
    }

    /**
     * 计算结构在指定粘贴点处的实际方块边界。
     * <p>以结构 origin 为基准换算 min/max 坐标，供等级系统基线扫描确定区域。</p>
     *
     * @return {minX, minY, minZ, maxX, maxY, maxZ}，结构不存在时返回 null
     */
    public int[] getSchematicBounds(String fileName, int pasteX, int pasteY, int pasteZ) {
        Clipboard clipboard = getSchematic(fileName);
        if (clipboard == null) {
            return null;
        }

        BlockVector3 origin = clipboard.getOrigin();
        BlockVector3 min = clipboard.getRegion().getMinimumPoint();
        BlockVector3 max = clipboard.getRegion().getMaximumPoint();

        int minX = min.x() - origin.x() + pasteX;
        int minY = min.y() - origin.y() + pasteY;
        int minZ = min.z() - origin.z() + pasteZ;
        int maxX = max.x() - origin.x() + pasteX;
        int maxY = max.y() - origin.y() + pasteY;
        int maxZ = max.z() - origin.z() + pasteZ;

        return new int[]{minX, minY, minZ, maxX, maxY, maxZ};
    }

    /**
     * 用空气清空指定立方体区域内的所有方块。
     * <p>岛屿删除时调用以清除结构，FAWE 模式下走异步引擎。</p>
     *
     * @return 成功返回 true，发生异常返回 false
     */
    @SuppressWarnings("null")
    public boolean clearArea(World world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(world);
        try (EditSession editSession = createEditSession(weWorld)) {
            CuboidRegion region = new CuboidRegion(weWorld, BlockVector3.at(minX, minY, minZ),
                    BlockVector3.at(maxX, maxY, maxZ));
            editSession.setBlocks((Region) region, BlockTypes.AIR.getDefaultState());
            flushIfFawe(editSession);
            return true;
        } catch (Exception e) {
            MessageUtil.consoleError("清空区域方块时发生 WorldEdit 错误！", e);
            return false;
        }
    }
}
