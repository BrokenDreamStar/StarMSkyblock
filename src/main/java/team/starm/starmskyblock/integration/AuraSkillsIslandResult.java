package team.starm.starmskyblock.integration;

import java.util.Collections;
import java.util.List;

/**
 * 岛屿 AuraSkills 等级加成计算结果。
 * <p>
 * 包含全体成员 PowerLevel 总和以及每个成员的逐人明细。
 */
public class AuraSkillsIslandResult {

    private final int totalPowerLevel;
    private final List<MemberSkillData> memberData;

    public AuraSkillsIslandResult(int totalPowerLevel, List<MemberSkillData> memberData) {
        this.totalPowerLevel = totalPowerLevel;
        this.memberData = memberData;
    }

    public int getTotalPowerLevel() {
        return totalPowerLevel;
    }

    public List<MemberSkillData> getMemberData() {
        return Collections.unmodifiableList(memberData);
    }
}