package team.starm.starmskyblock.level;

/**
 * 岛屿等级计算的核心纯逻辑。
 * <p>
 * 原内联于 {@link IslandLevelCalculator} 的 {@code finishPhase()}，抽出为纯工具类以便单元测试。
 * 无 Bukkit 依赖。
 */
public final class LevelFormula {

    private LevelFormula() {
    }

    /**
     * 幂函数模型反推等级。
     * <p>
     * 单级消耗 {@code cost(L) = round(base * L^power)}，累计 {@code total(L) = Σ cost(i)} for i=1..L。
     * 从 L=1 递增累加，直到累计超过 {@code totalExp}，返回上一级。
     * <p>
     * 保护：当 {@code cost <= 0}（power 为负致 cost 趋 0，或 base 非法）时终止，避免无限循环。
     *
     * @param totalExp 岛屿总经验值
     * @param base     基础经验（level.yml level-cost.base）
     * @param power    指数（level.yml level-cost.power）
     * @return 等级（&gt;= 0）
     */
    public static int fromPowerCurve(double totalExp, double base, double power) {
        if (totalExp < Math.round(base)) {
            return 0;
        }
        double cumulative = 0;
        int lvl = 0;
        while (true) {
            lvl++;
            long cost = Math.round(base * Math.pow(lvl, power));
            if (cost <= 0) { // 防溢出/无限循环
                return lvl - 1;
            }
            cumulative += cost;
            if (cumulative > totalExp) {
                return lvl - 1;
            }
        }
    }

    /**
     * 超阈值方块的递减收益经验总和。
     * <p>
     * 对超额的每一块（i = 0..overLimit-1）累加 {@code round(max(expValue / (1 + decay * i), minimum))}。
     * i 从 0 起，故首块经验为 {@code round(max(expValue, minimum))}。
     *
     * @param overLimit 超出阈值的方块数
     * @param expValue  单块基础经验值
     * @param decay     衰减系数
     * @param minimum   单块经验下限
     * @return 超额部分总经验
     */
    public static double diminishingReturns(long overLimit, double expValue, double decay, double minimum) {
        double sum = 0;
        for (long i = 0; i < overLimit; i++) {
            sum += Math.round(Math.max(expValue / (1 + decay * i), minimum));
        }
        return sum;
    }
}
