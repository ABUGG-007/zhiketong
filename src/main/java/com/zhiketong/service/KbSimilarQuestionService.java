package com.zhiketong.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiketong.ai.AiChatService;
import com.zhiketong.ai.ReplyPrompts;
import com.zhiketong.common.BusinessException;
import com.zhiketong.common.ResultCode;
import com.zhiketong.dto.AiChatResponse;
import com.zhiketong.dto.kb.SimilarExpandVO;
import com.zhiketong.dto.kb.SimilarQuestionVO;
import com.zhiketong.entity.KbEntry;
import com.zhiketong.entity.KbSimilarQuestion;
import com.zhiketong.mapper.KbEntryMapper;
import com.zhiketong.mapper.KbSimilarQuestionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 知识库相似问法服务（R-73 ~ R-77）。
 * <p>
 * 相似问法独立成表，参与 {@link KnowledgeMatcher} 的相似度打分，使口语化、同义改写与
 * 常见错别字问法也能命中同一条标准答案；同一词条下问法归一化后唯一，最多 20 条。
 *
 * @author 智客通开发组
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KbSimilarQuestionService {

    /** 单个词条下相似问法上限 */
    private static final int MAX_PER_ENTRY = 20;

    /** 单条相似问法长度下限 */
    private static final int MIN_QUESTION_LENGTH = 2;

    /** 单条相似问法长度上限 */
    private static final int MAX_QUESTION_LENGTH = 255;

    /** AI 扩写候选数量上限 */
    private static final int AI_CANDIDATE_LIMIT = 10;

    /** 来源：手工维护 */
    public static final String SOURCE_MANUAL = "MANUAL";

    /** 来源：大模型扩写 */
    public static final String SOURCE_AI = "AI";

    /** 来源：知识包导入 */
    public static final String SOURCE_IMPORT = "IMPORT";

    private final KbSimilarQuestionMapper similarQuestionMapper;
    private final KbEntryMapper entryMapper;
    private final KnowledgeMatcher knowledgeMatcher;
    private final AiChatService aiChatService;

    /**
     * 词条下的相似问法列表（R-73）。
     */
    public List<SimilarQuestionVO> list(Long entryId) {
        requireEntry(entryId);
        return toVOs(similarQuestionMapper.selectList(new LambdaQueryWrapper<KbSimilarQuestion>()
                .eq(KbSimilarQuestion::getEntryId, entryId)
                .orderByAsc(KbSimilarQuestion::getId)));
    }

    /**
     * 批量查询多个词条的相似问法，按条目分组，供列表页与详情页一次取数。
     */
    public Map<Long, List<SimilarQuestionVO>> listByEntryIds(Collection<Long> entryIds) {
        if (entryIds == null || entryIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<KbSimilarQuestion> similars = similarQuestionMapper.selectList(
                new LambdaQueryWrapper<KbSimilarQuestion>()
                        .in(KbSimilarQuestion::getEntryId, entryIds)
                        .orderByAsc(KbSimilarQuestion::getId));
        Map<Long, List<SimilarQuestionVO>> map = new HashMap<>();
        for (KbSimilarQuestion similar : similars) {
            map.computeIfAbsent(similar.getEntryId(), key -> new ArrayList<>()).add(toVO(similar));
        }
        return map;
    }

    /**
     * 统计多个词条的相似问法数量。
     */
    public Map<Long, Integer> countByEntryIds(Collection<Long> entryIds) {
        if (entryIds == null || entryIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<KbSimilarQuestion> similars = similarQuestionMapper.selectList(
                new LambdaQueryWrapper<KbSimilarQuestion>()
                        .select(KbSimilarQuestion::getEntryId)
                        .in(KbSimilarQuestion::getEntryId, entryIds));
        Map<Long, Integer> counts = new HashMap<>();
        for (KbSimilarQuestion similar : similars) {
            counts.merge(similar.getEntryId(), 1, Integer::sum);
        }
        return counts;
    }

    /**
     * 单个词条的相似问法数量。
     */
    public int countByEntry(Long entryId) {
        if (entryId == null) {
            return 0;
        }
        Long count = similarQuestionMapper.selectCount(new LambdaQueryWrapper<KbSimilarQuestion>()
                .eq(KbSimilarQuestion::getEntryId, entryId));
        return count == null ? 0 : count.intValue();
    }

    /**
     * 批量新增相似问法（R-74）：自动去重、校验上限，写操作后失效匹配缓存。
     */
    @Transactional(rollbackFor = Exception.class)
    public List<SimilarQuestionVO> createBatch(Long entryId, List<String> questions, String source, Long operatorId) {
        KbEntry entry = requireEntry(entryId);
        String normalizedSource = normalizeSource(source);
        List<String> existing = existingQuestions(entry);
        Set<String> seen = new HashSet<>();
        for (String question : existing) {
            seen.add(knowledgeMatcher.normalize(question));
        }
        int remaining = MAX_PER_ENTRY - existing.size();
        if (remaining <= 0) {
            throw new BusinessException(ResultCode.CONFLICT, "该词条相似问法已达上限 " + MAX_PER_ENTRY + " 条");
        }
        List<String> accepted = new ArrayList<>();
        for (String item : questions) {
            String question = cleanQuestion(item);
            String normalized = knowledgeMatcher.normalize(question);
            if (normalized.isEmpty() || !seen.add(normalized)) {
                continue;
            }
            accepted.add(question);
            if (accepted.size() >= remaining) {
                break;
            }
        }
        if (accepted.isEmpty()) {
            throw new BusinessException(ResultCode.CONFLICT, "新增的问法已全部存在或与标准问题重复");
        }
        List<KbSimilarQuestion> inserted = new ArrayList<>(accepted.size());
        for (String question : accepted) {
            KbSimilarQuestion similar = new KbSimilarQuestion();
            similar.setEntryId(entryId);
            similar.setQuestion(question);
            similar.setSource(normalizedSource);
            similar.setStatus(1);
            similar.setCreateBy(operatorId);
            similar.setDeleted(0);
            similarQuestionMapper.insert(similar);
            inserted.add(similar);
        }
        knowledgeMatcher.invalidate();
        log.info("新增相似问法：entryId={}, count={}, source={}, operatorId={}",
                entryId, inserted.size(), normalizedSource, operatorId);
        return toVOs(inserted);
    }

    /**
     * 全量替换词条的相似问法（供词条编辑与回滚使用）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void replaceAll(Long entryId, List<String> questions, String source, Long operatorId) {
        deleteByEntry(entryId);
        if (questions == null || questions.isEmpty()) {
            knowledgeMatcher.invalidate();
            return;
        }
        List<String> unique = new ArrayList<>(new LinkedHashSet<>(questions));
        if (unique.size() > MAX_PER_ENTRY) {
            unique = new ArrayList<>(unique.subList(0, MAX_PER_ENTRY));
        }
        KbEntry entry = requireEntry(entryId);
        Set<String> seen = new HashSet<>();
        seen.add(knowledgeMatcher.normalize(entry.getQuestion()));
        String normalizedSource = normalizeSource(source);
        for (String item : unique) {
            String question = cleanQuestion(item);
            if (knowledgeMatcher.normalize(question).isEmpty() || !seen.add(knowledgeMatcher.normalize(question))) {
                continue;
            }
            KbSimilarQuestion similar = new KbSimilarQuestion();
            similar.setEntryId(entryId);
            similar.setQuestion(question);
            similar.setSource(normalizedSource);
            similar.setStatus(1);
            similar.setCreateBy(operatorId);
            similar.setDeleted(0);
            similarQuestionMapper.insert(similar);
        }
        knowledgeMatcher.invalidate();
    }

    /**
     * 删除单条相似问法（R-76）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        KbSimilarQuestion similar = id == null ? null : similarQuestionMapper.selectById(id);
        if (similar == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "相似问法不存在");
        }
        similarQuestionMapper.deleteById(id);
        knowledgeMatcher.invalidate();
        log.info("删除相似问法：id={}, entryId={}", id, similar.getEntryId());
    }

    /**
     * 逻辑删除词条下的全部相似问法（词条删除时级联）。
     */
    public void deleteByEntry(Long entryId) {
        if (entryId == null) {
            return;
        }
        similarQuestionMapper.delete(new LambdaQueryWrapper<KbSimilarQuestion>()
                .eq(KbSimilarQuestion::getEntryId, entryId));
    }

    /**
     * 启用/停用相似问法（R-77）。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> updateStatus(Long id, Integer status) {
        KbSimilarQuestion similar = id == null ? null : similarQuestionMapper.selectById(id);
        if (similar == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "相似问法不存在");
        }
        similar.setStatus(status);
        similarQuestionMapper.updateById(similar);
        knowledgeMatcher.invalidate();
        Map<String, Object> result = new HashMap<>();
        result.put("id", id);
        result.put("status", status);
        return result;
    }

    /**
     * 大模型扩写相似问法（R-75）：结果仅作候选返回，不落库。
     */
    public SimilarExpandVO expand(Long entryId) {
        KbEntry entry = requireEntry(entryId);
        SimilarExpandVO vo = new SimilarExpandVO();
        vo.setEntryId(entryId);
        vo.setQuestion(entry.getQuestion());
        vo.setCandidates(new ArrayList<>());
        if (!aiChatService.isAvailable()) {
            vo.setNote("大模型未启用或未配置密钥，请先在「大模型配置」中完成接入");
            return vo;
        }
        List<String> existing = existingQuestions(entry);
        String prompt = ReplyPrompts.similarExpandPrompt(entry.getQuestion(), entry.getAnswer(), existing);
        AiChatResponse response = aiChatService.chat(null, null, prompt, null);
        if (response == null || response.isFallback() || !StringUtils.hasText(response.getContent())) {
            vo.setNote("大模型暂时不可用，请稍后重试或手工补充问法");
            return vo;
        }
        Set<String> seen = new HashSet<>();
        for (String question : existing) {
            seen.add(knowledgeMatcher.normalize(question));
        }
        seen.add(knowledgeMatcher.normalize(entry.getQuestion()));
        List<String> candidates = new ArrayList<>();
        for (String line : response.getContent().split("\\r?\\n")) {
            String candidate = cleanCandidate(line);
            if (candidate == null) {
                continue;
            }
            if (seen.add(knowledgeMatcher.normalize(candidate))) {
                candidates.add(candidate);
            }
            if (candidates.size() >= AI_CANDIDATE_LIMIT) {
                break;
            }
        }
        if (candidates.isEmpty()) {
            vo.setNote("大模型未返回可用问法，请稍后重试");
        }
        vo.setCandidates(candidates);
        return vo;
    }

    /**
     * 词条当前全部问法（标准问题 + 相似问法）。
     */
    public List<String> existingQuestions(KbEntry entry) {
        List<String> questions = new ArrayList<>();
        List<KbSimilarQuestion> similars = similarQuestionMapper.selectList(
                new LambdaQueryWrapper<KbSimilarQuestion>()
                        .select(KbSimilarQuestion::getQuestion)
                        .eq(KbSimilarQuestion::getEntryId, entry.getId())
                        .orderByAsc(KbSimilarQuestion::getId));
        for (KbSimilarQuestion similar : similars) {
            questions.add(similar.getQuestion());
        }
        return questions;
    }

    private KbEntry requireEntry(Long entryId) {
        KbEntry entry = entryId == null ? null : entryMapper.selectById(entryId);
        if (entry == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "知识条目不存在");
        }
        return entry;
    }

    private String cleanQuestion(String question) {
        if (!StringUtils.hasText(question)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "相似问法不能为空");
        }
        String value = question.trim();
        if (value.length() < MIN_QUESTION_LENGTH || value.length() > MAX_QUESTION_LENGTH) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "相似问法「" + abbreviate(value) + "」长度须为 2-255 个字符");
        }
        return value;
    }

    /**
     * 清洗大模型输出的单行文本：去掉编号、引号与序号前缀。
     *
     * @return 合法候选，非法时返回 null
     */
    private String cleanCandidate(String line) {
        if (!StringUtils.hasText(line)) {
            return null;
        }
        String value = line.trim();
        value = value.replaceFirst("^[0-9]+[.、)）:：\\s]*", "");
        value = value.replaceFirst("^[-*•\\s]+", "");
        value = value.replaceAll("^[\"'“”‘’《》]+|[\"'“”‘’《》]+$", "").trim();
        if (value.length() < MIN_QUESTION_LENGTH || value.length() > MAX_QUESTION_LENGTH) {
            return null;
        }
        return value;
    }

    private String normalizeSource(String source) {
        if (!StringUtils.hasText(source)) {
            return SOURCE_MANUAL;
        }
        String value = source.trim().toUpperCase();
        if (SOURCE_MANUAL.equals(value) || SOURCE_AI.equals(value) || SOURCE_IMPORT.equals(value)) {
            return value;
        }
        throw new BusinessException(ResultCode.PARAM_ERROR, "相似问法来源不合法");
    }

    private String abbreviate(String value) {
        return value.length() > 12 ? value.substring(0, 12) + "…" : value;
    }

    private List<SimilarQuestionVO> toVOs(List<KbSimilarQuestion> similars) {
        List<SimilarQuestionVO> list = new ArrayList<>(similars.size());
        for (KbSimilarQuestion similar : similars) {
            list.add(toVO(similar));
        }
        return list;
    }

    private SimilarQuestionVO toVO(KbSimilarQuestion similar) {
        SimilarQuestionVO vo = new SimilarQuestionVO();
        vo.setId(similar.getId());
        vo.setEntryId(similar.getEntryId());
        vo.setQuestion(similar.getQuestion());
        vo.setSource(similar.getSource());
        vo.setStatus(similar.getStatus());
        vo.setCreateTime(similar.getCreateTime());
        return vo;
    }
}
