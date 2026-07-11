package team.starm.starmskyblock.util.reflection;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import team.starm.starmskyblock.message.MessageUtil;

import java.lang.reflect.Method;

import com.sk89q.worldedit.world.World;

/**
 * FastAsyncWorldEdit (FAWE) EditSession 构建器反射封装。
 * <p>
 * FAWE 提供比原版 WorldEdit 更快的异步方块写入，但其 {@code newEditSessionBuilder}
 * 构建器 API 仅存在于 FAWE 而非上游 WorldEdit。为避免对 FAWE 产生编译期依赖（本项目仅
 * compileOnly 依赖 WorldEdit），此处用反射在运行时探测并调用 FAWE 的构建器方法；
 * 任一方法缺失时回退到 {@link WorldEdit#newEditSession(World)} 标准 API。
 */
public class FaweReflection {

    /** 反射缓存的方法句柄：构建器 -> world() -> fastmode()/checkMemory() -> build()，
     *  对应 FAWE 的 EditSessionBuilder 链式 API；为 null 表示该方法在当前运行环境不存在。 */
    private Method newBuilderMethod;
    private Method builderWorldMethod;
    private Method builderFastmodeMethod;
    private Method builderCheckMemoryMethod;
    private Method builderBuildMethod;
    private Method flushQueueMethod;

    public FaweReflection() {
        initReflection();
    }

    /** 启动期一次性解析并缓存 FAWE 构建器各方法句柄，失败仅记录警告不影响插件启动。 */
    private void initReflection() {
        try {
            newBuilderMethod = WorldEdit.class.getMethod("newEditSessionBuilder");
            Object builder = newBuilderMethod.invoke(WorldEdit.getInstance());
            Class<?> builderClass = builder.getClass();

            builderWorldMethod = builderClass.getMethod("world", World.class);

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

    /**
     * 创建一个启用了 fastmode（跳过历史记录）的 FAWE EditSession。
     * <p>
     * 走 FAWE 构建器反射链；构建器不可用（FAWE 未安装）或反射失败时
     * 回退到 WorldEdit 标准 {@code newEditSession}。
     *
     * @param weWorld WorldEdit 抽象 World
     * @return 已配置的 EditSession
     */
    public EditSession createEditSession(World weWorld) {
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

    /**
     * 刷新 EditSession 使方块写入立即生效（FAWE 异步队列落盘）。
     * {@code flushQueue} 方法不存在时为空操作（原版 WorldEdit 在 EditSession.close 时即同步）。
     *
     * @param session 待刷新的 EditSession
     */
    public void flush(EditSession session) {
        if (flushQueueMethod != null) {
            try {
                flushQueueMethod.invoke(session);
            } catch (Exception ignored) {}
        }
    }

    /** FAWE 构建器反射是否可用（即运行环境装了 FAWE）。 */
    public boolean isAvailable() {
        return newBuilderMethod != null;
    }
}
