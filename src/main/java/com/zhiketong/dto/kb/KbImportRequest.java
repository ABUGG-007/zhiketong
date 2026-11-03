package com.zhiketong.dto.kb;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 行业知识包导入请求（R-85）。
 * <p>
 * 用例：新客户冷启动时，把行业通用问答按分类一次性导入，
 * 分类不存在时自动创建（顶级分类），词条按「分类 + 标准问题」去重。
 *
 * @author 智客通开发组
 */
@Data
public class KbImportRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 知识包名称，用于批次记录展示 */
    @NotBlank(message = "知识包名称不能为空")
    @Size(max = 128, message = "知识包名称不能超过 128 个字符")
    private String packageName;

    /** 词条列表，单次最多 500 条 */
    @NotEmpty(message = "词条不能为空")
    @Size(max = 500, message = "单次最多导入 500 条词条")
    @Valid
    private List<Item> entries;

    /**
     * 单条导入词条。
     */
    @Data
    public static class Item implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 分类名称，不存在时自动创建 */
        @NotBlank(message = "分类名称不能为空")
        @Size(max = 64, message = "分类名称不能超过 64 个字符")
        private String categoryName;

        /** 标准问题 */
        @NotBlank(message = "标准问题不能为空")
        @Size(min = 2, max = 255, message = "标准问题长度须为 2-255 个字符")
        private String question;

        /** 标准答案 */
        @NotBlank(message = "标准答案不能为空")
        @Size(min = 2, max = 2000, message = "标准答案长度须为 2-2000 个字符")
        private String answer;

        /** 关键词列表，可为空 */
        private List<String> keywords = new ArrayList<>();

        /** 标签列表，可为空 */
        private List<String> tags = new ArrayList<>();

        /** 相似问法列表，可为空 */
        private List<String> similarQuestions = new ArrayList<>();
    }
}
