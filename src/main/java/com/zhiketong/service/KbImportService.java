package com.zhiketong.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiketong.common.BusinessException;
import com.zhiketong.common.ResultCode;
import com.zhiketong.dto.kb.KbImportRequest;
import com.zhiketong.dto.kb.KbImportResultVO;
import com.zhiketong.dto.kb.KbImportTaskVO;
import com.zhiketong.entity.KbCategory;
import com.zhiketong.entity.KbEntry;
import com.zhiketong.entity.KbImportTask;
import com.zhiketong.mapper.KbCategoryMapper;
import com.zhiketong.mapper.KbEntryMapper;
import com.zhiketong.mapper.KbImportTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 行业知识包导入服务（R-87 / R-88）。
 * <p>
 * 新客户冷启动时按分类批量灌入行业通用问答：分类不存在时自动创建顶级分类，
 * 词条按「分类 + 标准问题」去重，单条失败不影响其余条目，最后落一条导入批次记录。
 * <p>
 * 注意：本方法刻意不加事务，让每条词条独立提交，从而支持「部分成功」的导入结果；
 * 若整体回滚，运营人员无法知道哪条失败、也无法续传。
 *
 * @author 智客通开发组
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KbImportService {

    /** 失败明细最多保留条数 */
    private static final int MAX_ERROR_DETAILS = 20;

    /** 失败原因摘要最大长度 */
    private static final int MAX_ERROR_MESSAGE_LENGTH = 1000;

    /** 自动创建分类的默认排序值 */
    private static final int DEFAULT_SORT = 99;

    /** 顶级分类父 ID */
    private static final long ROOT_PARENT_ID = 0L;

    private final KbCategoryMapper categoryMapper;
    private final KbEntryMapper entryMapper;
    private final KbImportTaskMapper importTaskMapper;
    private final KbEntryService kbEntryService;

    /**
     * 导入行业知识包（R-87）。
     */
    public KbImportResultVO importPackage(KbImportRequest request, Long operatorId) {
        KbImportResultVO result = new KbImportResultVO();
        result.setPackageName(request.getPackageName().trim());
        result.setTotalCount(request.getEntries().size());
        List<String> errors = new ArrayList<>();
        List<String> createdCategories = new ArrayList<>();
        int success = 0;
        int fail = 0;

        for (KbImportRequest.Item item : request.getEntries()) {
            try {
                KbCategory category = resolveCategory(item.getCategoryName(), createdCategories);
                KbEntry existing = entryMapper.selectOne(new LambdaQueryWrapper<KbEntry>()
                        .eq(KbEntry::getCategoryId, category.getId())
                        .eq(KbEntry::getQuestion, item.getQuestion().trim())
                        .last("LIMIT 1"));
                if (existing != null) {
                    throw new BusinessException(ResultCode.CONFLICT, "同一分类下已存在相同问题");
                }
                kbEntryService.createFromSource(category.getId(), item.getQuestion().trim(),
                        item.getAnswer().trim(), item.getKeywords(), item.getTags(),
                        item.getSimilarQuestions(), KbSimilarQuestionService.SOURCE_IMPORT, operatorId);
                success++;
            } catch (BusinessException e) {
                fail++;
                addError(errors, item, e.getMessage());
            } catch (RuntimeException e) {
                fail++;
                addError(errors, item, "导入异常：" + e.getMessage());
                log.warn("知识包导入单条失败：package={}, question={}, error={}",
                        request.getPackageName(), item.getQuestion(), e.getMessage());
            }
        }

        String status = fail == 0 ? "SUCCESS" : (success == 0 ? "FAILED" : "PARTIAL");
        result.setSuccessCount(success);
        result.setFailCount(fail);
        result.setStatus(status);
        result.setCreatedCategories(createdCategories);
        result.setErrors(errors);

        KbImportTask task = new KbImportTask();
        task.setPackageName(result.getPackageName());
        task.setSource("JSON");
        task.setTotalCount(result.getTotalCount());
        task.setSuccessCount(success);
        task.setFailCount(fail);
        task.setStatus(status);
        task.setErrorMessage(truncateErrorMessage(errors));
        task.setOperatorId(operatorId);
        task.setDeleted(0);
        importTaskMapper.insert(task);
        result.setTaskId(task.getId());
        log.info("知识包导入完成：package={}, total={}, success={}, fail={}, operatorId={}",
                result.getPackageName(), result.getTotalCount(), success, fail, operatorId);
        return result;
    }

    /**
     * 导入批次列表（R-88）：按时间倒序。
     */
    public List<KbImportTaskVO> listTasks(Integer limit) {
        int size = limit == null || limit <= 0 ? 20 : Math.min(limit, 100);
        List<KbImportTask> tasks = importTaskMapper.selectList(new LambdaQueryWrapper<KbImportTask>()
                .orderByDesc(KbImportTask::getId)
                .last("LIMIT " + size));
        List<KbImportTaskVO> list = new ArrayList<>(tasks.size());
        for (KbImportTask task : tasks) {
            KbImportTaskVO vo = new KbImportTaskVO();
            vo.setId(task.getId());
            vo.setPackageName(task.getPackageName());
            vo.setSource(task.getSource());
            vo.setTotalCount(task.getTotalCount());
            vo.setSuccessCount(task.getSuccessCount());
            vo.setFailCount(task.getFailCount());
            vo.setStatus(task.getStatus());
            vo.setErrorMessage(task.getErrorMessage());
            vo.setCreateTime(task.getCreateTime());
            list.add(vo);
        }
        return list;
    }

    /**
     * 按名称解析顶级分类，不存在时自动创建。
     */
    private KbCategory resolveCategory(String categoryName, List<String> createdCategories) {
        String name = categoryName.trim();
        KbCategory category = categoryMapper.selectOne(new LambdaQueryWrapper<KbCategory>()
                .eq(KbCategory::getName, name)
                .eq(KbCategory::getParentId, ROOT_PARENT_ID)
                .last("LIMIT 1"));
        if (category != null) {
            return category;
        }
        category = new KbCategory();
        category.setName(name);
        category.setParentId(ROOT_PARENT_ID);
        category.setSort(DEFAULT_SORT);
        category.setStatus(1);
        category.setDescription("知识包导入自动创建");
        categoryMapper.insert(category);
        createdCategories.add(name);
        log.info("知识包导入自动创建分类：id={}, name={}", category.getId(), name);
        return category;
    }

    private void addError(List<String> errors, KbImportRequest.Item item, String message) {
        if (errors.size() >= MAX_ERROR_DETAILS) {
            return;
        }
        String reason = StringUtils.hasText(message) ? message : "导入失败";
        errors.add("【" + item.getQuestion() + "】" + reason);
    }

    private String truncateErrorMessage(List<String> errors) {
        if (errors.isEmpty()) {
            return null;
        }
        String message = String.join("；", errors);
        return message.length() > MAX_ERROR_MESSAGE_LENGTH
                ? message.substring(0, MAX_ERROR_MESSAGE_LENGTH) : message;
    }
}
