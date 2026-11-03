package com.zhiketong.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiketong.common.BusinessException;
import com.zhiketong.common.PageResult;
import com.zhiketong.common.ResultCode;
import com.zhiketong.dto.kb.EntryCreateRequest;
import com.zhiketong.dto.kb.EntryRankingVO;
import com.zhiketong.dto.kb.EntrySearchResultVO;
import com.zhiketong.dto.kb.EntryUpdateRequest;
import com.zhiketong.dto.kb.EntryVO;
import com.zhiketong.entity.KbCategory;
import com.zhiketong.entity.KbEntry;
import com.zhiketong.entity.SysUser;
import com.zhiketong.enums.AuditAction;
import com.zhiketong.enums.AuditModule;
import com.zhiketong.enums.VersionChangeType;
import com.zhiketong.mapper.KbCategoryMapper;
import com.zhiketong.mapper.KbEntryMapper;
import com.zhiketong.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 知识库条目服务：条目维护、匹配预览与命中排行（R-23 ~ R-30）。
 *
 * @author 智客通开发组
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KbEntryService {

    /** 关键词数量下限 */
    private static final int MIN_KEYWORDS = 2;

    /** 关键词数量上限 */
    private static final int MAX_KEYWORDS = 10;

    /** 关键词串最大长度 */
    private static final int MAX_KEYWORDS_LENGTH = 255;

    /** 统计时间跨度上限（天） */
    private static final long MAX_DATE_SPAN_DAYS = 366L;

    /** 审计对象类型：知识词条 */
    private static final String TARGET_KB_ENTRY = "KB_ENTRY";

    private final KbEntryMapper kbEntryMapper;
    private final KbCategoryMapper kbCategoryMapper;
    private final SysUserMapper sysUserMapper;
    private final KbCategoryService kbCategoryService;
    private final KbSimilarQuestionService kbSimilarQuestionService;
    private final KbVersionService kbVersionService;
    private final KnowledgeMatcher knowledgeMatcher;
    private final TenantUsageService tenantUsageService;
    private final AuditLogService auditLogService;

    /**
     * 条目分页列表（R-23）：分类筛选包含子分类。
     */
    public PageResult<EntryVO> page(Integer pageNum, Integer pageSize, Long categoryId,
                                    String keyword, Integer status) {
        LambdaQueryWrapper<KbEntry> wrapper = new LambdaQueryWrapper<>();
        Set<Long> categoryIds = kbCategoryService.resolveCategoryIds(categoryId);
        if (categoryIds != null) {
            wrapper.in(KbEntry::getCategoryId, categoryIds);
        }
        if (status != null) {
            wrapper.eq(KbEntry::getStatus, status);
        }
        if (StringUtils.hasText(keyword)) {
            String value = keyword.trim();
            wrapper.and(w -> w.like(KbEntry::getQuestion, value)
                    .or().like(KbEntry::getAnswer, value)
                    .or().like(KbEntry::getKeywords, value));
        }
        wrapper.orderByDesc(KbEntry::getUpdateTime).orderByDesc(KbEntry::getId);
        Page<KbEntry> page = kbEntryMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return PageResult.of(page, toVOs(page.getRecords()));
    }

    /**
     * 条目详情（R-24）：附带相似问法列表。
     */
    public EntryVO detail(Long id) {
        KbEntry entry = requireEntry(id);
        int similarCount = kbSimilarQuestionService.countByEntry(id);
        EntryVO vo = toVO(entry, categoryName(entry.getCategoryId()),
                userNames(Collections.singletonList(entry)), similarCount);
        vo.setSimilarQuestions(kbSimilarQuestionService.list(id));
        return vo;
    }

    /**
     * 新增条目（R-25）。
     */
    @Transactional(rollbackFor = Exception.class)
    public EntryVO create(EntryCreateRequest request, Long operatorId) {
        // 知识库条目额度绑定套餐，超额时拦截新建并提示升级（S4-B PL-04）
        tenantUsageService.assertQuota(TenantPlanService.METRIC_KB);
        KbCategory category = validateCategory(request.getCategoryId());
        String question = request.getQuestion().trim();
        checkQuestionUnique(category.getId(), question, null);

        KbEntry entry = new KbEntry();
        entry.setCategoryId(category.getId());
        entry.setQuestion(question);
        entry.setAnswer(request.getAnswer().trim());
        entry.setKeywords(String.join(",", parseKeywords(request.getKeywords())));
        entry.setTags(joinTags(request.getTags()));
        entry.setHitCount(0);
        entry.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        entry.setCreateBy(operatorId);
        entry.setUpdateBy(operatorId);
        kbEntryMapper.insert(entry);
        if (request.getSimilarQuestions() != null && !request.getSimilarQuestions().isEmpty()) {
            kbSimilarQuestionService.replaceAll(entry.getId(), request.getSimilarQuestions(),
                    KbSimilarQuestionService.SOURCE_MANUAL, operatorId);
        }
        kbVersionService.record(entry.getId(), VersionChangeType.CREATE, null,
                kbVersionService.snapshot(entry), operatorId, "新增知识词条");
        knowledgeMatcher.invalidate();
        log.info("新增知识条目：id={}, categoryId={}, operatorId={}", entry.getId(), category.getId(), operatorId);
        auditLogService.record(AuditModule.KB, AuditAction.CREATE)
                .target(TARGET_KB_ENTRY, entry.getId(), entry.getQuestion())
                .detail("新增知识词条「" + entry.getQuestion() + "」（分类 " + category.getName() + "）")
                .after(entry)
                .save();
        return toVO(entry, category.getName(), userNames(Collections.singletonList(entry)),
                kbSimilarQuestionService.countByEntry(entry.getId()));
    }

    /**
     * 内部创建入口：供未命中转词条（R-84）与知识包导入（R-87）复用。
     * <p>与手工创建的区别是关键词可为空（导入与转词条场景往往没有现成关键词），
     * 其余校验（分类可用、同分类问题唯一、版本留痕）保持一致。</p>
     *
     * @return 新建的词条
     */
    @Transactional(rollbackFor = Exception.class)
    public EntryVO createFromSource(Long categoryId, String question, String answer,
                                    List<String> keywords, List<String> tags, List<String> similarQuestions,
                                    String similarSource, Long operatorId) {
        // 导入与未命中转词条同样消耗额度，批量场景逐条校验，超额时显式失败
        tenantUsageService.assertQuota(TenantPlanService.METRIC_KB);
        KbCategory category = validateCategory(categoryId);
        String trimmedQuestion = question == null ? "" : question.trim();
        if (trimmedQuestion.length() < 2 || trimmedQuestion.length() > 255) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "标准问题长度须为 2-255 个字符");
        }
        checkQuestionUnique(category.getId(), trimmedQuestion, null);

        KbEntry entry = new KbEntry();
        entry.setCategoryId(category.getId());
        entry.setQuestion(trimmedQuestion);
        entry.setAnswer(answer == null ? "" : answer.trim());
        entry.setKeywords(joinKeywords(keywords));
        entry.setTags(joinTags(tags));
        entry.setHitCount(0);
        entry.setStatus(1);
        entry.setCreateBy(operatorId);
        entry.setUpdateBy(operatorId);
        kbEntryMapper.insert(entry);
        if (similarQuestions != null && !similarQuestions.isEmpty()) {
            kbSimilarQuestionService.replaceAll(entry.getId(), similarQuestions,
                    similarSource == null ? KbSimilarQuestionService.SOURCE_MANUAL : similarSource, operatorId);
        }
        kbVersionService.record(entry.getId(), VersionChangeType.IMPORT, null,
                kbVersionService.snapshot(entry), operatorId, "由知识运营入口创建");
        knowledgeMatcher.invalidate();
        log.info("知识运营创建词条：id={}, categoryId={}, operatorId={}", entry.getId(), category.getId(), operatorId);
        return toVO(entry, category.getName(), userNames(Collections.singletonList(entry)),
                kbSimilarQuestionService.countByEntry(entry.getId()));
    }

    /**
     * 编辑条目（R-26）。
     */
    @Transactional(rollbackFor = Exception.class)
    public EntryVO update(Long id, EntryUpdateRequest request, Long operatorId) {
        KbEntry entry = requireEntry(id);
        KbVersionService.EntrySnapshot before = kbVersionService.snapshot(entry);
        Long targetCategoryId = entry.getCategoryId();
        if (request.getCategoryId() != null) {
            KbCategory category = validateCategory(request.getCategoryId());
            targetCategoryId = category.getId();
            entry.setCategoryId(targetCategoryId);
        }
        boolean questionChanged = request.getQuestion() != null;
        if (questionChanged) {
            entry.setQuestion(request.getQuestion().trim());
        }
        if (questionChanged || request.getCategoryId() != null) {
            checkQuestionUnique(targetCategoryId, entry.getQuestion(), id);
        }
        if (request.getAnswer() != null) {
            entry.setAnswer(request.getAnswer().trim());
        }
        if (request.getKeywords() != null) {
            entry.setKeywords(String.join(",", parseKeywords(request.getKeywords())));
        }
        if (request.getTags() != null) {
            entry.setTags(joinTags(request.getTags()));
        }
        if (request.getStatus() != null) {
            entry.setStatus(request.getStatus());
        }
        entry.setUpdateBy(operatorId);
        kbEntryMapper.updateById(entry);
        if (request.getSimilarQuestions() != null) {
            kbSimilarQuestionService.replaceAll(id, request.getSimilarQuestions(),
                    KbSimilarQuestionService.SOURCE_MANUAL, operatorId);
        }
        kbVersionService.record(id, VersionChangeType.UPDATE, before,
                kbVersionService.snapshot(entry), operatorId, "编辑知识词条");
        knowledgeMatcher.invalidate();
        log.info("编辑知识条目：id={}, operatorId={}", id, operatorId);
        auditLogService.record(AuditModule.KB, AuditAction.UPDATE)
                .target(TARGET_KB_ENTRY, id, entry.getQuestion())
                .detail("编辑知识词条「" + entry.getQuestion() + "」")
                .before(before)
                .after(kbVersionService.snapshot(entry))
                .save();
        return toVO(entry, categoryName(targetCategoryId), userNames(Collections.singletonList(entry)),
                kbSimilarQuestionService.countByEntry(id));
    }

    /**
     * 删除条目（R-27）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        KbEntry entry = requireEntry(id);
        KbVersionService.EntrySnapshot before = kbVersionService.snapshot(entry);
        kbVersionService.record(id, VersionChangeType.DELETE, before,
                null, null, "删除知识词条");
        kbSimilarQuestionService.deleteByEntry(id);
        kbEntryMapper.deleteById(id);
        knowledgeMatcher.invalidate();
        log.info("删除知识条目：id={}", id);
        auditLogService.record(AuditModule.KB, AuditAction.DELETE)
                .target(TARGET_KB_ENTRY, id, entry.getQuestion())
                .detail("删除知识词条「" + entry.getQuestion() + "」")
                .before(before)
                .save();
    }

    /**
     * 启用/禁用条目（R-28）。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> updateStatus(Long id, Integer status) {
        KbEntry entry = requireEntry(id);
        KbVersionService.EntrySnapshot before = kbVersionService.snapshot(entry);
        entry.setStatus(status);
        kbEntryMapper.updateById(entry);
        kbVersionService.record(id, VersionChangeType.STATUS, before,
                kbVersionService.snapshot(entry), null, status != null && status == 1 ? "启用词条" : "停用词条");
        knowledgeMatcher.invalidate();
        auditLogService.record(AuditModule.KB,
                        status != null && status == 1 ? AuditAction.ENABLE : AuditAction.DISABLE)
                .target(TARGET_KB_ENTRY, id, entry.getQuestion())
                .detail("知识词条「" + entry.getQuestion() + "」" + (status != null && status == 1 ? "启用" : "停用"))
                .before(before)
                .after(kbVersionService.snapshot(entry))
                .save();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("status", status);
        return result;
    }

    /**
     * 匹配预览（R-29）：只读，不增加命中次数。
     */
    public EntrySearchResultVO search(String keyword, Long categoryId, Integer limit) {
        if (!StringUtils.hasText(keyword)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "待匹配问句不能为空");
        }
        String query = keyword.trim();
        Set<Long> categoryIds = kbCategoryService.resolveCategoryIds(categoryId);
        List<KnowledgeMatcher.MatchResult> matches = knowledgeMatcher.match(query, categoryIds, limit);
        double threshold = knowledgeMatcher.getDirectAnswerThreshold();

        EntrySearchResultVO result = new EntrySearchResultVO();
        result.setQuery(query);
        List<EntrySearchResultVO.Candidate> candidates = new ArrayList<>();
        for (KnowledgeMatcher.MatchResult match : matches) {
            EntrySearchResultVO.Candidate candidate = new EntrySearchResultVO.Candidate();
            candidate.setId(match.getId());
            candidate.setQuestion(match.getQuestion());
            candidate.setAnswer(match.getAnswer());
            candidate.setCategoryName(match.getCategoryName());
            candidate.setScore(BigDecimal.valueOf(match.getScore()).setScale(4, RoundingMode.HALF_UP));
            candidate.setHitKeywords(match.getHitKeywords());
            candidate.setDirectAnswer(match.getScore() >= threshold);
            candidates.add(candidate);
        }
        result.setCandidates(candidates);
        return result;
    }

    /**
     * 命中排行（R-30）：V1.0 直接读取 hit_count。
     */
    public List<EntryRankingVO> ranking(Long categoryId, Integer limit, String startDate, String endDate) {
        validateDateRange(startDate, endDate);
        LambdaQueryWrapper<KbEntry> wrapper = new LambdaQueryWrapper<>();
        Set<Long> categoryIds = kbCategoryService.resolveCategoryIds(categoryId);
        if (categoryIds != null) {
            wrapper.in(KbEntry::getCategoryId, categoryIds);
        }
        wrapper.orderByDesc(KbEntry::getHitCount).orderByAsc(KbEntry::getId);
        // 排行不需要总数统计，关闭 count 查询
        Page<KbEntry> page = kbEntryMapper.selectPage(new Page<>(1, limit, false), wrapper);
        Map<Long, String> categoryNames = categoryNameMap();
        List<EntryRankingVO> result = new ArrayList<>();
        int rank = 1;
        for (KbEntry entry : page.getRecords()) {
            EntryRankingVO vo = new EntryRankingVO();
            vo.setRank(rank++);
            vo.setId(entry.getId());
            vo.setQuestion(entry.getQuestion());
            vo.setCategoryName(categoryNames.get(entry.getCategoryId()));
            vo.setHitCount(entry.getHitCount() == null ? 0 : entry.getHitCount());
            result.add(vo);
        }
        return result;
    }

    /**
     * 命中次数原子 +1，供 AI 对话直接命中时调用。
     */
    public void incrementHitCount(Long entryId) {
        if (entryId == null) {
            return;
        }
        kbEntryMapper.update(null, new LambdaUpdateWrapper<KbEntry>()
                .setSql("hit_count = hit_count + 1")
                .eq(KbEntry::getId, entryId));
    }

    /**
     * 查询条目，不存在时抛出 404。
     */
    public KbEntry requireEntry(Long id) {
        KbEntry entry = id == null ? null : kbEntryMapper.selectById(id);
        if (entry == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "知识条目不存在");
        }
        return entry;
    }

    /**
     * 校验条目所属分类：必须存在、启用，父分类启用，且为叶子分类。
     */
    private KbCategory validateCategory(Long categoryId) {
        KbCategory category = kbCategoryService.requireCategory(categoryId);
        if (!Integer.valueOf(1).equals(category.getStatus())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "所属分类已停用");
        }
        Long parentId = category.getParentId();
        if (parentId != null && parentId != 0L) {
            KbCategory parent = kbCategoryService.requireCategory(parentId);
            if (!Integer.valueOf(1).equals(parent.getStatus())) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "所属分类的父分类已停用");
            }
        } else {
            Long childCount = kbCategoryMapper.selectCount(new LambdaQueryWrapper<KbCategory>()
                    .eq(KbCategory::getParentId, categoryId));
            if (childCount != null && childCount > 0) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "该分类下存在子分类，条目只能挂在叶子分类下");
            }
        }
        return category;
    }

    /**
     * 同一分类下标准问题唯一。
     */
    private void checkQuestionUnique(Long categoryId, String question, Long excludeId) {
        Long count = kbEntryMapper.selectCount(new LambdaQueryWrapper<KbEntry>()
                .eq(KbEntry::getCategoryId, categoryId)
                .eq(KbEntry::getQuestion, question)
                .ne(excludeId != null, KbEntry::getId, excludeId));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "同一分类下已存在相同问题");
        }
    }

    /**
     * 拆分并校验关键词：2-10 个，英文逗号分隔。
     */
    private List<String> parseKeywords(String keywords) {
        List<String> list = new ArrayList<>();
        for (String item : keywords.replace('，', ',').split(",")) {
            String keyword = item.trim();
            if (!keyword.isEmpty()) {
                list.add(keyword);
            }
        }
        if (list.size() < MIN_KEYWORDS || list.size() > MAX_KEYWORDS) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "关键词须为 2-10 个");
        }
        if (String.join(",", list).length() > MAX_KEYWORDS_LENGTH) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "关键词总长度不能超过 255");
        }
        return list;
    }

    /**
     * 清洗标签串：去空项后英文逗号拼接，总长不超 255。
     */
    private String joinTags(String tags) {
        List<String> list = new ArrayList<>();
        if (StringUtils.hasText(tags)) {
            for (String item : tags.replace('，', ',').split(",")) {
                String tag = item.trim();
                if (!tag.isEmpty() && !list.contains(tag)) {
                    list.add(tag);
                }
            }
        }
        return joinLimited(list, MAX_KEYWORDS, MAX_KEYWORDS_LENGTH);
    }

    private String joinTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return "";
        }
        List<String> list = new ArrayList<>();
        for (String tag : tags) {
            if (StringUtils.hasText(tag) && !list.contains(tag.trim())) {
                list.add(tag.trim());
            }
        }
        return joinLimited(list, MAX_KEYWORDS, MAX_KEYWORDS_LENGTH);
    }

    /**
     * 拼接导入/转词条场景的关键词：允许为空，超过上限时截断而非报错。
     */
    private String joinKeywords(List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return "";
        }
        List<String> list = new ArrayList<>();
        for (String keyword : keywords) {
            if (StringUtils.hasText(keyword) && !list.contains(keyword.trim())) {
                list.add(keyword.trim());
            }
        }
        return joinLimited(list, MAX_KEYWORDS, MAX_KEYWORDS_LENGTH);
    }

    /**
     * 将列表拼接为英文逗号串，超出数量或长度时截断。
     */
    private String joinLimited(List<String> values, int maxCount, int maxLength) {
        StringBuilder builder = new StringBuilder();
        int count = 0;
        for (String value : values) {
            if (count >= maxCount) {
                break;
            }
            int extra = builder.length() == 0 ? value.length() : value.length() + 1;
            if (builder.length() + extra > maxLength) {
                break;
            }
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(value);
            count++;
        }
        return builder.toString();
    }

    /**
     * 校验统计日期范围：格式 yyyy-MM-dd、开始不晚于结束、跨度不超过 366 天。
     */
    private void validateDateRange(String startDate, String endDate) {
        if (!StringUtils.hasText(startDate) && !StringUtils.hasText(endDate)) {
            return;
        }
        try {
            LocalDate start = StringUtils.hasText(startDate) ? LocalDate.parse(startDate) : null;
            LocalDate end = StringUtils.hasText(endDate) ? LocalDate.parse(endDate) : null;
            if (start != null && end != null) {
                if (start.isAfter(end)) {
                    throw new BusinessException(ResultCode.PARAM_ERROR, "开始日期不能晚于结束日期");
                }
                if (ChronoUnit.DAYS.between(start, end) > MAX_DATE_SPAN_DAYS) {
                    throw new BusinessException(ResultCode.PARAM_ERROR, "统计时间跨度不能超过 366 天");
                }
            }
        } catch (DateTimeParseException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "日期格式须为 yyyy-MM-dd");
        }
    }

    private EntryVO toVO(KbEntry entry, String categoryName, Map<Long, String> userNames, int similarCount) {
        EntryVO vo = new EntryVO();
        vo.setId(entry.getId());
        vo.setCategoryId(entry.getCategoryId());
        vo.setCategoryName(categoryName);
        vo.setQuestion(entry.getQuestion());
        vo.setAnswer(entry.getAnswer());
        vo.setKeywords(entry.getKeywords());
        vo.setTags(entry.getTags());
        vo.setSimilarCount(similarCount);
        vo.setHitCount(entry.getHitCount() == null ? 0 : entry.getHitCount());
        vo.setStatus(entry.getStatus());
        vo.setCreateBy(entry.getCreateBy());
        vo.setCreateByName(userNames.get(entry.getCreateBy()));
        vo.setUpdateBy(entry.getUpdateBy());
        vo.setUpdateByName(userNames.get(entry.getUpdateBy()));
        vo.setCreateTime(entry.getCreateTime());
        vo.setUpdateTime(entry.getUpdateTime());
        return vo;
    }

    private List<EntryVO> toVOs(List<KbEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Long, String> categoryNames = categoryNameMap();
        Map<Long, String> userNames = userNames(entries);
        Set<Long> entryIds = new HashSet<>();
        for (KbEntry entry : entries) {
            entryIds.add(entry.getId());
        }
        Map<Long, Integer> similarCounts = kbSimilarQuestionService.countByEntryIds(entryIds);
        List<EntryVO> list = new ArrayList<>(entries.size());
        for (KbEntry entry : entries) {
            list.add(toVO(entry, categoryNames.get(entry.getCategoryId()), userNames,
                    similarCounts.getOrDefault(entry.getId(), 0)));
        }
        return list;
    }

    private String categoryName(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        KbCategory category = kbCategoryMapper.selectById(categoryId);
        return category == null ? null : category.getName();
    }

    private Map<Long, String> categoryNameMap() {
        List<KbCategory> categories = kbCategoryMapper.selectList(null);
        Map<Long, String> map = new HashMap<>();
        for (KbCategory category : categories) {
            map.put(category.getId(), category.getName());
        }
        return map;
    }

    /**
     * 批量查询创建人/更新人名称（昵称优先，其次登录名）。
     */
    private Map<Long, String> userNames(List<KbEntry> entries) {
        Set<Long> userIds = new HashSet<>();
        for (KbEntry entry : entries) {
            if (entry.getCreateBy() != null) {
                userIds.add(entry.getCreateBy());
            }
            if (entry.getUpdateBy() != null) {
                userIds.add(entry.getUpdateBy());
            }
        }
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<SysUser> users = sysUserMapper.selectBatchIds(userIds);
        Map<Long, String> names = new HashMap<>();
        for (SysUser user : users) {
            String displayName = user.getNickname() == null ? user.getUsername() : user.getNickname();
            names.put(user.getId(), displayName);
        }
        return names;
    }
}
