package com.zhiketong.enums;

/**
 * 质检等级，按得分区间映射，对应 quality_record.grade。
 *
 * <p>区间口径固定：A ≥ 90、B ≥ 80、C ≥ 70、D &lt; 70；
 * 是否合格另由合格线 {@code pass_score}（默认 80）判定，与等级解耦。</p>
 *
 * @author 智客通开发组
 */
public enum QualityGrade {

    /** 优秀 */
    A("A", "优秀", 90),

    /** 良好 */
    B("B", "良好", 80),

    /** 待改进 */
    C("C", "待改进", 70),

    /** 不合格 */
    D("D", "不合格", 0);

    private final String code;

    private final String description;

    /** 该等级的最低得分（含） */
    private final int minScore;

    QualityGrade(String code, String description, int minScore) {
        this.code = code;
        this.description = description;
        this.minScore = minScore;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public int getMinScore() {
        return minScore;
    }

    /**
     * 按得分推导等级，得分会先收敛到 0-100。
     *
     * @param score 质检得分
     * @return 对应等级，不会为 null
     */
    public static QualityGrade of(Integer score) {
        int value = score == null ? 0 : Math.max(0, Math.min(100, score));
        for (QualityGrade grade : values()) {
            if (value >= grade.minScore) {
                return grade;
            }
        }
        return D;
    }

    /**
     * 根据编码获取枚举，未匹配或入参为 null 时返回 null。
     *
     * @param code 等级编码
     * @return 匹配的枚举，可能为 null
     */
    public static QualityGrade fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (QualityGrade value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }
}
