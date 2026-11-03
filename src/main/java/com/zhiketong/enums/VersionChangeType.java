package com.zhiketong.enums;

/**
 * 知识词条版本变更类型（kb_entry_version.change_type）。
 *
 * @author 智客通开发组
 */
public enum VersionChangeType {

    /** 新建词条 */
    CREATE("新建"),

    /** 编辑词条 */
    UPDATE("编辑"),

    /** 启用/停用 */
    STATUS("启停"),

    /** 删除词条 */
    DELETE("删除"),

    /** 回滚到历史版本 */
    ROLLBACK("回滚"),

    /** 知识包导入 */
    IMPORT("导入");

    private final String description;

    VersionChangeType(String description) {
        this.description = description;
    }

    public String getCode() {
        return name();
    }

    public String getDescription() {
        return description;
    }
}
