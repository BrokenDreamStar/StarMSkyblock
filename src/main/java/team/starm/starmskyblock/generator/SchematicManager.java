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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * 结构文件（.schem）管理器 —— 基于 WorldEdit API 实现岛屿结构的加载、缓存和粘贴。
 * <p>
 * 职责：
 * <ul>
 *   <li>从磁盘加载 .schem 文件并缓存为 Clipboard 对象</li>
 *   <li>自动检测结构中的基岩方块作为粘贴原点校准</li>
 *   <li>缓存告示牌相对偏移供后续文字更新</li>
 *   <li>提供区域清空能力（删除岛屿时使用）</li>
 * </ul>
 */
public class SchematicManager {

    /** 结构文件存放目录（<dataFolder>/schematics/） */
    private final File schematicFolder;
    private final Logger logger;
    /** 已解析的 Clipboard 缓存（文件名 → Clipboard），避免重复解析 */
    private final Map<String, Clipboard> schematicCache = new HashMap<>();
    /** 告示牌相对于基岩原点的偏移缓存（文件名 → 偏移列表） */
    private final Map<String, List<BlockVector3>> signOffsetCache = new HashMap<>();

    public SchematicManager(File schematicFolder, Logger logger) {
        this.schematicFolder = schematicFolder;
        this.logger = logger;

        if (!schematicFolder.exists()) {
            schematicFolder.mkdirs();
        }
    }

    /**
     * 获取（或加载并缓存）指定结构文件的 Clipboard 对象。
     * 加载时自动检测基岩方块位置设为粘贴原点，同时收集告示牌偏移。
     *
     * @param fileName 结构文件名（如 "default.schem"）
     * @return Clipboard 对象，文件不存在或格式错误则返回 null
     */
    public Clipboard getSchematic(String fileName) {
        if (schematicCache.containsKey(fileName)) {
            return schematicCache.get(fileName);
        }

        File file = new File(schematicFolder, fileName);
        if (!file.exists()) {
            logger.warning("找不到岛屿结构文件: " + file.getAbsolutePath());
            return null;
        }

        ClipboardFormat format = ClipboardFormats.findByFile(file);
        if (format == null) {
            logger.warning("无法识别的结构文件格式: " + fileName);
            return null;
        }

        try (ClipboardReader reader = format.getReader(new FileInputStream(file))) {
            Clipboard clipboard = reader.read();

            BlockVector3 min = clipboard.getRegion().getMinimumPoint();
            BlockVector3 max = clipboard.getRegion().getMaximumPoint();
            int minY = min.y();

            BlockVector3 bedrockLoc = null;
            List<BlockVector3> signPositions = new ArrayList<>();

            // 先只在底部几层快速查找基岩（基岩通常在建筑底部）
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

            // 如果在底部几层没找到基岩, 全量扫描
            if (bedrockLoc == null) {
                for (BlockVector3 pos : clipboard.getRegion()) {
                    BlockType type = clipboard.getBlock(pos).getBlockType();
                    if (type.equals(BlockTypes.BEDROCK)) {
                        bedrockLoc = pos;
                        break;
                    }
                }
            }

            if (bedrockLoc != null) {
                clipboard.setOrigin(bedrockLoc);
                // 将告示牌位置转为相对基岩的偏移
                List<BlockVector3> relativeSigns = new ArrayList<>(signPositions.size());
                for (BlockVector3 signPos : signPositions) {
                    relativeSigns.add(signPos.subtract(bedrockLoc));
                }
                signOffsetCache.put(fileName, relativeSigns);
            } else {
                logger.warning("在结构文件 " + fileName + " 中未找到基岩方块！将使用默认原点进行粘贴。");
                signOffsetCache.put(fileName, signPositions);
            }

            schematicCache.put(fileName, clipboard);
            return clipboard;
        } catch (IOException e) {
            logger.severe("读取结构文件时发生错误: " + fileName);
            e.printStackTrace();
            return null;
        }
    }

    /** 判断方块类型是否为告示牌（通过 ID 字符串匹配） */
    @SuppressWarnings("removal")
    private static boolean isSignBlockType(BlockType type) {
        String id = type.getId();
        return id.contains("sign") || id.contains("SIGN");
    }

    /**
     * 获取指定结构文件中所有告示牌相对于基岩原点的偏移列表。
     * 如结构尚未加载则触发加载。
     */
    public List<BlockVector3> getSignOffsets(String fileName) {
        getSchematic(fileName);
        return signOffsetCache.get(fileName);
    }

    /**
     * 将指定结构文件粘贴到世界中的目标坐标位置。
     * 使用 ClipboardHolder 的 createPaste 并忽略空气方块过滤（保留结构中所有方块）。
     *
     * @param fileName 结构文件名
     * @param world    Bukkit 世界
     * @param x, y, z 粘贴基准点（基岩所在位置）
     * @return true 粘贴成功
     */
    public boolean pasteSchematic(String fileName, World world, int x, int y, int z) {
        Clipboard clipboard = getSchematic(fileName);
        if (clipboard == null) {
            return false;
        }

        com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(world);

        try (EditSession editSession = WorldEdit.getInstance().newEditSession(weWorld)) {
            @SuppressWarnings("resource")
            Operation operation = new ClipboardHolder(clipboard)
                    .createPaste(editSession)
                    .to(BlockVector3.at(x, y, z))
                    .ignoreAirBlocks(false)
                    .build();

            Operations.complete(operation);
            return true;
        } catch (WorldEditException e) {
            logger.severe("粘贴岛屿结构时发生 WorldEdit 错误！");
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 获取结构文件粘贴后在世界的边界坐标
     *
     * @param fileName 结构文件名
     * @param pasteX   粘贴基准 X
     * @param pasteY   粘贴基准 Y
     * @param pasteZ   粘贴基准 Z
     * @return int[6] = {minX, minY, minZ, maxX, maxY, maxZ}，若结构不存在则返回 null
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
     * 清空世界指定立方体区域内的所有方块（变为空气）。
     * 用于删除岛屿时清理残留建筑方块。
     */
    @SuppressWarnings("null")
    public boolean clearArea(World world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(world);
        try (EditSession editSession = WorldEdit.getInstance().newEditSession(weWorld)) {
            CuboidRegion region = new CuboidRegion(weWorld, BlockVector3.at(minX, minY, minZ),
                    BlockVector3.at(maxX, maxY, maxZ));
            editSession.setBlocks((Region) region, BlockTypes.AIR.getDefaultState());
            return true;
        } catch (Exception e) {
            logger.severe("清空区域方块时发生 WorldEdit 错误！");
            e.printStackTrace();
            return false;
        }
    }
}
