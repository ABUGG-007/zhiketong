package com.zhiketong.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiketong.common.BusinessException;
import com.zhiketong.common.PageResult;
import com.zhiketong.common.ResultCode;
import com.zhiketong.dto.kb.EntryVO;
import com.zhiketong.dto.kb.KbHealthVO;
import com.zhiketong.dto.kb.UnmatchedConvertRequest;
import com.zhiketong.dto.kb.UnmatchedQuestionVO;
import com.zhiketong.entity.KbEntry;
import com.zhiketong.entity.KbUnmatchedQuestion;
import com.zhiketong.entity.VocTopic;
import com.zhiketong.enums.UnmatchedStatus;
import com.zhiketong.mapper.KbEntryMapper;
import com.zhiketong.mapper.KbUnmatchedQuestionMapper;
import com.zhiketong.mapper.VocTopicMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 知识库未命中问题服务（R-80 ~ R-84）。
 * <p>
 * 只有「弱命中推荐」与「兜底」两类应答才计入未命中，闲聊与无效输入不计入，
 * 避免看板被“你好”“在吗”这类噪声污染。同一问法按归一化文本聚合为一行累加次数，
 * 运营人员可一键转为知识词条或标记忽略。
 *
 * @author 智客通开发组
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KbUnmatchedService {

    /** 来源渠道：站内会话 */
    public static final String CHANNEL_SITE = "SITE";

    /** 来源渠道：网页嵌入 */
    public static final String CHANNEL_EMBED = "EMBED";

    /** 单次批量操作上限 */
    private static final int BATCH_LIMIT = 200;

    /** 原始问法最大长度，超出截断避免撑破索引 */
    private static final int MAX_QUESTION_LENGTH = 255;

    private final KbUnmatchedQuestionMapper unmatchedQuestionMapper;
    private final KbEntryMapper entryMapper;
    private final KbEntryService kbEntryService;
    private final KnowledgeMatcher knowledgeMatcher;
    private final VocTopicMapper vocTopicMapper;

    /**
     * 采集一次未命中问法：按归一化文本聚合累加。
     *
     * @param question  访客原始问法
     * @param channel   来源渠道 SITE / EMBED
     * @param sessionId 会话 ID，可为空
     * @param userId    用户 ID，可为空
     * @param score     本轮最佳匹配得分
     */
    public void record(String question, String channel, Long sessionId, Long userId, double score) {
        if (!StringUtils.hasText(question)) {
            return;
        }
        String original = truncate(question.trim());
        String normalized = knowledgeMatcher.normalize(question);
        String key = truncate(normalized.isEmpty() ? original.toLowerCase() : normalized);
        if (key.isEmpty()) {
            return;
        }
        String targetChannel = CHANNEL_EMBED.equalsIgnoreCase(channel) ? CHANNEL_EMBED : CHANNEL_SITE;
        BigDecimal lastScore = BigDecimal.valueOf(Math.max(0, Math.min(1, score))).setScale(4, RoundingMode.HALF_UP);
        try {
            if (increment(key, original, targetChannel, sessionId, userId, lastScore)) {
                return;
            }
            KbUnmatchedQuestion record = new KbUnmatchedQuestion();
            record.setQuestion(original);
            record.setNormalizedQuestion(key);
            record.setChannel(targetChannel);
            record.setSessionId(sessionId);
            record.setUserId(userId);
            record.setAskCount(1);
            record.setLastScore(lastScore);
            record.setStatus(UnmatchedStatus.PENDING.name());
            record.setFirstAskTime(LocalDateTime.now());
            record.setLastAskTime(LocalDateTime.now());
            record.setDeleted(0);
            unmatchedQuestionMapper.insert(record);
        } catch (DuplicateKeyException e) {
            // 并发写入同一问法时由唯一索引兜底，退化为累加更新
            increment(key, original, targetChannel, sessionId, userId, lastScore);
        } catch (RuntimeException e) {
            // 未命中采集不能影响对话主流程，失败只记录日志
            log.warn("未命中问题落库失败：question={}, error={}", original, e.getMessage());
        }
    }

    /**
     * 未命中问题分页列表（R-80）：默认按出现次数倒序。
     */
    public PageResult<UnmatchedQuestionVO> page(Integer pageNum, Integer pageSize, String status,
                                                String channel, String keyword) {
        LambdaQueryWrapper<KbUnmatchedQuestion> wrapper = new LambdaQueryWrapper<>();
        UnmatchedStatus targetStatus = UnmatchedStatus.fromCode(status);
        if (StringUtils.hasText(status) && targetStatus == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "处理状态不合法");
        }
        wrapper.eq(targetStatus != null, KbUnmatchedQuestion::getStatus,
                targetStatus == null ? null : targetStatus.name());
        if (StringUtils.hasText(channel)) {
            wrapper.eq(KbUnmatchedQuestion::getChannel, channel.trim().toUpperCase());
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.like(KbUnmatchedQuestion::getQuestion, keyword.trim());
        }
        wrapper.orderByDesc(KbUnmatchedQuestion::getAskCount)
                .orderByDesc(KbUnmatchedQuestion::getLastAskTime);
        Page<KbUnmatchedQuestion> page = unmatchedQuestionMapper.selectPage(
                new Page<>(pageNum, pageSize), wrapper);
        return PageResult.of(page, toVOs(page.getRecords()));
    }

    /**
     * 忽略单条未命中问题（R-81）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void ignore(Long id) {
        KbUnmatchedQuestion record = requireRecord(id);
        if (UnmatchedStatus.CONVERTED.name().equals(record.getStatus())) {
            throw new BusinessException(ResultCode.CONFLICT, "该问题已转为知识词条，无法忽略");
        }
        updateStatus(id, UnmatchedStatus.IGNORED);
    }

    /**
     * 批量忽略未命中问题（R-82）。
     */
    @Transactional(rollbackFor = Exception.class)
    public int batchIgnore(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "请选择要忽略的问题");
        }
        if (ids.size() > BATCH_LIMIT) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "单次最多处理 " + BATCH_LIMIT + " 条");
        }
        unmatchedQuestionMapper.update(null, new LambdaUpdateWrapper<KbUnmatchedQuestion>()
                .set(KbUnmatchedQuestion::getStatus, UnmatchedStatus.IGNORED.name())
                .in(KbUnmatchedQuestion::getId, ids)
                .ne(KbUnmatchedQuestion::getStatus, UnmatchedStatus.CONVERTED.name()));
        return ids.size();
    }

    /**
     * 恢复为待处理（R-83）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void restore(Long id) {
        KbUnmatchedQuestion record = requireRecord(id);
        if (UnmatchedStatus.CONVERTED.name().equals(record.getStatus())) {
            throw new BusinessException(ResultCode.CONFLICT, "该问题已转为知识词条，无法恢复");
        }
        updateStatus(id, UnmatchedStatus.PENDING);
    }

    /**
     * 未命中问题转知识词条（R-84）。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> convert(Long id, UnmatchedConvertRequest request, Long operatorId) {
        KbUnmatchedQuestion record = requireRecord(id);
        if (UnmatchedStatus.CONVERTED.name().equals(record.getStatus())) {
            throw new BusinessException(ResultCode.CONFLICT, "该问题已转为知识词条");
        }
        String question = StringUtils.hasText(request.getQuestion())
                ? request.getQuestion().trim() : record.getQuestion();
        if (question.length() < 2 || question.length() > 255) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "标准问题长度须为 2-255 个字符");
        }
        boolean keepAsSimilar = request.getKeepAsSimilar() == null || Boolean.TRUE.equals(request.getKeepAsSimilar());
        List<String> similars = new ArrayList<>();
        if (keepAsSimilar && !question.equals(record.getQuestion())) {
            similars.add(record.getQuestion());
        }
        List<String> keywords = splitToList(request.getKeywords());
        List<String> tags = splitToList(request.getTags());
        EntryVO entry = kbEntryService.createFromSource(request.getCategoryId(), question, request.getAnswer(),
                keywords, tags, similars, KbSimilarQuestionService.SOURCE_MANUAL, operatorId);

        record.setStatus(UnmatchedStatus.CONVERTED.name());
        record.setEntryId(entry.getId());
        unmatchedQuestionMapper.updateById(record);
        log.info("未命中问题转词条：unmatchedId={}, entryId={}, operatorId={}", id, entry.getId(), operatorId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("unmatchedId", id);
        result.put("entryId", entry.getId());
        result.put("entryQuestion", entry.getQuestion());
        return result;
    }

    /**
     * 待处理未命中总数（看板与健康度共用）。
     */
    public long countPending() {
        Long count = unmatchedQuestionMapper.selectCount(new LambdaQueryWrapper<KbUnmatchedQuestion>()
                .eq(KbUnmatchedQuestion::getStatus, UnmatchedStatus.PENDING.name()));
        return count == null ? 0L : count;
    }

    /**
     * 未命中总数。
     */
    public long countTotal() {
        Long count = unmatchedQuestionMapper.selectCount(null);
        return count == null ? 0L : count;
    }

    /**
     * 未命中高频问题（Top N，仅待处理）。
     */
    public List<KbHealthVO.UnmatchedItem> topUnmatched(int limit) {
        List<KbUnmatchedQuestion> records = unmatchedQuestionMapper.selectList(
                new LambdaQueryWrapper<KbUnmatchedQuestion>()
                        .eq(KbUnmatchedQuestion::getStatus, UnmatchedStatus.PENDING.name())
                        .orderByDesc(KbUnmatchedQuestion::getAskCount)
                        .last("LIMIT " + Math.max(1, limit)));
        List<KbHealthVO.UnmatchedItem> list = new ArrayList<>(records.size());
        for (KbUnmatchedQuestion record : records) {
            KbHealthVO.UnmatchedItem item = new KbHealthVO.UnmatchedItem();
            item.setId(record.getId());
            item.setQuestion(record.getQuestion());
            item.setAskCount(record.getAskCount() == null ? 0 : record.getAskCount());
            item.setChannel(record.getChannel());
            list.add(item);
        }
        return list;
    }

    /**
     * 近 N 天新增未命中趋势：按首次出现日期聚合，缺失日期补 0。
     */
    public List<KbHealthVO.TrendItem> trend(int days) {
        int span = Math.max(1, days);
        LocalDate start = LocalDate.now().minusDays(span - 1L);
        QueryWrapper<KbUnmatchedQuestion> wrapper = new QueryWrapper<>();
        wrapper.select("DATE(first_ask_time) AS stat_date", "COUNT(*) AS stat_count")
                .ge("first_ask_time", start.atStartOfDay())
                .groupBy("DATE(first_ask_time)");
        List<Map<String, Object>> rows = unmatchedQuestionMapper.selectMaps(wrapper);
        Map<String, Long> counts = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object date = row.get("stat_date");
            Object count = row.get("stat_count");
            if (date == null) {
                continue;
            }
            String key = date.toString().length() > 10 ? date.toString().substring(0, 10) : date.toString();
            long value = count instanceof Number ? ((Number) count).longValue() : 0L;
            counts.put(key, value);
        }
        List<KbHealthVO.TrendItem> trend = new ArrayList<>(span);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        for (int i = 0; i < span; i++) {
            String key = start.plusDays(i).format(formatter);
            KbHealthVO.TrendItem item = new KbHealthVO.TrendItem();
            item.setDate(key);
            item.setCount(counts.getOrDefault(key, 0L));
            trend.add(item);
        }
        return trend;
    }

    /**
     * 查询某个客户之声主题下的未命中问法（R-207 主题详情复用）。
     *
     * @param topicId 主题 ID
     * @param limit   条数上限
     * @return 问法列表，按累计提问次数倒序
     */
    public List<UnmatchedQuestionVO> listByTopic(Long topicId, int limit) {
        if (topicId == null) {
            return new ArrayList<>();
        }
        int size = Math.max(1, Math.min(limit, 200));
        List<KbUnmatchedQuestion> records = unmatchedQuestionMapper.selectList(
                new LambdaQueryWrapper<KbUnmatchedQuestion>()
                        .eq(KbUnmatchedQuestion::getTopicId, topicId)
                        .orderByDesc(KbUnmatchedQuestion::getAskCount)
                        .orderByDesc(KbUnmatchedQuestion::getLastAskTime)
                        .last("LIMIT " + size));
        return toVOs(records);
    }

    /**
     * 查询未命中记录，不存在时抛出 404。
     */
    public KbUnmatchedQuestion requireRecord(Long id) {
        KbUnmatchedQuestion record = id == null ? null : unmatchedQuestionMapper.selectById(id);
        if (record == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "未命中问题不存在");
        }
        return record;
    }

    private boolean increment(String normalized, String original, String channel, Long sessionId,
                              Long userId, BigDecimal lastScore) {
        KbUnmatchedQuestion existing = unmatchedQuestionMapper.selectOne(
                new LambdaQueryWrapper<KbUnmatchedQuestion>()
                        .eq(KbUnmatchedQuestion::getNormalizedQuestion, normalized)
                        .last("LIMIT 1"));
        if (existing == null) {
            return false;
        }
        LambdaUpdateWrapper<KbUnmatchedQuestion> update = new LambdaUpdateWrapper<KbUnmatchedQuestion>()
                .setSql("ask_count = ask_count + 1")
                .set(KbUnmatchedQuestion::getLastScore, lastScore)
                .set(KbUnmatchedQuestion::getLastAskTime, LocalDateTime.now())
                .set(KbUnmatchedQuestion::getQuestion, original)
                .set(KbUnmatchedQuestion::getChannel, channel)
                .set(sessionId != null, KbUnmatchedQuestion::getSessionId, sessionId)
                .set(userId != null, KbUnmatchedQuestion::getUserId, userId)
                .eq(KbUnmatchedQuestion::getId, existing.getId());
        unmatchedQuestionMapper.update(null, update);
        return true;
    }

    private void updateStatus(Long id, UnmatchedStatus status) {
        KbUnmatchedQuestion record = new KbUnmatchedQuestion();
        record.setId(id);
        record.setStatus(status.name());
        unmatchedQuestionMapper.updateById(record);
    }

    private List<UnmatchedQuestionVO> toVOs(List<KbUnmatchedQuestion> records) {
        List<UnmatchedQuestionVO> list = new ArrayList<>(records.size());
        Set<Long> entryIds = new HashSet<>();
        Set<Long> topicIds = new HashSet<>();
        for (KbUnmatchedQuestion record : records) {
            if (record.getEntryId() != null) {
                entryIds.add(record.getEntryId());
            }
            if (record.getTopicId() != null) {
                topicIds.add(record.getTopicId());
            }
        }
        Map<Long, String> entryQuestions = entryQuestions(entryIds);
        Map<Long, String> topicNames = topicNames(topicIds);
        for (KbUnmatchedQuestion record : records) {
            UnmatchedQuestionVO vo = new UnmatchedQuestionVO();
            vo.setId(record.getId());
            vo.setQuestion(record.getQuestion());
            vo.setChannel(record.getChannel());
            vo.setAskCount(record.getAskCount() == null ? 0 : record.getAskCount());
            vo.setLastScore(record.getLastScore());
            vo.setStatus(record.getStatus());
            vo.setEntryId(record.getEntryId());
            vo.setEntryQuestion(record.getEntryId() == null ? null : entryQuestions.get(record.getEntryId()));
            vo.setTopicId(record.getTopicId());
            vo.setTopicName(record.getTopicId() == null ? null : topicNames.get(record.getTopicId()));
            vo.setClusterScore(record.getClusterScore());
            vo.setFirstAskTime(record.getFirstAskTime());
            vo.setLastAskTime(record.getLastAskTime());
            list.add(vo);
        }
        return list;
    }

    private Map<Long, String> topicNames(Collection<Long> topicIds) {
        if (topicIds == null || topicIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<VocTopic> topics = vocTopicMapper.selectBatchIds(topicIds);
        Map<Long, String> map = new HashMap<>();
        for (VocTopic topic : topics) {
            map.put(topic.getId(), topic.getTopicName());
        }
        return map;
    }

    private Map<Long, String> entryQuestions(Collection<Long> entryIds) {
        if (entryIds == null || entryIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<KbEntry> entries = entryMapper.selectBatchIds(entryIds);
        Map<Long, String> map = new HashMap<>();
        for (KbEntry entry : entries) {
            map.put(entry.getId(), entry.getQuestion());
        }
        return map;
    }

    private List<String> splitToList(String value) {
        List<String> list = new ArrayList<>();
        if (!StringUtils.hasText(value)) {
            return list;
        }
        for (String item : value.replace('，', ',').split(",")) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) {
                list.add(trimmed);
            }
        }
        return list;
    }

    private String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > MAX_QUESTION_LENGTH ? value.substring(0, MAX_QUESTION_LENGTH) : value;
    }
}
