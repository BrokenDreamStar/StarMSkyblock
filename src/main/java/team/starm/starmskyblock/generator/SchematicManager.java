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

public class SchematicManager {

    private final File schematicFolder;
    private final Map<String, Clipboard> schematicCache = new ConcurrentHashMap<>();
    private final Map<String, List<BlockVector3>> signOffsetCache = new ConcurrentHashMap<>();
    private final boolean faweMode;
    private final boolean faweAvailable;

    private FaweReflection faweReflection;

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

    private static boolean isFaweInstalled() {
        try {
            Class.forName("com.fastasyncworldedit.core.Fawe");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @SuppressWarnings("deprecation")
    public Clipboard getSchematic(String fileName) {
        if (schematicCache.containsKey(fileName)) {
            return schematicCache.get(fileName);
        }

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

        if (bedrockLoc != null) {
            clipboard.setOrigin(bedrockLoc);
            List<BlockVector3> relativeSigns = new ArrayList<>(signPositions.size());
            for (BlockVector3 signPos : signPositions) {
                relativeSigns.add(signPos.subtract(bedrockLoc));
            }
            signOffsetCache.put(fileName, relativeSigns);
        } else {
            MessageUtil.consoleWarn("在结构文件 " + fileName + " 中未找到基岩方块！将使用默认原点进行粘贴。");
            signOffsetCache.put(fileName, signPositions);
        }

        schematicCache.put(fileName, clipboard);
        return clipboard;
    }

    /**
     * 尝试读取 schematic 文件，遇到 FAWE FastSchematicReaderV3 不兼容时自动回退
     * 到标准 Sponge v2 格式。
     */
    private Clipboard readSchematicWithFallback(File file, ClipboardFormat primaryFormat, String fileName) {
        // 首次尝试
        try (ClipboardReader reader = primaryFormat.getReader(new FileInputStream(file))) {
            return reader.read();
        } catch (NullPointerException e) {
            // FAWE FastSchematicReaderV3 可能因无法解析的方块类型抛出 NPE
            // 尝试回退到 Sponge（v2）格式
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

    @SuppressWarnings("removal")
    private static boolean isSignBlockType(BlockType type) {
        String id = type.getId();
        return id.contains("sign") || id.contains("SIGN");
    }

    public List<BlockVector3> getSignOffsets(String fileName) {
        getSchematic(fileName);
        return signOffsetCache.get(fileName);
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

    public boolean pasteSchematic(String fileName, World world, int x, int y, int z) {
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
                    .ignoreAirBlocks(false)
                    .build();

            Operations.complete(operation);
            flushIfFawe(editSession);
            return true;
        } catch (WorldEditException e) {
            MessageUtil.consoleError("粘贴岛屿结构时发生 WorldEdit 错误！", e);
            return false;
        }
    }

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
