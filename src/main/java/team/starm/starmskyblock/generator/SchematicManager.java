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
import com.sk89q.worldedit.world.block.BlockTypes;
import org.bukkit.World;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class SchematicManager {

    private final File schematicFolder;
    private final Logger logger;
    private final Map<String, Clipboard> schematicCache = new HashMap<>();

    public SchematicManager(File schematicFolder, Logger logger) {
        this.schematicFolder = schematicFolder;
        this.logger = logger;

        if (!schematicFolder.exists()) {
            schematicFolder.mkdirs();
        }
    }

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

            BlockVector3 bedrockLoc = null;
            for (BlockVector3 pos : clipboard.getRegion()) {
                if (clipboard.getBlock(pos).getBlockType().equals(BlockTypes.BEDROCK)) {
                    bedrockLoc = pos;
                    break;
                }
            }

            if (bedrockLoc != null) {
                clipboard.setOrigin(bedrockLoc);
            } else {
                logger.warning("在结构文件 " + fileName + " 中未找到基岩方块！将使用默认原点进行粘贴。");
            }

            schematicCache.put(fileName, clipboard);
            return clipboard;
        } catch (IOException e) {
            logger.severe("读取结构文件时发生错误: " + fileName);
            e.printStackTrace();
            return null;
        }
    }

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
