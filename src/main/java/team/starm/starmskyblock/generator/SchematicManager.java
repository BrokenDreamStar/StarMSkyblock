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
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.block.BlockTypes;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import org.bukkit.World;
import team.starm.starmskyblock.StarMSkyblock;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class SchematicManager {

    private final StarMSkyblock plugin;
    private final File schematicFolder;
    private final Map<String, Clipboard> schematicCache = new HashMap<>();

    public SchematicManager(StarMSkyblock plugin) {
        this.plugin = plugin;
        this.schematicFolder = new File(plugin.getDataFolder(), "schematics");

        if (!schematicFolder.exists()) {
            schematicFolder.mkdirs();
        }
    }

    /**
     * 预加载或者获取缓存的 Schematic，并将原点(Origin)设置在基岩方块的位置
     */
    public Clipboard getSchematic(String fileName) {
        if (schematicCache.containsKey(fileName)) {
            return schematicCache.get(fileName);
        }

        File file = new File(schematicFolder, fileName);
        if (!file.exists()) {
            plugin.getLogger().warning("§c找不到岛屿结构文件: " + file.getAbsolutePath());
            return null;
        }

        ClipboardFormat format = ClipboardFormats.findByFile(file);
        if (format == null) {
            plugin.getLogger().warning("§c无法识别的结构文件格式: " + fileName);
            return null;
        }

        try (ClipboardReader reader = format.getReader(new FileInputStream(file))) {
            Clipboard clipboard = reader.read();

            // 遍历结构寻找基岩方块
            BlockVector3 bedrockLoc = null;
            for (BlockVector3 pos : clipboard.getRegion()) {
                if (clipboard.getBlock(pos).getBlockType().equals(BlockTypes.BEDROCK)) {
                    bedrockLoc = pos;
                    break;
                }
            }

            if (bedrockLoc != null) {
                // 设置结构的粘贴原点为基岩方块位置
                clipboard.setOrigin(bedrockLoc);
            } else {
                plugin.getLogger().warning("§e在结构文件 " + fileName + " 中未找到基岩方块！将使用默认原点进行粘贴。");
            }

            schematicCache.put(fileName, clipboard);
            return clipboard;
        } catch (IOException e) {
            plugin.getLogger().severe("§c读取结构文件时发生错误: " + fileName);
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 将结构文件粘贴到指定世界的坐标点
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
                    .ignoreAirBlocks(false) // 是否忽略空气（覆盖虚空时一般不忽略空气，但虚空本身是空气所以无所谓）
                    .build();

            Operations.complete(operation);
            return true;
        } catch (WorldEditException e) {
            plugin.getLogger().severe("§c粘贴岛屿结构时发生 FAWE 错误！");
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 使用 FAWE 快速清空指定区域的方块
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
            plugin.getLogger().severe("§c清空区域方块时发生 FAWE 错误！");
            e.printStackTrace();
            return false;
        }
    }
}
