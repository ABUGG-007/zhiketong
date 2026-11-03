package com.zhiketong.dto.orderform;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 工单表单配置（R-126）：新建工单弹窗所需的启用字段、启用模板与问题分类。
 *
 * @author 智客通开发组
 */
@Data
public class OrderFormVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 启用状态的自定义字段 */
    private List<OrderFieldVO> fields = new ArrayList<>();

    /** 启用状态的工单模板 */
    private List<OrderTemplateVO> templates = new ArrayList<>();

    /** 可选问题分类 */
    private List<String> categories = new ArrayList<>();

    /** 字段类型字典 */
    private List<FieldTypeOption> fieldTypes = new ArrayList<>();

    /**
     * 字段类型字典项。
     */
    @Data
    public static class FieldTypeOption implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 类型编码 */
        private String code;

        /** 类型名称 */
        private String label;

        public FieldTypeOption() {
        }

        public FieldTypeOption(String code, String label) {
            this.code = code;
            this.label = label;
        }
    }
}
