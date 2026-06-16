package team.starm.starmskyblock.integration;

/**
 * 岛屿成员 AuraSkills 技能数据记录。
 * <p>
 * 保存每个成员的 PowerLevel 贡献明细，用于在等级结果中逐人显示。
 *
 * @param playerName  玩家名
 * @param powerLevel  该成员的 AuraSkills PowerLevel
 */
public record MemberSkillData(String playerName, int powerLevel) {
}