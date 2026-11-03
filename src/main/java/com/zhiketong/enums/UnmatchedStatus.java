package com.zhiketong.enums;

/**
 * 未命中问题处理状态（kb_unmatched_question.status）。
 *
 * @author 智客通开发组
 */
public enum UnmatchedStatus {

    /** 待处理 */
    PENDING("待处理"),

    /** 已转词条 */
    CONVERTED("已转词条"),

    /** 已忽略（如闲聊、无业务价值的问题） */
    IGNORED("已忽略");

    private final String description;

    UnmatchedStatus(String description) {
        this.description = description;
    }

    public String getCode() {
        return name();
    }

    public String getDescription() {
        return description;
    }

    /**
     * 按编码解析，非法值返回 null。
     */
    public static UnmatchedStatus fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (UnmatchedStatus status : values()) {
            if (status.name().equalsIgnoreCase(code.trim())) {
                return status;
            }
        }
        return null;
    }
}
