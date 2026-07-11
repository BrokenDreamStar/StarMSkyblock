package team.starm.starmskyblock.command.subcommand;

import org.bukkit.command.CommandSender;
import team.starm.starmskyblock.StarMSkyblock;

import java.util.Collections;
import java.util.List;

/**
 * 管理员子命令抽象基类
 * <p>
 * 所有 {@code /isadmin} 子命令的公共父类，与 {@link SubCommand} 类似但执行者为
 * {@link CommandSender}（可由控制台触发），故不提供依赖玩家的岛屿查询/位置安全等工具方法。
 * 具体子命令实现 {@link #execute(CommandSender, String[])} 即可。
 */
public abstract class AdminSubCommand {

    protected final StarMSkyblock plugin;

    public AdminSubCommand(StarMSkyblock plugin) {
        this.plugin = plugin;
    }

    /**
     * 执行管理员子命令。
     *
     * @param sender 命令发送者（玩家或控制台）
     * @param args   去除主命令名后的参数数组（args[0] 为子命令名）
     * @return true 表示已处理
     */
    public abstract boolean execute(CommandSender sender, String[] args);

    /**
     * 子命令 Tab 补全，默认返回空列表。子类按需重写。
     */
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}
