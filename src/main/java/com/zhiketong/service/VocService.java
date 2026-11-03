package com.zhiketong.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiketong.common.BusinessException;
import com.zhiketong.common.Constants;
import com.zhiketong.common.PageResult;
import com.zhiketong.common.ResultCode;
import com.zhiketong.dto.voc.VocClusterResultVO;
import com.zhiketong.dto.voc.VocOverviewVO;
import com.zhiketong.dto.voc.VocTopicDetailVO;
import com.zhiketong.dto.voc.VocTopicMergeRequest;
import com.zhiketong.dto.voc.VocTopicRequest;
import com.zhiketong.dto.voc.VocTopicVO;
import com.zhiketong.dto.voc.VocWordCloudVO;
import com.zhiketong.entity.KbUnmatchedQuestion;
import com.zhiketong.entity.VocTopic;
import com.zhiketong.enums.AuditAction;
import com.zhiketong.enums.AuditModule;
import com.zhiketong.enums.UnmatchedStatus;
import com.zhiketong.mapper.KbUnmatchedQuestionMapper;
import com.zhiketong.mapper.VocTopicMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 客户之声服务（S6-C，DB-06：R-205 ~ R-214）。
 *
 * <p>把未命中问法变成可运营的主题：</p>
 * <ol>
 *   <li><b>聚类</b>：以问法 2-gram 集合与主题（名称 + 关键词 + 代表问法）做结构相似度，
 *       达到阈值归入已有主题，否则新建自动主题；每轮按提问次数倒序处理，
 *       让高频声音先成为种子。</li>
 *   <li><b>聚合</b>：主题的条数、提问次数、待处理数、转化数与渠道分布统一重算，
 *       热点话题榜与主题详情因此始终同源。</li>
 *   <li><b>词云</b>：对未转词条的问法做中文 2~4-gram 词频，权重为包含该词问法的
 *       累计提问次数，反映「访客真实在问什么」，而不是字符串出现次数。</li>
 * </ol>
 *
 * <p>聚类只写「归属」，不改变未命中问题的处理状态（PENDING / CONVERTED / IGNORED），
 * 因此聚类与知识运营两条线互不干扰。</p>
 *
 * @author 智客通开发组
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VocService {

    /** 主题来源：自动聚类 */
    public static final String TOPIC_TYPE_AUTO = "AUTO";

    /** 主题来源：人工创建 */
    public static final String TOPIC_TYPE_MANUAL = "MANUAL";

    /** 词云默认词条数 */
    private static final int WORD_CLOUD_DEFAULT = 80;

    /** 词云最大词条数 */
    private static final int WORD_CLOUD_MAX = 200;

    /** 自动主题名称截取长度 */
    private static final int TOPIC_NAME_LENGTH = 12;

    /** 主题详情展示的问法条数上限 */
    private static final int TOPIC_QUESTION_LIMIT = 50;

    /** 主题名称最大长度，与表结构一致 */
    private static final int TOPIC_NAME_MAX_LENGTH = 64;

    /** 单次归并源主题数量上限 */
    private static final int MERGE_LIMIT = 50;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final VocTopicMapper vocTopicMapper;
    private final KbUnmatchedQuestionMapper unmatchedQuestionMapper;
    private final KbUnmatchedService kbUnmatchedService;
    private final KnowledgeMatcher knowledgeMatcher;
    private final AuditLogService auditLogService;

    /** 问法归入主题的相似度阈值 */
    @Value("${zhiketong.voc.similarity-threshold:0.5}")
    private double similarityThreshold;

    /** 热点话题判定阈值（累计提问次数） */
    @Value("${zhiketong.voc.hot-ask-threshold:5}")
    private int hotAskThreshold;

    /** 单轮聚类最多处理的问法条数 */
    @Value("${zhiketong.voc.cluster-batch-size:500}")
    private int clusterBatchSize;

    // ------------------------------------------------------------------
    // 概览与词云
    // ------------------------------------------------------------------

    /**
     * 客户之声概览（R-205）。
     *
     * @return 概览指标
     */
    public VocOverviewVO overview() {
        VocOverviewVO vo = new VocOverviewVO();
        long total = countQuestions(null, null);
        long pending = countQuestions(UnmatchedStatus.PENDING.name(), null);
        long clustered = countQuestions(null, Boolean.TRUE);
        vo.setUnmatchedTotal(total);
        vo.setUnmatchedPending(pending);
        vo.setClusteredCount(clustered);
        vo.setClusterRate(total == 0 ? BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(clustered).divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP));

        List<VocTopic> topics = vocTopicMapper.selectList(new LambdaQueryWrapper<VocTopic>()
                .orderByDesc(VocTopic::getAskTotal)
                .orderByDesc(VocTopic::getId));
        long autoCount = 0L;
        long manualCount = 0L;
        long hotCount = 0L;
        List<VocTopicVO> topTopics = new ArrayList<>();
        for (VocTopic topic : topics) {
            if (TOPIC_TYPE_MANUAL.equals(topic.getTopicType())) {
                manualCount++;
            } else {
                autoCount++;
            }
            int askTotal = topic.getAskTotal() == null ? 0 : topic.getAskTotal();
            if (askTotal >= hotAskThreshold && Integer.valueOf(Constants.STATUS_ENABLED).equals(topic.getStatus())) {
                hotCount++;
            }
            if (topTopics.size() < 10) {
                topTopics.add(VocTopicVO.of(topic));
            }
        }
        vo.setTopicCount(topics.size());
        vo.setAutoTopicCount(autoCount);
        vo.setManualTopicCount(manualCount);
        vo.setHotTopicCount(hotCount);
        vo.setTopTopics(topTopics);
        vo.setChannels(channels());
        vo.setTrend(trend(7));
        LocalDateTime lastClusterTime = null;
        for (VocTopic topic : topics) {
            if (topic.getLastClusterTime() != null
                    && (lastClusterTime == null || topic.getLastClusterTime().isAfter(lastClusterTime))) {
                lastClusterTime = topic.getLastClusterTime();
            }
        }
        vo.setLastClusterTime(lastClusterTime);
        return vo;
    }

    /**
     * 词云（R-214）：中文 2~4-gram 词频，权重为包含该词的问法累计提问次数。
     *
     * @param topicId 只统计某个主题下的问法，为空统计全部未转词条的问法
     * @param limit   词条数上限
     * @return 词云
     */
    public VocWordCloudVO wordCloud(Long topicId, Integer limit) {
        int top = limit == null ? WORD_CLOUD_DEFAULT : Math.min(Math.max(10, limit), WORD_CLOUD_MAX);
        LambdaQueryWrapper<KbUnmatchedQuestion> wrapper = new LambdaQueryWrapper<KbUnmatchedQuestion>()
                .ne(KbUnmatchedQuestion::getStatus, UnmatchedStatus.CONVERTED.name())
                .eq(topicId != null, KbUnmatchedQuestion::getTopicId, topicId)
                .select(KbUnmatchedQuestion::getId, KbUnmatchedQuestion::getQuestion,
                        KbUnmatchedQuestion::getNormalizedQuestion, KbUnmatchedQuestion::getAskCount);
        List<KbUnmatchedQuestion> questions = unmatchedQuestionMapper.selectList(wrapper);

        Map<String, long[]> stats = new HashMap<>();
        for (KbUnmatchedQuestion question : questions) {
            // 归一化文本已去标点与停用词，避免「怎么」「可以」这类虚词占榜
            String text = knowledgeMatcher.normalize(question.getQuestion());
            if (!StringUtils.hasText(text)) {
                continue;
            }
            long weight = question.getAskCount() == null ? 1L : Math.max(1, question.getAskCount());
            for (String term : candidates(text)) {
                long[] value = stats.computeIfAbsent(term, key -> new long[2]);
                value[0] += weight;
                value[1] += 1;
            }
        }

        List<Map.Entry<String, long[]>> entries = new ArrayList<>(stats.entrySet());
        entries.sort(Comparator.<Map.Entry<String, long[]>>comparingLong(entry -> entry.getValue()[0]).reversed()
                .thenComparing(entry -> entry.getKey().length(), Comparator.reverseOrder())
                .thenComparing(Map.Entry::getKey));

        VocWordCloudVO vo = new VocWordCloudVO();
        vo.setQuestionCount(questions.size());
        Map<String, Long> weightMap = new HashMap<>();
        for (Map.Entry<String, long[]> entry : entries) {
            weightMap.put(entry.getKey(), entry.getValue()[0]);
        }
        List<String> accepted = new ArrayList<>();
        long totalWeight = 0L;
        for (Map.Entry<String, long[]> entry : entries) {
            if (vo.getWords().size() >= top) {
                break;
            }
            String term = entry.getKey();
            long weight = entry.getValue()[0];
            // 同权重下若已被更长的词覆盖，则丢弃短词，避免「退款」与「申请退款」同时上榜
            if (isCovered(term, weight, accepted, weightMap)) {
                continue;
            }
            accepted.add(term);
            VocWordCloudVO.Word word = new VocWordCloudVO.Word();
            word.setTerm(term);
            word.setWeight(weight);
            word.setQuestionCount((int) entry.getValue()[1]);
            vo.getWords().add(word);
            totalWeight += weight;
        }
        vo.setTotalWeight((int) Math.min(Integer.MAX_VALUE, totalWeight));
        return vo;
    }

    // ------------------------------------------------------------------
    // 主题管理
    // ------------------------------------------------------------------

    /**
     * 主题分页（R-206）。
     *
     * @param pageNum   页码
     * @param pageSize  每页条数
     * @param topicType 来源过滤 AUTO / MANUAL
     * @param status    状态过滤
     * @param keyword   名称模糊查询
     * @return 分页结果
     */
    public PageResult<VocTopicVO> pageTopics(Integer pageNum, Integer pageSize, String topicType,
                                             Integer status, String keyword) {
        LambdaQueryWrapper<VocTopic> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(topicType)) {
            String type = topicType.trim().toUpperCase();
            if (!TOPIC_TYPE_AUTO.equals(type) && !TOPIC_TYPE_MANUAL.equals(type)) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "主题来源只能为 AUTO 或 MANUAL");
            }
            wrapper.eq(VocTopic::getTopicType, type);
        }
        wrapper.eq(status != null, VocTopic::getStatus, status)
                .like(StringUtils.hasText(keyword), VocTopic::getTopicName,
                        keyword == null ? null : keyword.trim())
                .orderByAsc(VocTopic::getSort)
                .orderByDesc(VocTopic::getAskTotal)
                .orderByDesc(VocTopic::getId);
        Page<VocTopic> page = vocTopicMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<VocTopicVO> list = new ArrayList<>(page.getRecords().size());
        for (VocTopic topic : page.getRecords()) {
            list.add(VocTopicVO.of(topic));
        }
        return PageResult.of(page, list);
    }

    /**
     * 主题详情（R-207）：指标 + 归属问法。
     *
     * @param id 主题 ID
     * @return 主题详情
     */
    public VocTopicDetailVO topicDetail(Long id) {
        VocTopic topic = requireTopic(id);
        VocTopicDetailVO vo = new VocTopicDetailVO();
        vo.setTopic(VocTopicVO.of(topic));
        vo.setQuestions(kbUnmatchedService.listByTopic(topic.getId(), TOPIC_QUESTION_LIMIT));
        return vo;
    }

    /**
     * 新增人工主题（R-208）：名称同租户唯一。
     *
     * @param request    主题请求
     * @param operatorId 操作人 ID
     * @return 新建主题
     */
    @Transactional(rollbackFor = Exception.class)
    public VocTopicVO createTopic(VocTopicRequest request, Long operatorId) {
        String name = request.getTopicName().trim();
        if (existsName(name, null)) {
            throw new BusinessException(ResultCode.CONFLICT, "主题名称已存在");
        }
        VocTopic topic = new VocTopic();
        topic.setTopicName(name);
        topic.setTopicType(TOPIC_TYPE_MANUAL);
        topic.setKeywords(trimToNull(request.getKeywords()));
        topic.setSampleQuestion(trimToNull(request.getSampleQuestion()));
        topic.setRemark(trimToNull(request.getRemark()));
        topic.setQuestionCount(0);
        topic.setAskTotal(0);
        topic.setPendingCount(0);
        topic.setConvertedCount(0);
        topic.setStatus(request.getStatus() == null ? Constants.STATUS_ENABLED : request.getStatus());
        topic.setSort(request.getSort() == null ? 0 : request.getSort());
        topic.setCreateBy(operatorId);
        topic.setUpdateBy(operatorId);
        topic.setDeleted(0);
        vocTopicMapper.insert(topic);

        auditLogService.record(AuditModule.VOC, AuditAction.CREATE)
                .target("VOC_TOPIC", topic.getId(), topic.getTopicName())
                .detail("新增客户之声主题：" + topic.getTopicName())
                .after(topic)
                .save();
        log.info("新增客户之声主题：id={}, name={}, operatorId={}", topic.getId(), name, operatorId);
        return VocTopicVO.of(topic);
    }

    /**
     * 修改主题（R-209）：来源与聚合字段不可改，改由聚类与重算维护。
     *
     * @param id         主题 ID
     * @param request    主题请求
     * @param operatorId 操作人 ID
     * @return 修改后的主题
     */
    @Transactional(rollbackFor = Exception.class)
    public VocTopicVO updateTopic(Long id, VocTopicRequest request, Long operatorId) {
        VocTopic topic = requireTopic(id);
        String name = request.getTopicName().trim();
        if (existsName(name, id)) {
            throw new BusinessException(ResultCode.CONFLICT, "主题名称已存在");
        }
        String before = describe(topic);
        topic.setTopicName(name);
        topic.setKeywords(trimToNull(request.getKeywords()));
        topic.setSampleQuestion(trimToNull(request.getSampleQuestion()));
        topic.setRemark(trimToNull(request.getRemark()));
        if (request.getSort() != null) {
            topic.setSort(request.getSort());
        }
        if (request.getStatus() != null) {
            topic.setStatus(request.getStatus());
        }
        topic.setUpdateBy(operatorId);
        topic.setUpdateTime(LocalDateTime.now());
        vocTopicMapper.updateById(topic);

        auditLogService.record(AuditModule.VOC, AuditAction.UPDATE)
                .target("VOC_TOPIC", topic.getId(), topic.getTopicName())
                .detail("修改客户之声主题：" + topic.getTopicName())
                .before(before)
                .after(describe(topic))
                .save();
        log.info("修改客户之声主题：id={}, name={}, operatorId={}", id, name, operatorId);
        return VocTopicVO.of(topic);
    }

    /**
     * 启用/停用主题（R-212）：停用后不再参与自动归类，已归属问法保持不动。
     *
     * @param id         主题 ID
     * @param status     目标状态 1/0
     * @param operatorId 操作人 ID
     * @return 修改后的主题
     */
    @Transactional(rollbackFor = Exception.class)
    public VocTopicVO updateStatus(Long id, Integer status, Long operatorId) {
        VocTopic topic = requireTopic(id);
        topic.setStatus(status);
        topic.setUpdateBy(operatorId);
        topic.setUpdateTime(LocalDateTime.now());
        vocTopicMapper.updateById(topic);

        auditLogService.record(AuditModule.VOC,
                        Integer.valueOf(Constants.STATUS_ENABLED).equals(status)
                                ? AuditAction.ENABLE : AuditAction.DISABLE)
                .target("VOC_TOPIC", topic.getId(), topic.getTopicName())
                .detail((Integer.valueOf(Constants.STATUS_ENABLED).equals(status) ? "启用" : "停用")
                        + "客户之声主题：" + topic.getTopicName())
                .after(topic)
                .save();
        return VocTopicVO.of(topic);
    }

    /**
     * 删除主题（R-210）：逻辑删除，同时解除问法归属，下一轮聚类会重新归集。
     *
     * @param id         主题 ID
     * @param operatorId 操作人 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteTopic(Long id, Long operatorId) {
        VocTopic topic = requireTopic(id);
        detachQuestions(Collections.singletonList(id));
        // 名称参与唯一索引，删除时改名释放原名，避免同名主题反复删除触发唯一键冲突
        vocTopicMapper.update(null, new LambdaUpdateWrapper<VocTopic>()
                .set(VocTopic::getTopicName, deletedName(topic))
                .set(VocTopic::getUpdateBy, operatorId)
                .set(VocTopic::getUpdateTime, LocalDateTime.now())
                .eq(VocTopic::getId, topic.getId()));
        vocTopicMapper.deleteById(topic.getId());

        auditLogService.record(AuditModule.VOC, AuditAction.DELETE)
                .target("VOC_TOPIC", topic.getId(), topic.getTopicName())
                .detail("删除客户之声主题：" + topic.getTopicName() + "，归属问法已解除")
                .before(describe(topic))
                .save();
        log.info("删除客户之声主题：id={}, name={}, operatorId={}", id, topic.getTopicName(), operatorId);
    }

    /**
     * 合并主题（R-211）：源主题的问法并入目标主题后删除源主题。
     *
     * @param request    合并请求
     * @param operatorId 操作人 ID
     * @return 合并后的目标主题
     */
    @Transactional(rollbackFor = Exception.class)
    public VocTopicVO mergeTopics(VocTopicMergeRequest request, Long operatorId) {
        List<Long> sourceIds = new ArrayList<>(new LinkedHashSet<>(request.getSourceTopicIds()));
        sourceIds.remove(request.getTargetTopicId());
        if (sourceIds.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "请选择与目标主题不同的源主题");
        }
        if (sourceIds.size() > MERGE_LIMIT) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "单次最多合并 " + MERGE_LIMIT + " 个主题");
        }
        VocTopic target = requireTopic(request.getTargetTopicId());
        List<VocTopic> sources = vocTopicMapper.selectBatchIds(sourceIds);
        if (sources.size() != sourceIds.size()) {
            throw new BusinessException(ResultCode.NOT_FOUND, "部分源主题不存在或已删除");
        }
        for (VocTopic source : sources) {
            unmatchedQuestionMapper.update(null, new LambdaUpdateWrapper<KbUnmatchedQuestion>()
                    .set(KbUnmatchedQuestion::getTopicId, target.getId())
                    .set(KbUnmatchedQuestion::getClusterScore, null)
                    .eq(KbUnmatchedQuestion::getTopicId, source.getId()));
        }
        Set<Long> touched = new HashSet<>();
        touched.add(target.getId());
        recomputeAggregates(touched, null);

        for (VocTopic source : sources) {
            vocTopicMapper.update(null, new LambdaUpdateWrapper<VocTopic>()
                    .set(VocTopic::getTopicName, deletedName(source))
                    .set(VocTopic::getUpdateBy, operatorId)
                    .set(VocTopic::getUpdateTime, LocalDateTime.now())
                    .eq(VocTopic::getId, source.getId()));
            vocTopicMapper.deleteById(source.getId());
        }

        auditLogService.record(AuditModule.VOC, AuditAction.UPDATE)
                .target("VOC_TOPIC", target.getId(), target.getTopicName())
                .detail("合并客户之声主题：" + sources.size() + " 个源主题并入【" + target.getTopicName() + "】")
                .after(describe(requireTopic(target.getId())))
                .save();
        log.info("合并客户之声主题：targetId={}, sourceIds={}, operatorId={}",
                target.getId(), sourceIds, operatorId);
        return VocTopicVO.of(requireTopic(target.getId()));
    }

    // ------------------------------------------------------------------
    // 聚类
    // ------------------------------------------------------------------

    /**
     * 执行一轮聚类（R-213）。
     *
     * <p>同一租户内按提问次数倒序取一批未转词条的问法，逐条与启用中的主题做结构相似度匹配；
     * 达到阈值归入最相似主题，否则以该问法为种子新建自动主题。方法自身不加分布式锁，
     * 由 {@code VocClusterTask} 在任务层统一加锁与跨租户调度。</p>
     *
     * @param operatorId 操作人 ID，定时任务传 0
     * @return 本轮执行结果
     */
    @Transactional(rollbackFor = Exception.class)
    public VocClusterResultVO cluster(Long operatorId) {
        long start = System.currentTimeMillis();
        LocalDateTime now = LocalDateTime.now();
        VocClusterResultVO result = new VocClusterResultVO();
        result.setClusterTime(now);

        List<KbUnmatchedQuestion> candidates = unmatchedQuestionMapper.selectList(
                new LambdaQueryWrapper<KbUnmatchedQuestion>()
                        .ne(KbUnmatchedQuestion::getStatus, UnmatchedStatus.CONVERTED.name())
                        .orderByDesc(KbUnmatchedQuestion::getAskCount)
                        .orderByAsc(KbUnmatchedQuestion::getId)
                        .last("LIMIT " + Math.max(1, clusterBatchSize)));
        long eligible = countUnconvertedQuestions();
        result.setScannedCount(candidates.size());
        result.setSkippedCount((int) Math.max(0L, eligible - candidates.size()));

        List<VocTopic> topics = new ArrayList<>(vocTopicMapper.selectList(
                new LambdaQueryWrapper<VocTopic>().eq(VocTopic::getStatus, Constants.STATUS_ENABLED)));
        Map<Long, Set<String>> topicGrams = new HashMap<>();
        Set<String> usedNames = new HashSet<>();
        for (VocTopic topic : topics) {
            topicGrams.put(topic.getId(), topicGrams(topic));
        }
        Set<Long> touched = new HashSet<>();
        int assigned = 0;
        int created = 0;
        for (KbUnmatchedQuestion question : candidates) {
            Set<String> grams = gramsOf(questionText(question));
            VocTopic best = null;
            double bestScore = 0.0;
            for (VocTopic topic : topics) {
                double score = similarity(grams, topicGrams.get(topic.getId()));
                if (score > bestScore) {
                    bestScore = score;
                    best = topic;
                }
            }
            if (best != null && bestScore >= similarityThreshold) {
                assign(question, best.getId(), (int) Math.round(bestScore * 100));
                touched.add(best.getId());
                assigned++;
                continue;
            }
            VocTopic topic = buildAutoTopic(question, operatorId, usedNames);
            vocTopicMapper.insert(topic);
            topics.add(topic);
            topicGrams.put(topic.getId(), gramsOf(topicText(topic)));
            assign(question, topic.getId(), 100);
            touched.add(topic.getId());
            created++;
        }
        recomputeAggregates(touched, now);
        result.setAssignedCount(assigned);
        result.setCreatedCount(created);
        Long totalTopics = vocTopicMapper.selectCount(null);
        result.setTopicCount(totalTopics == null ? 0 : totalTopics.intValue());
        result.setDurationMs(System.currentTimeMillis() - start);

        auditLogService.record(AuditModule.VOC, AuditAction.UPDATE)
                .target("VOC_CLUSTER", null, "未命中问法聚类")
                .detail("聚类完成：扫描 " + result.getScannedCount() + " 条，归入 " + assigned
                        + " 条，新建主题 " + created + " 个")
                .save();
        log.info("客户之声聚类完成：tenant scan={}, assigned={}, created={}, topics={}, cost={}ms",
                result.getScannedCount(), assigned, created, topics.size(), result.getDurationMs());
        return result;
    }

    /**
     * 重算主题聚合：条数、提问次数、待处理数、转化数与渠道分布。
     *
     * @param topicIds    主题 ID 集合
     * @param clusterTime 本轮聚类时间，为空表示只重算指标
     */
    private void recomputeAggregates(Collection<Long> topicIds, LocalDateTime clusterTime) {
        if (topicIds == null || topicIds.isEmpty()) {
            return;
        }
        Map<Long, long[]> aggregates = new HashMap<>();
        Map<Long, Map<String, Integer>> channelStats = new HashMap<>();

        QueryWrapper<KbUnmatchedQuestion> aggWrapper = new QueryWrapper<>();
        aggWrapper.select("topic_id", "COUNT(*) AS q_cnt", "COALESCE(SUM(ask_count), 0) AS ask_total",
                        "SUM(CASE WHEN status = 'PENDING' THEN 1 ELSE 0 END) AS pending_cnt",
                        "SUM(CASE WHEN status = 'CONVERTED' THEN 1 ELSE 0 END) AS converted_cnt")
                .isNotNull("topic_id")
                .in("topic_id", topicIds)
                .groupBy("topic_id");
        for (Map<String, Object> row : unmatchedQuestionMapper.selectMaps(aggWrapper)) {
            Long topicId = longOfNullable(row, "topic_id");
            if (topicId == null) {
                continue;
            }
            aggregates.put(topicId, new long[]{
                    longOf(row, "q_cnt"), longOf(row, "ask_total"),
                    longOf(row, "pending_cnt"), longOf(row, "converted_cnt")});
        }

        QueryWrapper<KbUnmatchedQuestion> channelWrapper = new QueryWrapper<>();
        channelWrapper.select("topic_id", "channel", "COUNT(*) AS q_cnt")
                .isNotNull("topic_id")
                .in("topic_id", topicIds)
                .groupBy("topic_id", "channel");
        for (Map<String, Object> row : unmatchedQuestionMapper.selectMaps(channelWrapper)) {
            Long topicId = longOfNullable(row, "topic_id");
            Object channel = valueOf(row, "channel");
            if (topicId == null || channel == null) {
                continue;
            }
            channelStats.computeIfAbsent(topicId, key -> new LinkedHashMap<>())
                    .merge(channel.toString().toUpperCase(), (int) longOf(row, "q_cnt"), Integer::sum);
        }

        for (Long topicId : topicIds) {
            long[] values = aggregates.getOrDefault(topicId, new long[]{0L, 0L, 0L, 0L});
            Map<String, Integer> channels = channelStats.get(topicId);
            LambdaUpdateWrapper<VocTopic> update = new LambdaUpdateWrapper<VocTopic>()
                    .set(VocTopic::getQuestionCount, (int) values[0])
                    .set(VocTopic::getAskTotal, (int) values[1])
                    .set(VocTopic::getPendingCount, (int) values[2])
                    .set(VocTopic::getConvertedCount, (int) values[3])
                    .set(VocTopic::getChannels, channelsText(channels))
                    .set(clusterTime != null, VocTopic::getLastClusterTime, clusterTime)
                    .eq(VocTopic::getId, topicId);
            vocTopicMapper.update(null, update);
        }
    }

    /**
     * 构建自动主题：以问法前 12 字命名，同名时追加序号。
     */
    private VocTopic buildAutoTopic(KbUnmatchedQuestion question, Long operatorId, Set<String> usedNames) {
        String base = baseName(questionText(question));
        VocTopic topic = new VocTopic();
        topic.setTopicName(uniqueName(base, usedNames));
        topic.setTopicType(TOPIC_TYPE_AUTO);
        topic.setKeywords(keywordsOf(questionText(question)));
        topic.setSampleQuestion(truncate(question.getQuestion(), 255));
        topic.setQuestionCount(0);
        topic.setAskTotal(0);
        topic.setPendingCount(0);
        topic.setConvertedCount(0);
        topic.setStatus(Constants.STATUS_ENABLED);
        topic.setSort(0);
        topic.setCreateBy(operatorId);
        topic.setUpdateBy(operatorId);
        topic.setDeleted(0);
        return topic;
    }

    private void assign(KbUnmatchedQuestion question, Long topicId, Integer score) {
        if (topicId.equals(question.getTopicId()) && Objects.equals(question.getClusterScore(), score)) {
            // 上轮已是同一归属，无需重复写库
            return;
        }
        unmatchedQuestionMapper.update(null, new LambdaUpdateWrapper<KbUnmatchedQuestion>()
                .set(KbUnmatchedQuestion::getTopicId, topicId)
                .set(KbUnmatchedQuestion::getClusterScore, score)
                .eq(KbUnmatchedQuestion::getId, question.getId()));
        question.setTopicId(topicId);
        question.setClusterScore(score);
    }

    private void detachQuestions(Collection<Long> topicIds) {
        if (topicIds == null || topicIds.isEmpty()) {
            return;
        }
        unmatchedQuestionMapper.update(null, new LambdaUpdateWrapper<KbUnmatchedQuestion>()
                .set(KbUnmatchedQuestion::getTopicId, null)
                .set(KbUnmatchedQuestion::getClusterScore, null)
                .in(KbUnmatchedQuestion::getTopicId, topicIds));
    }

    // ------------------------------------------------------------------
    // 文本处理
    // ------------------------------------------------------------------

    /**
     * 结构相似度：Dice 系数占 60%、包含度占 40%，与知识库匹配保持同一套口径。
     */
    private double similarity(Set<String> left, Set<String> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return 0.0;
        }
        int intersection = 0;
        for (String gram : left) {
            if (right.contains(gram)) {
                intersection++;
            }
        }
        double dice = 2.0 * intersection / (left.size() + right.size());
        double containment = (double) intersection / Math.min(left.size(), right.size());
        return 0.6 * dice + 0.4 * containment;
    }

    /**
     * 主题的匹配词集合：代表关键词 + 名称 + 代表问法。
     */
    private Set<String> topicGrams(VocTopic topic) {
        Set<String> grams = new HashSet<>();
        if (StringUtils.hasText(topic.getKeywords())) {
            for (String keyword : topic.getKeywords().replace('，', ',').split(",")) {
                grams.addAll(gramsOf(keyword));
            }
        }
        grams.addAll(gramsOf(topic.getTopicName()));
        grams.addAll(gramsOf(topic.getSampleQuestion()));
        return grams;
    }

    private String topicText(VocTopic topic) {
        StringBuilder builder = new StringBuilder();
        if (StringUtils.hasText(topic.getKeywords())) {
            builder.append(topic.getKeywords().replace(',', ' ')).append(' ');
        }
        if (StringUtils.hasText(topic.getTopicName())) {
            builder.append(topic.getTopicName()).append(' ');
        }
        if (StringUtils.hasText(topic.getSampleQuestion())) {
            builder.append(topic.getSampleQuestion());
        }
        return builder.toString();
    }

    /**
     * 中文按 2-gram、英文数字按整词切分。
     */
    private Set<String> gramsOf(String text) {
        if (!StringUtils.hasText(text)) {
            return Collections.emptySet();
        }
        Set<String> grams = new HashSet<>();
        StringBuilder cjk = new StringBuilder();
        StringBuilder ascii = new StringBuilder();
        for (char ch : knowledgeMatcher.normalize(text).toCharArray()) {
            if (isCjk(ch)) {
                flushAscii(ascii, grams);
                cjk.append(ch);
            } else if (Character.isLetterOrDigit(ch)) {
                flushCjk(cjk, grams);
                ascii.append(ch);
            } else {
                flushCjk(cjk, grams);
                flushAscii(ascii, grams);
            }
        }
        flushCjk(cjk, grams);
        flushAscii(ascii, grams);
        return grams;
    }

    /**
     * 词云候选词：中文 2~4-gram，英文数字整词。
     */
    private Set<String> candidates(String normalizedText) {
        Set<String> words = new LinkedHashSet<>();
        StringBuilder cjk = new StringBuilder();
        StringBuilder ascii = new StringBuilder();
        for (char ch : normalizedText.toCharArray()) {
            if (isCjk(ch)) {
                flushAscii(ascii, words);
                cjk.append(ch);
            } else if (Character.isLetterOrDigit(ch)) {
                flushCjkMulti(cjk, words);
                ascii.append(ch);
            } else {
                flushCjkMulti(cjk, words);
                flushAscii(ascii, words);
            }
        }
        flushCjkMulti(cjk, words);
        flushAscii(ascii, words);
        return words;
    }

    private void flushCjk(StringBuilder buffer, Set<String> grams) {
        String text = buffer.toString();
        for (int i = 0; i + 1 < text.length(); i++) {
            grams.add(text.substring(i, i + 2));
        }
        buffer.setLength(0);
    }

    private void flushCjkMulti(StringBuilder buffer, Set<String> words) {
        String text = buffer.toString();
        for (int length = 2; length <= 4; length++) {
            for (int i = 0; i + length <= text.length(); i++) {
                words.add(text.substring(i, i + length));
            }
        }
        buffer.setLength(0);
    }

    private void flushAscii(StringBuilder buffer, Set<String> grams) {
        if (buffer.length() >= 2) {
            grams.add(buffer.toString());
        }
        buffer.setLength(0);
    }

    private boolean isCjk(char ch) {
        return (ch >= '\u4E00' && ch <= '\u9FFF')
                || (ch >= '\u3400' && ch <= '\u4DBF')
                || (ch >= '\uF900' && ch <= '\uFAFF');
    }

    /**
     * 词条是否已被同等权重的更长词覆盖：被覆盖则说明该短词不携带额外信息。
     */
    private boolean isCovered(String term, long weight, List<String> accepted, Map<String, Long> weightMap) {
        for (String longer : accepted) {
            if (longer.length() > term.length() && longer.contains(term)
                    && Objects.equals(weightMap.get(longer), weight)) {
                return true;
            }
        }
        return false;
    }

    private String baseName(String text) {
        String base = text == null ? "" : text.replaceAll("\\s+", "");
        if (base.isEmpty()) {
            return "未命名主题";
        }
        return base.length() > TOPIC_NAME_LENGTH ? base.substring(0, TOPIC_NAME_LENGTH) : base;
    }

    /**
     * 名称唯一化：同名时追加序号，长度上限 64。
     */
    private String uniqueName(String base, Set<String> usedNames) {
        String name = base;
        int suffix = 2;
        while (usedNames.contains(name) || existsName(name, null)) {
            String tail = " " + suffix;
            int max = TOPIC_NAME_MAX_LENGTH - tail.length();
            name = (base.length() > max ? base.substring(0, max) : base) + tail;
            suffix++;
            if (suffix > 999) {
                name = base.substring(0, Math.min(base.length(), TOPIC_NAME_MAX_LENGTH - 8)) + " " + System.nanoTime() % 10000;
                break;
            }
        }
        usedNames.add(name);
        return name;
    }

    /**
     * 代表关键词：取问法中下标最靠前的 3 个 2-gram。
     */
    private String keywordsOf(String text) {
        Set<String> grams = gramsOf(text);
        if (grams.isEmpty()) {
            return null;
        }
        List<String> list = new ArrayList<>(grams);
        list.sort(Comparator.comparingInt((String gram) -> text.indexOf(gram))
                .thenComparing(Comparator.naturalOrder()));
        List<String> picked = list.size() > 3 ? list.subList(0, 3) : list;
        return String.join(",", picked);
    }

    private String questionText(KbUnmatchedQuestion question) {
        return StringUtils.hasText(question.getNormalizedQuestion())
                ? question.getNormalizedQuestion() : question.getQuestion();
    }

    private String deletedName(VocTopic topic) {
        String suffix = "#" + topic.getId();
        String base = topic.getTopicName() == null ? "主题" : topic.getTopicName();
        int max = TOPIC_NAME_MAX_LENGTH - suffix.length();
        return (base.length() > max ? base.substring(0, max) : base) + suffix;
    }

    private String describe(VocTopic topic) {
        return "name=" + topic.getTopicName() + ", type=" + topic.getTopicType()
                + ", keywords=" + topic.getKeywords() + ", status=" + topic.getStatus()
                + ", sort=" + topic.getSort();
    }

    // ------------------------------------------------------------------
    // 统计与工具
    // ------------------------------------------------------------------

    /**
     * 未命中问法计数。
     *
     * @param status    处理状态，为空不过滤
     * @param clustered true 只统计已归属主题，false 只统计未归属，null 不过滤
     */
    private long countQuestions(String status, Boolean clustered) {
        LambdaQueryWrapper<KbUnmatchedQuestion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(status), KbUnmatchedQuestion::getStatus, status);
        if (Boolean.TRUE.equals(clustered)) {
            wrapper.isNotNull(KbUnmatchedQuestion::getTopicId);
        } else if (Boolean.FALSE.equals(clustered)) {
            wrapper.isNull(KbUnmatchedQuestion::getTopicId);
        }
        Long count = unmatchedQuestionMapper.selectCount(wrapper);
        return count == null ? 0L : count;
    }

    /**
     * 参与聚类的问法总数：未转成知识词条的未命中问法。
     */
    private long countUnconvertedQuestions() {
        Long count = unmatchedQuestionMapper.selectCount(new LambdaQueryWrapper<KbUnmatchedQuestion>()
                .ne(KbUnmatchedQuestion::getStatus, UnmatchedStatus.CONVERTED.name()));
        return count == null ? 0L : count;
    }

    private List<VocOverviewVO.ChannelItem> channels() {
        QueryWrapper<KbUnmatchedQuestion> wrapper = new QueryWrapper<>();
        wrapper.select("channel", "COUNT(*) AS q_cnt", "COALESCE(SUM(ask_count), 0) AS ask_cnt")
                .groupBy("channel");
        List<VocOverviewVO.ChannelItem> list = new ArrayList<>();
        for (Map<String, Object> row : unmatchedQuestionMapper.selectMaps(wrapper)) {
            Object channel = valueOf(row, "channel");
            if (channel == null) {
                continue;
            }
            VocOverviewVO.ChannelItem item = new VocOverviewVO.ChannelItem();
            item.setChannel(channel.toString().toUpperCase());
            item.setChannelName(channelName(channel.toString()));
            item.setQuestionCount(longOf(row, "q_cnt"));
            item.setAskCount(longOf(row, "ask_cnt"));
            list.add(item);
        }
        list.sort(Comparator.comparingLong(VocOverviewVO.ChannelItem::getQuestionCount).reversed());
        return list;
    }

    private String channelName(String channel) {
        if (KbUnmatchedService.CHANNEL_EMBED.equalsIgnoreCase(channel)) {
            return "网页接入";
        }
        if (KbUnmatchedService.CHANNEL_SITE.equalsIgnoreCase(channel)) {
            return "站内会话";
        }
        return channel;
    }

    private List<VocOverviewVO.TrendItem> trend(int days) {
        int span = Math.max(1, days);
        LocalDate start = LocalDate.now().minusDays(span - 1L);
        QueryWrapper<KbUnmatchedQuestion> wrapper = new QueryWrapper<>();
        wrapper.select("DATE(first_ask_time) AS stat_date", "COUNT(*) AS stat_count")
                .ge("first_ask_time", start.atStartOfDay())
                .groupBy("DATE(first_ask_time)");
        Map<String, Long> counts = new HashMap<>();
        for (Map<String, Object> row : unmatchedQuestionMapper.selectMaps(wrapper)) {
            Object date = valueOf(row, "stat_date");
            if (date == null) {
                continue;
            }
            String key = date.toString().length() > 10 ? date.toString().substring(0, 10) : date.toString();
            counts.put(key, longOf(row, "stat_count"));
        }
        List<VocOverviewVO.TrendItem> list = new ArrayList<>(span);
        for (int i = 0; i < span; i++) {
            String key = start.plusDays(i).format(DATE_FORMATTER);
            VocOverviewVO.TrendItem item = new VocOverviewVO.TrendItem();
            item.setDate(key);
            item.setCount(counts.getOrDefault(key, 0L));
            list.add(item);
        }
        return list;
    }

    private VocTopic requireTopic(Long id) {
        VocTopic topic = id == null ? null : vocTopicMapper.selectById(id);
        if (topic == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "客户之声主题不存在");
        }
        return topic;
    }

    private boolean existsName(String name, Long excludeId) {
        LambdaQueryWrapper<VocTopic> wrapper = new LambdaQueryWrapper<VocTopic>()
                .eq(VocTopic::getTopicName, name)
                .ne(excludeId != null, VocTopic::getId, excludeId);
        Long count = vocTopicMapper.selectCount(wrapper);
        return count != null && count > 0;
    }

    private String channelsText(Map<String, Integer> channels) {
        if (channels == null || channels.isEmpty()) {
            return null;
        }
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(channels.entrySet());
        entries.sort(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue).reversed());
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, Integer> entry : entries) {
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(entry.getKey()).append(':').append(entry.getValue());
        }
        return builder.toString();
    }

    private Object valueOf(Map<String, Object> row, String key) {
        if (row == null) {
            return null;
        }
        Object value = row.get(key);
        if (value != null) {
            return value;
        }
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private long longOf(Map<String, Object> row, String key) {
        Object value = valueOf(row, key);
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    private Long longOfNullable(Map<String, Object> row, String key) {
        Object value = valueOf(row, key);
        return value instanceof Number ? ((Number) value).longValue() : null;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() > max ? value.substring(0, max) : value;
    }
}
