package team.starm.starmskyblock.level;

/**
 * 简易算术表达式解析器（递归下降）。
 * <p>
 * 支持 {@code + - * /} 四则运算、括号嵌套与十进制小数，运算符优先级：
 * 加减 &lt; 乘除 &lt; 括号。空白字符在解析前被去除。
 * <p>
 * 原为 {@link IslandLevelCalculator} 内部实现，抽出为纯工具类以便单元测试。
 * 无 Bukkit 依赖，可在无服务端环境下测试。
 */
public final class ExpressionParser {

    private ExpressionParser() {
    }

    /**
     * 计算算术表达式的值。
     *
     * @param expr 表达式字符串，如 {@code "2 + 3 * 4"} 或 {@code "(1+2)*3"}
     * @return 计算结果；空串或无法解析的尾部返回 0（保持原行为）
     */
    public static double evaluate(String expr) {
        expr = expr.trim().replaceAll("\\s+", "");
        return parseAddSub(expr, new int[]{0});
    }

    private static double parseAddSub(String expr, int[] pos) {
        double left = parseMulDiv(expr, pos);
        while (pos[0] < expr.length()) {
            char op = expr.charAt(pos[0]);
            if (op == '+' || op == '-') {
                pos[0]++;
                double right = parseMulDiv(expr, pos);
                left = (op == '+') ? left + right : left - right;
            } else {
                break;
            }
        }
        return left;
    }

    private static double parseMulDiv(String expr, int[] pos) {
        double left = parsePrimary(expr, pos);
        while (pos[0] < expr.length()) {
            char op = expr.charAt(pos[0]);
            if (op == '*' || op == '/') {
                pos[0]++;
                double right = parsePrimary(expr, pos);
                left = (op == '*') ? left * right : left / right;
            } else {
                break;
            }
        }
        return left;
    }

    private static double parsePrimary(String expr, int[] pos) {
        if (pos[0] >= expr.length()) return 0;
        char c = expr.charAt(pos[0]);
        if (c == '(') {
            pos[0]++;
            double val = parseAddSub(expr, pos);
            if (pos[0] < expr.length() && expr.charAt(pos[0]) == ')') pos[0]++;
            return val;
        }
        // 数字
        int start = pos[0];
        while (pos[0] < expr.length() && (Character.isDigit(expr.charAt(pos[0])) || expr.charAt(pos[0]) == '.')) {
            pos[0]++;
        }
        if (start == pos[0]) return 0;
        return Double.parseDouble(expr.substring(start, pos[0]));
    }
}
