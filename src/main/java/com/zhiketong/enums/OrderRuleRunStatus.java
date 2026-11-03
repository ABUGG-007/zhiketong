package com.zhiketong.enums;

/**
 * 工单规则执行状态枚举，对应 order_rule_run.status（S3-C）。
 *
 * <p>立即动作直接落 {@link #DONE}/{@link #FAILED}/{@link #SKIPPED} 留痕；延迟与时间驱动动作先落
 * {@link #PENDING}，由扫描任务到期执行后回写终态。</p>
 *
 * @author 智客通开发组
 */
public enum OrderRuleRunStatus {

    /** 待执行 */
    PENDING("PENDING", "待执行"),

    /** 执行成功 */
    DONE("DONE", "执行成功"),

    /** 执行失败 */
    FAILED("FAILED", "执行失败"),

    /** 已跳过（如无可用在岗坐席） */
    SKIPPED("SKIPPED", "已跳过");

    private final String code;

    private final String description;

    OrderRuleRunStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 根据编码获取可读描述，未匹配返回原编码。
     *
     * @param code 状态编码
     * @return 状态描述
     */
    public static String labelOf(String code) {
        if (code == null) {
            return null;
        }
        for (OrderRuleRunStatus value : values()) {
            if (value.code.equals(code)) {
                return value.description;
            }
        }
        return code;
    }
}
