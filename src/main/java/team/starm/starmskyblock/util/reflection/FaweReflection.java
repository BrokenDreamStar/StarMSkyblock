package team.starm.starmskyblock.util.reflection;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import team.starm.starmskyblock.message.MessageUtil;

import java.lang.reflect.Method;

public class FaweReflection {

    private Method newBuilderMethod;
    private Method builderWorldMethod;
    private Method builderFastmodeMethod;
    private Method builderCheckMemoryMethod;
    private Method builderBuildMethod;
    private Method flushQueueMethod;

    public FaweReflection() {
        initReflection();
    }

    private void initReflection() {
        try {
            newBuilderMethod = WorldEdit.class.getMethod("newEditSessionBuilder");
            Object builder = newBuilderMethod.invoke(WorldEdit.getInstance());
            Class<?> builderClass = builder.getClass();

            builderWorldMethod = builderClass.getMethod("world", com.sk89q.worldedit.world.World.class);

            try {
                builderFastmodeMethod = builderClass.getMethod("fastmode", boolean.class);
            } catch (NoSuchMethodException ignored) {}

            try {
                builderCheckMemoryMethod = builderClass.getMethod("checkMemory", boolean.class);
            } catch (NoSuchMethodException ignored) {}

            builderBuildMethod = builderClass.getMethod("build");

            try {
                flushQueueMethod = EditSession.class.getMethod("flushQueue");
            } catch (NoSuchMethodException ignored) {}
        } catch (Exception e) {
            MessageUtil.consoleWarn("FAWE 反射初始化失败: " + e.getMessage());
        }
    }

    public EditSession createEditSession(com.sk89q.worldedit.world.World weWorld) {
        if (newBuilderMethod == null) {
            return WorldEdit.getInstance().newEditSession(weWorld);
        }
        try {
            Object builder = newBuilderMethod.invoke(WorldEdit.getInstance());
            builder = builderWorldMethod.invoke(builder, weWorld);
            if (builderFastmodeMethod != null) {
                builder = builderFastmodeMethod.invoke(builder, true);
            }
            if (builderCheckMemoryMethod != null) {
                builder = builderCheckMemoryMethod.invoke(builder, false);
            }
            return (EditSession) builderBuildMethod.invoke(builder);
        } catch (Exception e) {
            return WorldEdit.getInstance().newEditSession(weWorld);
        }
    }

    public void flush(EditSession session) {
        if (flushQueueMethod != null) {
            try {
                flushQueueMethod.invoke(session);
            } catch (Exception ignored) {}
        }
    }

    public boolean isAvailable() {
        return newBuilderMethod != null;
    }
}
