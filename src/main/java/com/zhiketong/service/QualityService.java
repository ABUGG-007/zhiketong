package com.zhiketong.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiketong.ai.AiChatService;
import com.zhiketong.ai.QualityPrompts;
import com.zhiketong.common.BusinessException;
import com.zhiketong.common.PageResult;
import com.zhiketong.common.ResultCode;
import com.zhiketong.dto.AiChatResponse;
import com.zhiketong.dto.quality.QualityComparisonVO;
import com.zhiketong.dto.quality.QualityOverviewVO;
import com.zhiketong.dto.quality.QualityRecordDetailVO;
import com.zhiketong.dto.quality.QualityRecordVO;
import com.zhiketong.dto.quality.QualitySessionVO;
import com.zhiketong.entity.ChatMessage;
import com.zhiketong.entity.ChatSession;
import com.zhiketong.entity.QualityItem;
import com.zhiketong.entity.QualityRecord;
import com.zhiketong.entity.QualityRule;
import com.zhiketong.entity.SysUser;
import com.zhiketong.enums.QualityEngine;
import com.zhiketong.enums.QualityGrade;
import com.zhiketong.enums.QualityRecordStatus;
import com.zhiketong.enums.QualityRuleType;
import com.zhiketong.enums.SenderType;
import com.zhiketong.enums.SessionStatus;
import com.zhiketong.mapper.ChatMessageMapper;
import com.zhiketong.mapper.ChatSessionMapper;
import com.zhiketong.mapper.QualityItemMapper;
import com.zhiketong.mapper.QualityRecordMapper;
import com.zhiketong.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 智能质检服务（S5-A，QC-01 ~ QC-05）。
 *
 * <p>一次质检由两部分组成，结果合并成同一张评分卡：</p>
 * <ol>
 *   <li><b>规则判定</b>：服务规范类（问候/确认/结束语/超时/禁语）与敏感词类完全本地计算，
 *       关键词来自 {@code quality_rule.keywords}，行为范围限定在坐席消息；</li>
 *   <li><b>大模型判定</b>：语义类（答非所问/未解决/重复提问）与情感类一次调用批量判定，
 *       提示词协议见 {@link QualityPrompts}。大模型不可用或解析失败时整组跳过并标记未命中，
 *       绝不因为「没判出来」而扣分。</li>
 * </ol>
 *
 * <p>评分口径：起点 100，减去命中规则的扣分，下限 0；等级按 A≥90 / B≥80 / C≥70 / D&lt;70；
 * 是否合格由 {@code zhiketong.quality.pass-score}（默认 80）判定，并在记录中留存快照。</p>
 *
 * @author 智客通开发组
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QualityService {

    /** 单条证据最大长度 */
    private static final int MAX_EVIDENCE_LENGTH = 500;

    /** 单条建议最大长度 */
    private static final int MAX_SUGGESTION_LENGTH = 255;

    /** AI 结论最大长度 */
    private static final int MAX_AI_SUMMARY_LENGTH = 1000;

    /** 会话记录中单条消息最大长度 */
    private static final int MAX_DIALOGUE_MESSAGE_LENGTH = 200;

    /** 概览中坐席表现最多展示条数 */
    private static final int MAX_AGENT_ROWS = 10;

    /** 规则命中排行最多展示条数 */
    private static final int MAX_RULE_ROWS = 10;

    /** 人机对比单次最多聚合的记录数，防止超大区间拖垮明细聚合 */
    private static final int MAX_COMPARE_RECORDS = 500;

    /** 分差分布区间：code + 展示文案，顺序即前端展示顺序 */
    private static final String[][] DIFF_BUCKETS = {
            {"SAME", "完全一致"},
            {"UP_1_5", "小幅上调 1-5 分"},
            {"UP_6_15", "明显上调 6-15 分"},
            {"UP_OVER_15", "大幅上调 15 分以上"},
            {"DOWN_1_5", "小幅下调 1-5 分"},
            {"DOWN_6_15", "明显下调 6-15 分"},
            {"DOWN_OVER_15", "大幅下调 15 分以上"}
    };

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final SysUserMapper sysUserMapper;
    private final QualityRecordMapper qualityRecordMapper;
    private final QualityItemMapper qualityItemMapper;
    private final QualityRuleService qualityRuleService;
    private final QualityRecordWriter qualityRecordWriter;
    private final AiChatService aiChatService;

    /** 质检合格线，可在 application.yml 覆盖 */
    @Value("${zhiketong.quality.pass-score:80}")
    private int passScore;

    // ==================================================================
    // 一、质检执行
    // ==================================================================

    /**
     * 对指定会话执行质检（R-168）；已存在记录时覆盖重评。
     *
     * <p>事务边界刻意放在落库阶段（{@link QualityRecordWriter}），
     * 避免大模型调用期间长时间占用数据库连接。</p>
     *
     * @param sessionId  会话 ID
     * @param operatorId 手动质检发起人 ID，定时任务传 null
     * @return 质检结果详情
     */
    public QualityRecordDetailVO evaluate(Long sessionId, Long operatorId) {
        ChatSession session = requireClosedSession(sessionId);
        List<ChatMessage> messages = loadJudgeableMessages(sessionId);
        if (messages.isEmpty()) {
            throw new BusinessException(ResultCode.CONFLICT, "该会话没有可质检的对话消息");
        }
        List<QualityRule> rules = qualityRuleService.listEnabledRules();
        if (rules.isEmpty()) {
            throw new BusinessException(ResultCode.CONFLICT, "未启用任何质检规则，请先在「质检规则」中启用规则");
        }

        QualityRecord existing = findRecord(sessionId);
        if (existing != null && !QualityRecordStatus.PENDING.getCode().equals(existing.getStatus())) {
            throw new BusinessException(ResultCode.CONFLICT, "该会话已完成人工复核，不能重新质检");
        }

        QualityContext context = new QualityContext(messages);
        List<ItemDraft> drafts = new ArrayList<>(rules.size());
        List<QualityRule> aiRules = new ArrayList<>();
        for (QualityRule rule : rules) {
            QualityRuleType type = QualityRuleType.fromCode(rule.getRuleType());
            if (type != null && type.isAiEvaluated()) {
                aiRules.add(rule);
                continue;
            }
            drafts.add(evaluateByRule(rule, context));
        }
        AiEvaluation ai = evaluateByAi(sessionId, aiRules, context);
        drafts.addAll(ai.items);
        drafts.sort(Comparator.comparingInt(draft ->
                draft.rule.getSort() == null ? 0 : draft.rule.getSort()));

        QualityRecord record = buildRecord(session, existing, drafts, ai, operatorId, messages);
        List<QualityItem> items = new ArrayList<>(drafts.size());
        for (ItemDraft draft : drafts) {
            items.add(toItem(draft, session.getId()));
        }
        qualityRecordWriter.save(record, items);
        return recordDetail(record.getId());
    }

    /**
     * 扫描候选：已关闭且尚未产生质检记录的会话（供定时任务跨租户批处理）。
     *
     * <p>必须在 {@code TenantContext.runSystem} 内调用：本方法返回的会话可能来自多个租户，
     * 调用方需逐条用 {@code callAs(session.getTenantId())} 切回租户作用域再质检。
     * 排除已产生记录的会话，保证「一键全量质检」不会重复扣费。</p>
     *
     * @param limit 单轮最多处理条数
     * @return 待质检会话（只含 id 与 tenantId）
     */
    public List<ChatSession> listPendingQualitySessions(int limit) {
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(ChatSession::getId, ChatSession::getTenantId)
                .eq(ChatSession::getStatus, SessionStatus.CLOSED.getCode())
                .notInSql(ChatSession::getId,
                        "SELECT session_id FROM quality_record WHERE deleted = 0")
                .orderByAsc(ChatSession::getId)
                .last("LIMIT " + Math.max(1, limit));
        return chatSessionMapper.selectList(wrapper);
    }

    /**
     * 按规则判定单条规则（服务规范类与敏感词类）。
     */
    private ItemDraft evaluateByRule(QualityRule rule, QualityContext context) {
        QualityRuleType type = QualityRuleType.fromCode(rule.getRuleType());
        if (type == QualityRuleType.SENSITIVE) {
            return evaluateSensitive(rule, context);
        }
        if (type == QualityRuleType.SERVICE) {
            return evaluateService(rule, context);
        }
        return ItemDraft.miss(rule, "规则类型未知，本次跳过");
    }

    /**
     * 敏感词类：在全部可见消息中匹配，命中即记录说话人与原文片段。
     */
    private ItemDraft evaluateSensitive(QualityRule rule, QualityContext context) {
        List<String> keywords = QualityPrompts.splitKeywords(rule.getKeywords());
        if (keywords.isEmpty()) {
            return ItemDraft.miss(rule, "规则未配置关键词，本次跳过");
        }
        for (ChatMessage message : context.messages) {
            String keyword = firstHit(message.getContent(), keywords);
            if (keyword != null) {
                return ItemDraft.hit(rule, speaker(message.getSenderType()) + "「"
                                + snippet(message.getContent(), keyword) + "」命中敏感词「" + keyword + "」",
                        "避免使用「" + keyword + "」等表述", "RULE");
            }
        }
        return ItemDraft.miss(rule, null);
    }

    /**
     * 服务规范类：回复超时走计时判定，其余规则按「坐席消息是否出现关键词」判定。
     */
    private ItemDraft evaluateService(QualityRule rule, QualityContext context) {
        if (QualityRuleService.RULE_SLOW_REPLY.equals(rule.getRuleCode())) {
            return evaluateSlowReply(rule, context);
        }
        if (context.agentMessages.isEmpty()) {
            return ItemDraft.miss(rule, "会话无人工坐席参与，该规则不适用");
        }
        List<String> keywords = QualityPrompts.splitKeywords(rule.getKeywords());
        if (keywords.isEmpty()) {
            return ItemDraft.miss(rule, "规则未配置关键词，本次跳过");
        }
        if (!"ABSENT".equals(rule.getMatchMode())) {
            for (ChatMessage message : context.agentMessages) {
                String keyword = firstHit(message.getContent(), keywords);
                if (keyword != null) {
                    return ItemDraft.hit(rule, "坐席「" + snippet(message.getContent(), keyword)
                                    + "」命中禁语「" + keyword + "」",
                            "改用规范话术回应，避免直接拒绝或推诿", "RULE");
                }
            }
            return ItemDraft.miss(rule, null);
        }
        // ABSENT：约定「问候」只看首条、「结束语」只看末条，其余看全程
        List<ChatMessage> scope = context.agentMessages;
        String code = rule.getRuleCode() == null ? "" : rule.getRuleCode();
        if (code.startsWith("GREETING")) {
            scope = context.agentMessages.subList(0, 1);
        } else if (code.startsWith("CLOSING")) {
            scope = context.agentMessages.subList(context.agentMessages.size() - 1,
                    context.agentMessages.size());
        }
        for (ChatMessage message : scope) {
            if (firstHit(message.getContent(), keywords) != null) {
                return ItemDraft.miss(rule, null);
            }
        }
        return ItemDraft.hit(rule, missingEvidence(code, scope),
                "按「" + previewKeywords(keywords) + "」等要素补全服务话术", "RULE");
    }

    /**
     * 回复超时：访客消息后首条人工或智能回复间隔超过阈值分钟数即命中，只记首次超时。
     */
    private ItemDraft evaluateSlowReply(QualityRule rule, QualityContext context) {
        int threshold = rule.getThresholdValue() == null ? 0 : rule.getThresholdValue();
        if (threshold <= 0) {
            return ItemDraft.miss(rule, "阈值未配置，本次跳过");
        }
        LocalDateTime pendingTime = null;
        for (ChatMessage message : context.messages) {
            String sender = message.getSenderType();
            if (SenderType.USER.getCode().equals(sender)) {
                // 访客连发多条时仍以第一条计时，符合首响口径
                if (pendingTime == null) {
                    pendingTime = message.getCreateTime();
                }
                continue;
            }
            if (pendingTime == null || message.getCreateTime() == null) {
                continue;
            }
            if (SenderType.AI.getCode().equals(sender) || SenderType.AGENT.getCode().equals(sender)) {
                long gap = Duration.between(pendingTime, message.getCreateTime()).toMinutes();
                if (gap > threshold) {
                    return ItemDraft.hit(rule, "访客提问后 " + gap + " 分钟才获得回复（阈值 "
                                    + threshold + " 分钟）",
                            "缩短响应时长，高峰期及时转人工或启用自动应答", "RULE");
                }
                pendingTime = null;
            }
        }
        return ItemDraft.miss(rule, null);
    }

    /**
     * 语义类与情感类：一次大模型调用批量判定，失败整组跳过。
     */
    private AiEvaluation evaluateByAi(Long sessionId, List<QualityRule> aiRules, QualityContext context) {
        AiEvaluation result = new AiEvaluation();
        if (aiRules.isEmpty()) {
            return result;
        }
        if (!aiChatService.isAvailable()) {
            for (QualityRule rule : aiRules) {
                result.items.add(ItemDraft.aiMiss(rule, "未启用大模型，语义与情感规则本次跳过"));
            }
            return result;
        }
        try {
            List<String> labels = new ArrayList<>(aiRules.size());
            for (QualityRule rule : aiRules) {
                labels.add(QualityPrompts.ruleLabel(rule.getRuleCode(), rule.getRuleName(),
                        rule.getDescription()));
            }
            String dialogue = dialogueText(context.messages);
            AiChatResponse response = aiChatService.complete(sessionId,
                    QualityPrompts.QUALITY_SYSTEM_PROMPT,
                    QualityPrompts.evaluatePrompt(labels, dialogue));
            if (response == null || response.isFallback() || !StringUtils.hasText(response.getContent())) {
                for (QualityRule rule : aiRules) {
                    result.items.add(ItemDraft.aiMiss(rule, "大模型调用失败，语义与情感规则本次跳过"));
                }
                return result;
            }
            Map<String, AiRuleResult> parsed = parseAiEvaluation(response.getContent());
            for (QualityRule rule : aiRules) {
                String code = rule.getRuleCode() == null ? "" : rule.getRuleCode().toUpperCase(Locale.ROOT);
                AiRuleResult judged = parsed.get(code);
                if (judged == null) {
                    result.items.add(ItemDraft.aiMiss(rule, "大模型未返回该条结论，按未命中处理"));
                } else if (judged.hit) {
                    result.items.add(ItemDraft.hit(rule,
                            StringUtils.hasText(judged.evidence) ? judged.evidence : "大模型判定命中",
                            StringUtils.hasText(judged.suggestion) ? judged.suggestion : "复盘该轮对话并优化应答",
                            "AI"));
                } else {
                    result.items.add(ItemDraft.aiMiss(rule, null));
                }
            }
            result.participated = true;
            result.model = response.getModel();
        } catch (Exception e) {
            log.warn("大模型质检失败，降级为规则判定：sessionId={}, error={}", sessionId, e.getMessage());
            result.items.clear();
            for (QualityRule rule : aiRules) {
                result.items.add(ItemDraft.aiMiss(rule, "大模型调用异常，语义与情感规则本次跳过"));
            }
            result.participated = false;
            result.model = null;
        }
        return result;
    }

    /**
     * 解析「规则编码|命中|证据|建议」输出，忽略表头与代码块标记。
     */
    private Map<String, AiRuleResult> parseAiEvaluation(String content) {
        Map<String, AiRuleResult> parsed = new HashMap<>();
        for (String rawLine : content.split("\\r?\\n")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("```") || line.startsWith("【")) {
                continue;
            }
            String[] parts = line.split("\\|", -1);
            if (parts.length < 2) {
                continue;
            }
            String code = parts[0].replace("【", "").replace("】", "").trim().toUpperCase(Locale.ROOT);
            if (code.isEmpty()) {
                continue;
            }
            String flag = parts[1].trim();
            boolean hit = "1".equals(flag) || "是".equals(flag) || "命中".equals(flag);
            String evidence = parts.length > 2 ? parts[2].trim() : null;
            String suggestion = parts.length > 3 ? parts[3].trim() : null;
            parsed.put(code, new AiRuleResult(hit, evidence, suggestion));
        }
        return parsed;
    }

    /**
     * 组装质检记录：得分、等级、合格标记与引擎标识。
     */
    private QualityRecord buildRecord(ChatSession session, QualityRecord existing, List<ItemDraft> drafts,
                                      AiEvaluation ai, Long operatorId, List<ChatMessage> messages) {
        QualityRecord record = existing == null ? new QualityRecord() : existing;
        int totalDeduct = 0;
        int hitCount = 0;
        for (ItemDraft draft : drafts) {
            if (draft.hit) {
                hitCount++;
                totalDeduct += draft.deductScore();
            }
        }
        int score = Math.max(0, 100 - totalDeduct);
        QualityGrade grade = QualityGrade.of(score);

        record.setSessionId(session.getId());
        record.setSessionNo(session.getSessionNo());
        record.setCustomerId(session.getUserId());
        record.setAgentId(session.getAgentId());
        record.setAgentName(loadAgentName(session.getAgentId()));
        record.setSessionEndTime(session.getEndTime());
        record.setMessageCount(messages.size());
        record.setCustomerMsgCount(countByType(messages, SenderType.USER));
        record.setAgentMsgCount(countByType(messages, SenderType.AGENT));
        record.setAiMsgCount(countByType(messages, SenderType.AI));
        record.setScore(score);
        record.setGrade(grade.getCode());
        record.setPassScore(passScore);
        record.setPassFlag(score >= passScore ? 1 : 0);
        record.setTotalDeduct(totalDeduct);
        record.setRuleCount(drafts.size());
        record.setHitCount(hitCount);
        record.setEngine(resolveEngine(drafts, ai));
        record.setAiModel(ai.participated ? ai.model : null);
        record.setAiSummary(buildAiSummary(ai));
        record.setStatus(QualityRecordStatus.PENDING.getCode());
        record.setEvaluateBy(operatorId);
        record.setEvaluateTime(LocalDateTime.now());
        return record;
    }

    /**
     * 引擎标识：规则与大模型都参与为 MIX，只有一方参与取对应值。
     */
    private String resolveEngine(List<ItemDraft> drafts, AiEvaluation ai) {
        boolean hasRule = false;
        for (ItemDraft draft : drafts) {
            if (!"AI".equals(draft.source)) {
                hasRule = true;
                break;
            }
        }
        if (ai.participated && hasRule) {
            return QualityEngine.MIX.getCode();
        }
        return ai.participated ? QualityEngine.AI.getCode() : QualityEngine.RULE.getCode();
    }

    /**
     * AI 结论摘要：不额外调用大模型，直接基于判定结果拼装，保证质检成本可控。
     */
    private String buildAiSummary(AiEvaluation ai) {
        if (!ai.participated) {
            return null;
        }
        List<String> hits = new ArrayList<>();
        for (ItemDraft draft : ai.items) {
            if (draft.hit && "AI".equals(draft.source)) {
                hits.add(draft.rule.getRuleName());
            }
        }
        String summary = hits.isEmpty()
                ? "大模型判定：未发现答非所问、重复提问、问题未解决或明显负面情绪。"
                : "大模型判定命中：" + String.join("、", hits) + "。";
        return summary.length() > MAX_AI_SUMMARY_LENGTH ? summary.substring(0, MAX_AI_SUMMARY_LENGTH) : summary;
    }

    /**
     * 明细实体：未命中扣分记 0，保证明细扣分之和等于记录总扣分。
     */
    private QualityItem toItem(ItemDraft draft, Long sessionId) {
        QualityItem item = new QualityItem();
        item.setSessionId(sessionId);
        item.setRuleId(draft.rule.getId());
        item.setRuleCode(draft.rule.getRuleCode());
        item.setRuleName(draft.rule.getRuleName());
        item.setRuleType(draft.rule.getRuleType());
        item.setSeverity(draft.rule.getSeverity());
        item.setHit(draft.hit ? 1 : 0);
        item.setDeductScore(draft.hit ? draft.deductScore() : 0);
        item.setEvidence(draft.hit ? truncate(draft.evidence, MAX_EVIDENCE_LENGTH) : null);
        item.setSuggestion(draft.hit ? truncate(draft.suggestion, MAX_SUGGESTION_LENGTH) : null);
        item.setSource(draft.source);
        return item;
    }

    // ==================================================================
    // 二、查询与统计
    // ==================================================================

    /**
     * 质检记录分页（R-166）。
     *
     * @param keyword   会话编号或坐席名称
     * @param grade     等级 A/B/C/D
     * @param status    复核状态
     * @param agentId   接待坐席
     * @param startDate 质检时间起（yyyy-MM-dd，含当天）
     * @param endDate   质检时间止（yyyy-MM-dd，含当天）
     */
    public PageResult<QualityRecordVO> pageRecords(int pageNum, int pageSize, String keyword, String grade,
                                                   String status, Long agentId,
                                                   String startDate, String endDate) {
        LambdaQueryWrapper<QualityRecord> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(QualityRecord::getSessionNo, kw)
                    .or().like(QualityRecord::getAgentName, kw));
        }
        wrapper.eq(StringUtils.hasText(grade), QualityRecord::getGrade, grade)
                .eq(StringUtils.hasText(status), QualityRecord::getStatus, status)
                .eq(agentId != null, QualityRecord::getAgentId, agentId)
                .ge(QualityRecord::getEvaluateTime, parseStartDate(startDate))
                .lt(QualityRecord::getEvaluateTime, parseEndDateExclusive(endDate))
                .orderByDesc(QualityRecord::getId);
        Page<QualityRecord> page = qualityRecordMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<QualityRecordVO> list = new ArrayList<>(page.getRecords().size());
        for (QualityRecord record : page.getRecords()) {
            list.add(QualityRecordVO.of(record));
        }
        return PageResult.of(page, list);
    }

    /**
     * 质检记录详情（R-167）：评分卡 + 逐规则明细（命中优先展示）。
     */
    public QualityRecordDetailVO recordDetail(Long id) {
        QualityRecord record = qualityRecordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "质检记录不存在");
        }
        QualityRecordDetailVO vo = QualityRecordDetailVO.of(record);
        List<QualityItem> items = qualityItemMapper.selectList(new LambdaQueryWrapper<QualityItem>()
                .eq(QualityItem::getRecordId, record.getId())
                .orderByDesc(QualityItem::getHit)
                .orderByDesc(QualityItem::getDeductScore)
                .orderByAsc(QualityItem::getId));
        for (QualityItem item : items) {
            QualityRecordDetailVO.Item row = new QualityRecordDetailVO.Item();
            row.setId(item.getId());
            row.setRuleId(item.getRuleId());
            row.setRuleCode(item.getRuleCode());
            row.setRuleName(item.getRuleName());
            row.setRuleType(item.getRuleType());
            row.setSeverity(item.getSeverity());
            row.setHit(item.getHit());
            row.setDeductScore(item.getDeductScore());
            row.setEvidence(item.getEvidence());
            row.setSuggestion(item.getSuggestion());
            row.setSource(item.getSource());
            row.setReviewHit(item.getReviewHit());
            row.setReviewDeduct(item.getReviewDeduct());
            row.setEffectiveHit(item.effectiveHit());
            row.setEffectiveDeduct(item.effectiveDeduct());
            row.setOverturned(item.getReviewHit() != null);
            row.fillNames();
            vo.getItems().add(row);
        }
        return vo;
    }

    /**
     * 质检概览（R-169）：合格率、平均分、等级分布、规则命中排行与坐席表现。
     *
     * <p>统计区间默认最近 30 天；未指定区间时同时给出已关闭会话数用于计算覆盖率。</p>
     */
    public QualityOverviewVO overview(String startDate, String endDate) {
        LocalDateTime start = parseStartDate(startDate);
        LocalDateTime end = parseEndDateExclusive(endDate);

        List<QualityRecord> records = qualityRecordMapper.selectList(new LambdaQueryWrapper<QualityRecord>()
                .select(QualityRecord::getId, QualityRecord::getScore, QualityRecord::getPassFlag,
                        QualityRecord::getGrade, QualityRecord::getStatus, QualityRecord::getAgentId,
                        QualityRecord::getAgentName, QualityRecord::getReviewScore,
                        QualityRecord::getPassScore)
                .ge(QualityRecord::getEvaluateTime, start)
                .lt(QualityRecord::getEvaluateTime, end));

        QualityOverviewVO overview = new QualityOverviewVO();
        overview.setRecordCount(records.size());

        long passCount = 0;
        long pendingCount = 0;
        long reviewedCount = 0;
        long confirmedCount = 0;
        long scoreSum = 0;
        Map<String, Long> gradeCounts = new HashMap<>();
        Map<Long, AgentStat> agentStats = new HashMap<>();
        for (QualityRecord record : records) {
            // 复核分优先：人工调分后合格率与均分跟随生效口径变化
            int score = record.effectiveScore();
            scoreSum += score;
            if (record.effectivePassFlag() == 1) {
                passCount++;
            }
            if (QualityRecordStatus.PENDING.getCode().equals(record.getStatus())) {
                pendingCount++;
            } else if (QualityRecordStatus.CONFIRMED.getCode().equals(record.getStatus())) {
                confirmedCount++;
            } else {
                reviewedCount++;
            }
            gradeCounts.merge(QualityGrade.of(score).getCode(), 1L, Long::sum);
            if (record.getAgentId() != null) {
                AgentStat stat = agentStats.computeIfAbsent(record.getAgentId(),
                        id -> new AgentStat(id, record.getAgentName()));
                stat.recordCount++;
                stat.scoreSum += score;
                if (record.effectivePassFlag() != 1) {
                    stat.failCount++;
                }
            }
        }
        overview.setPassCount(passCount);
        overview.setFailCount(records.size() - passCount);
        overview.setPendingCount(pendingCount);
        overview.setReviewedCount(reviewedCount);
        overview.setConfirmedCount(confirmedCount);
        overview.setPassRate(ratio(passCount, records.size()));
        overview.setAvgScore(records.isEmpty() ? 0 : round1((double) scoreSum / records.size()));
        overview.setRuleCount(qualityRuleService.listEnabledRules().size());

        for (QualityGrade grade : QualityGrade.values()) {
            QualityOverviewVO.GradeRow row = new QualityOverviewVO.GradeRow();
            row.setGrade(grade.getCode());
            row.setGradeName(grade.getDescription());
            row.setCount(gradeCounts.getOrDefault(grade.getCode(), 0L));
            overview.getGrades().add(row);
        }

        overview.setClosedSessionCount(chatSessionMapper.selectCount(new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getStatus, SessionStatus.CLOSED.getCode())
                .ge(ChatSession::getCreateTime, start)
                .lt(ChatSession::getCreateTime, end)));
        overview.setCoverageRate(ratio(records.size(), overview.getClosedSessionCount()));

        overview.setRules(loadRuleHitRank(start, end));
        overview.setAgents(toAgentRows(agentStats));
        return overview;
    }

    /**
     * 人机质检对比报表（R-175，QC-07）：自动分与复核分的差异、逐规则改判与一致率。
     *
     * <p>对比样本限定为「已复核 / 已确认」的记录：待复核记录的复核分尚不存在，
     * 计入分母会虚假拉低一致率。区间口径与概览一致，按质检时间（evaluate_time）过滤。</p>
     *
     * @param startDate 质检时间起（yyyy-MM-dd，含当天）
     * @param endDate   质检时间止（yyyy-MM-dd，含当天）
     * @return 人机对比报表
     */
    public QualityComparisonVO comparison(String startDate, String endDate) {
        LocalDateTime start = parseStartDate(startDate);
        LocalDateTime end = parseEndDateExclusive(endDate);

        List<QualityRecord> records = qualityRecordMapper.selectList(new LambdaQueryWrapper<QualityRecord>()
                .select(QualityRecord::getId, QualityRecord::getScore, QualityRecord::getPassFlag,
                        QualityRecord::getReviewScore, QualityRecord::getPassScore, QualityRecord::getStatus)
                .ge(QualityRecord::getEvaluateTime, start)
                .lt(QualityRecord::getEvaluateTime, end)
                .orderByDesc(QualityRecord::getId));

        QualityComparisonVO vo = new QualityComparisonVO();
        vo.setRecordCount(records.size());

        long autoPass = 0;
        long reviewPass = 0;
        long autoSum = 0;
        long reviewSum = 0;
        long diffSum = 0;
        long sameCount = 0;
        long upCount = 0;
        long downCount = 0;
        long reviewedCount = 0;
        Map<String, Long> statusCounts = new HashMap<>();
        Map<String, Long> diffCounts = new HashMap<>();
        List<Long> sampleIds = new ArrayList<>();
        for (QualityRecord record : records) {
            statusCounts.merge(record.getStatus(), 1L, Long::sum);
            if (Integer.valueOf(1).equals(record.getPassFlag())) {
                autoPass++;
            }
            if (QualityRecordStatus.PENDING.getCode().equals(record.getStatus())) {
                continue;
            }
            reviewedCount++;
            int autoScore = record.getScore() == null ? 0 : record.getScore();
            int reviewScore = record.effectiveScore();
            int diff = reviewScore - autoScore;
            autoSum += autoScore;
            reviewSum += reviewScore;
            diffSum += diff;
            if (diff > 0) {
                upCount++;
            } else if (diff < 0) {
                downCount++;
            } else {
                sameCount++;
            }
            if (record.effectivePassFlag() == 1) {
                reviewPass++;
            }
            diffCounts.merge(diffBucket(diff), 1L, Long::sum);
            if (sampleIds.size() < MAX_COMPARE_RECORDS) {
                sampleIds.add(record.getId());
            }
        }

        vo.setReviewedCount(reviewedCount);
        vo.setPendingCount(statusCounts.getOrDefault(QualityRecordStatus.PENDING.getCode(), 0L));
        vo.setAutoPassRate(ratio(autoPass, records.size()));
        vo.setReviewPassRate(ratio(reviewPass, reviewedCount));
        vo.setAvgAutoScore(reviewedCount == 0 ? 0 : round1((double) autoSum / reviewedCount));
        vo.setAvgReviewScore(reviewedCount == 0 ? 0 : round1((double) reviewSum / reviewedCount));
        vo.setAvgDiff(reviewedCount == 0 ? 0 : round1((double) diffSum / reviewedCount));
        vo.setSameCount(sameCount);
        vo.setUpCount(upCount);
        vo.setDownCount(downCount);
        vo.setConsistencyRate(ratio(sameCount, reviewedCount));

        for (String[] bucket : DIFF_BUCKETS) {
            QualityComparisonVO.DiffRow row = new QualityComparisonVO.DiffRow();
            row.setBucket(bucket[0]);
            row.setLabel(bucket[1]);
            row.setCount(diffCounts.getOrDefault(bucket[0], 0L));
            vo.getDiffs().add(row);
        }
        for (QualityRecordStatus status : QualityRecordStatus.values()) {
            QualityComparisonVO.StatusRow row = new QualityComparisonVO.StatusRow();
            row.setStatus(status.getCode());
            row.setStatusName(status.getDescription());
            row.setCount(statusCounts.getOrDefault(status.getCode(), 0L));
            vo.getStatuses().add(row);
        }
        fillItemComparison(vo, sampleIds);
        return vo;
    }

    /**
     * 逐规则人机对比：统计自动命中、改判后命中与被改判条数。
     *
     * <p>明细一致率 = 1 - 被改判条数 / 明细总条数，口径与记录级一致率保持一致。</p>
     */
    private void fillItemComparison(QualityComparisonVO vo, List<Long> recordIds) {
        if (recordIds.isEmpty()) {
            return;
        }
        QueryWrapper<QualityItem> totalWrapper = new QueryWrapper<>();
        totalWrapper.select("COUNT(*) AS total",
                        "SUM(review_hit IS NOT NULL AND review_hit <> hit) AS overturn")
                .in("record_id", recordIds);
        List<Map<String, Object>> totals = qualityItemMapper.selectMaps(totalWrapper);
        long itemTotal = 0;
        long overturn = 0;
        if (!totals.isEmpty()) {
            itemTotal = asLong(totals.get(0).get("total"));
            overturn = asLong(totals.get(0).get("overturn"));
        }
        vo.setItemTotal(itemTotal);
        vo.setItemOverturn(overturn);
        vo.setItemAgreeRate(ratio(itemTotal - overturn, itemTotal));

        QueryWrapper<QualityItem> wrapper = new QueryWrapper<>();
        wrapper.select("rule_code", "rule_name", "rule_type",
                        "SUM(hit = 1) AS auto_hit",
                        "SUM(IFNULL(review_hit, hit) = 1) AS review_hit",
                        "SUM(review_hit IS NOT NULL AND review_hit <> hit) AS overturn",
                        "COUNT(*) AS total")
                .in("record_id", recordIds)
                .groupBy("rule_code", "rule_name", "rule_type")
                .orderByDesc("overturn")
                .orderByDesc("total")
                .last("LIMIT " + MAX_RULE_ROWS);
        for (Map<String, Object> row : qualityItemMapper.selectMaps(wrapper)) {
            QualityComparisonVO.RuleRow item = new QualityComparisonVO.RuleRow();
            item.setRuleCode(asString(row.get("rule_code")));
            item.setRuleName(asString(row.get("rule_name")));
            item.setRuleType(asString(row.get("rule_type")));
            QualityRuleType type = QualityRuleType.fromCode(item.getRuleType());
            item.setRuleTypeName(type == null ? item.getRuleType() : type.getDescription());
            item.setAutoHitCount(asLong(row.get("auto_hit")));
            item.setReviewHitCount(asLong(row.get("review_hit")));
            long rowOverturn = asLong(row.get("overturn"));
            long rowTotal = asLong(row.get("total"));
            item.setOverturnCount(rowOverturn);
            item.setAgreeRate(ratio(rowTotal - rowOverturn, rowTotal));
            vo.getRules().add(item);
        }
    }

    /**
     * 分差归档：正数为人工上调、负数为人工下调，零为完全一致。
     */
    private String diffBucket(int diff) {
        if (diff == 0) {
            return "SAME";
        }
        String prefix = diff > 0 ? "UP_" : "DOWN_";
        int abs = Math.abs(diff);
        if (abs <= 5) {
            return prefix + "1_5";
        }
        return abs <= 15 ? prefix + "6_15" : prefix + "OVER_15";
    }

    /**
     * 规则命中排行：按命中次数倒序，最多 10 条。
     */
    private List<QualityOverviewVO.RuleHitRow> loadRuleHitRank(LocalDateTime start, LocalDateTime end) {
        QueryWrapper<QualityItem> wrapper = new QueryWrapper<>();
        wrapper.select("rule_code", "rule_name", "rule_type", "COUNT(*) AS cnt")
                .eq("hit", 1)
                .ge("create_time", start)
                .lt("create_time", end)
                .groupBy("rule_code", "rule_name", "rule_type")
                .orderByDesc("cnt")
                .last("LIMIT " + MAX_RULE_ROWS);
        List<Map<String, Object>> rows = qualityItemMapper.selectMaps(wrapper);
        List<QualityOverviewVO.RuleHitRow> list = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            QualityOverviewVO.RuleHitRow item = new QualityOverviewVO.RuleHitRow();
            item.setRuleCode(asString(row.get("rule_code")));
            item.setRuleName(asString(row.get("rule_name")));
            item.setRuleType(asString(row.get("rule_type")));
            QualityRuleType type = QualityRuleType.fromCode(item.getRuleType());
            item.setRuleTypeName(type == null ? item.getRuleType() : type.getDescription());
            item.setHitCount(asLong(row.get("cnt")));
            list.add(item);
        }
        return list;
    }

    /**
     * 坐席质检表现：平均分升序，分数最低的排前面，最多 10 条。
     */
    private List<QualityOverviewVO.AgentRow> toAgentRows(Map<Long, AgentStat> stats) {
        List<AgentStat> sorted = new ArrayList<>(stats.values());
        sorted.sort(Comparator.comparingDouble(AgentStat::avgScore));
        List<QualityOverviewVO.AgentRow> rows = new ArrayList<>(sorted.size());
        for (AgentStat stat : sorted) {
            if (rows.size() >= MAX_AGENT_ROWS) {
                break;
            }
            QualityOverviewVO.AgentRow row = new QualityOverviewVO.AgentRow();
            row.setAgentId(stat.agentId);
            row.setAgentName(stat.agentName);
            row.setRecordCount(stat.recordCount);
            row.setAvgScore(stat.avgScore());
            row.setFailCount(stat.failCount);
            rows.add(row);
        }
        return rows;
    }

    /**
     * 可质检会话分页（R-171）：只列已关闭会话，并带出已有质检记录。
     */
    public PageResult<QualitySessionVO> pageClosedSessions(int pageNum, int pageSize, String keyword) {
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(ChatSession::getSessionNo, kw)
                    .or().eq(ChatSession::getId, parseIdOrNegative(kw)));
        }
        wrapper.eq(ChatSession::getStatus, SessionStatus.CLOSED.getCode())
                .orderByDesc(ChatSession::getEndTime)
                .orderByDesc(ChatSession::getId);
        Page<ChatSession> page = chatSessionMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);

        Set<Long> sessionIds = new LinkedHashSet<>();
        Set<Long> agentIds = new LinkedHashSet<>();
        for (ChatSession session : page.getRecords()) {
            sessionIds.add(session.getId());
            if (session.getAgentId() != null) {
                agentIds.add(session.getAgentId());
            }
        }
        Map<Long, QualityRecord> records = new HashMap<>();
        if (!sessionIds.isEmpty()) {
            for (QualityRecord record : qualityRecordMapper.selectList(
                    new LambdaQueryWrapper<QualityRecord>().in(QualityRecord::getSessionId, sessionIds))) {
                records.put(record.getSessionId(), record);
            }
        }
        Map<Long, String> agentNames = loadAgentNames(agentIds);

        List<QualitySessionVO> list = new ArrayList<>(page.getRecords().size());
        for (ChatSession session : page.getRecords()) {
            QualitySessionVO vo = new QualitySessionVO();
            vo.setId(session.getId());
            vo.setSessionNo(session.getSessionNo());
            vo.setStatus(session.getStatus());
            vo.setUserId(session.getUserId());
            vo.setAgentId(session.getAgentId());
            vo.setAgentName(agentNames.get(session.getAgentId()));
            vo.setEndTime(session.getEndTime());
            vo.setMessageCount(session.getMessageCount());
            QualityRecord record = records.get(session.getId());
            if (record != null) {
                vo.setRecordId(record.getId());
                vo.setScore(record.getScore());
                vo.setRecordStatus(record.getStatus());
            }
            list.add(vo);
        }
        return PageResult.of(page, list);
    }

    // ==================================================================
    // 三、内部工具
    // ==================================================================

    private ChatSession requireClosedSession(Long sessionId) {
        // selectById 经多租户拦截器自动附加 tenant_id 条件，跨租户访问直接查不到
        ChatSession session = sessionId == null ? null : chatSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "会话不存在");
        }
        if (!SessionStatus.CLOSED.getCode().equals(session.getStatus())) {
            throw new BusinessException(ResultCode.CONFLICT, "仅已关闭的会话可以质检");
        }
        return session;
    }

    /**
     * 参与质检的消息：排除系统欢迎语与内部备注，只保留访客、智能客服与坐席的对外消息。
     */
    private List<ChatMessage> loadJudgeableMessages(Long sessionId) {
        return chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId)
                .in(ChatMessage::getSenderType, SenderType.USER.getCode(),
                        SenderType.AI.getCode(), SenderType.AGENT.getCode())
                .orderByAsc(ChatMessage::getId));
    }

    private QualityRecord findRecord(Long sessionId) {
        return qualityRecordMapper.selectOne(new LambdaQueryWrapper<QualityRecord>()
                .eq(QualityRecord::getSessionId, sessionId)
                .last("LIMIT 1"));
    }

    private int countByType(List<ChatMessage> messages, SenderType senderType) {
        int count = 0;
        for (ChatMessage message : messages) {
            if (senderType.getCode().equals(message.getSenderType())) {
                count++;
            }
        }
        return count;
    }

    private String loadAgentName(Long agentId) {
        if (agentId == null) {
            return null;
        }
        SysUser user = sysUserMapper.selectById(agentId);
        if (user == null) {
            return null;
        }
        return StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername();
    }

    private Map<Long, String> loadAgentNames(Set<Long> agentIds) {
        Map<Long, String> names = new HashMap<>();
        if (agentIds.isEmpty()) {
            return names;
        }
        List<SysUser> users = sysUserMapper.selectBatchIds(agentIds);
        for (SysUser user : users) {
            names.put(user.getId(), StringUtils.hasText(user.getNickname())
                    ? user.getNickname() : user.getUsername());
        }
        return names;
    }

    /**
     * 会话记录文本：说话人 + 内容，供大模型判定与证据引用。
     */
    private String dialogueText(List<ChatMessage> messages) {
        StringBuilder builder = new StringBuilder();
        for (ChatMessage message : messages) {
            String speaker = speaker(message.getSenderType());
            if (speaker == null || !StringUtils.hasText(message.getContent())) {
                continue;
            }
            builder.append(speaker).append('：')
                    .append(truncate(message.getContent().trim(), MAX_DIALOGUE_MESSAGE_LENGTH))
                    .append('\n');
        }
        return builder.toString().trim();
    }

    private String speaker(String senderType) {
        if (SenderType.USER.getCode().equals(senderType)) {
            return "访客";
        }
        if (SenderType.AI.getCode().equals(senderType)) {
            return "智能客服";
        }
        if (SenderType.AGENT.getCode().equals(senderType)) {
            return "坐席";
        }
        return null;
    }

    private String firstHit(String content, List<String> keywords) {
        if (!StringUtils.hasText(content)) {
            return null;
        }
        String text = content.toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase(Locale.ROOT))) {
                return keyword;
            }
        }
        return null;
    }

    /**
     * 截取关键词附近的上下文作为证据，避免整段消息塞进明细。
     */
    private String snippet(String content, String keyword) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        String text = content.trim();
        int index = text.toLowerCase(Locale.ROOT).indexOf(keyword.toLowerCase(Locale.ROOT));
        if (index < 0 || text.length() <= 60) {
            return truncate(text, 60);
        }
        int from = Math.max(0, index - 20);
        int to = Math.min(text.length(), index + keyword.length() + 20);
        String part = text.substring(from, to);
        return (from > 0 ? "…" : "") + part + (to < text.length() ? "…" : "");
    }

    /**
     * 缺失类规则的证据文案：区分首条、末条与全程。
     */
    private String missingEvidence(String ruleCode, List<ChatMessage> scope) {
        if (scope.isEmpty()) {
            return "坐席未产生有效文字消息";
        }
        String content = truncate(scope.get(0).getContent(), 60);
        if (ruleCode.startsWith("GREETING")) {
            return "坐席首次回复为「" + content + "」，未包含问候用语";
        }
        if (ruleCode.startsWith("CLOSING")) {
            return "坐席最后一条消息为「" + content + "」，未包含结束用语";
        }
        return "坐席全程未出现确认或复述类用语，首条消息为「" + content + "」";
    }

    private String previewKeywords(List<String> keywords) {
        int limit = Math.min(3, keywords.size());
        return String.join("/", keywords.subList(0, limit));
    }

    private LocalDateTime parseStartDate(String startDate) {
        if (!StringUtils.hasText(startDate)) {
            return LocalDate.now().minusDays(29).atStartOfDay();
        }
        try {
            return LocalDate.parse(startDate.trim(), DATE_FORMATTER).atStartOfDay();
        } catch (DateTimeParseException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "开始日期格式应为 yyyy-MM-dd");
        }
    }

    private LocalDateTime parseEndDateExclusive(String endDate) {
        if (!StringUtils.hasText(endDate)) {
            return LocalDate.now().plusDays(1).atStartOfDay();
        }
        try {
            return LocalDate.parse(endDate.trim(), DATE_FORMATTER).plusDays(1).atStartOfDay();
        } catch (DateTimeParseException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "结束日期格式应为 yyyy-MM-dd");
        }
    }

    /**
     * 关键词既能当会话号也能当会话 ID 用：非数字时给一个不可能命中的负数。
     */
    private Long parseIdOrNegative(String keyword) {
        try {
            return Long.parseLong(keyword);
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    private double ratio(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0;
        }
        return round1((double) numerator * 100 / denominator);
    }

    private double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private long asLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return 0L;
    }

    private String truncate(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    /**
     * 一次质检的会话上下文：预分组坐席消息，避免规则判定反复遍历。
     */
    private static final class QualityContext {

        private final List<ChatMessage> messages;

        private final List<ChatMessage> agentMessages;

        private QualityContext(List<ChatMessage> messages) {
            this.messages = messages;
            List<ChatMessage> agents = new ArrayList<>();
            for (ChatMessage message : messages) {
                if (SenderType.AGENT.getCode().equals(message.getSenderType())
                        && StringUtils.hasText(message.getContent())) {
                    agents.add(message);
                }
            }
            this.agentMessages = agents;
        }
    }

    /**
     * 单条规则的判定草稿。
     */
    private static final class ItemDraft {

        private final QualityRule rule;

        private final boolean hit;

        private final String evidence;

        private final String suggestion;

        private final String source;

        private ItemDraft(QualityRule rule, boolean hit, String evidence, String suggestion, String source) {
            this.rule = rule;
            this.hit = hit;
            this.evidence = evidence;
            this.suggestion = suggestion;
            this.source = source;
        }

        static ItemDraft hit(QualityRule rule, String evidence, String suggestion, String source) {
            return new ItemDraft(rule, true, evidence, suggestion, source);
        }

        static ItemDraft miss(QualityRule rule, String evidence) {
            return new ItemDraft(rule, false, evidence, null, "RULE");
        }

        /**
         * 大模型判定为未命中：来源仍记 AI，便于在引擎标识与人机对比中区分。
         */
        static ItemDraft aiMiss(QualityRule rule, String evidence) {
            return new ItemDraft(rule, false, evidence, null, "AI");
        }

        int deductScore() {
            return rule.getDeductScore() == null ? 0 : rule.getDeductScore();
        }
    }

    /**
     * 大模型判定结果集合。
     */
    private static final class AiEvaluation {

        private final List<ItemDraft> items = new ArrayList<>();

        private boolean participated;

        private String model;
    }

    /**
     * 单条 AI 判定结果。
     */
    private static final class AiRuleResult {

        private final boolean hit;

        private final String evidence;

        private final String suggestion;

        private AiRuleResult(boolean hit, String evidence, String suggestion) {
            this.hit = hit;
            this.evidence = evidence;
            this.suggestion = suggestion;
        }
    }

    /**
     * 坐席质检聚合中间态。
     */
    private static final class AgentStat {

        private final Long agentId;

        private final String agentName;

        private long recordCount;

        private long failCount;

        private long scoreSum;

        private AgentStat(Long agentId, String agentName) {
            this.agentId = agentId;
            this.agentName = agentName;
        }

        private double avgScore() {
            return recordCount == 0 ? 0 : round1((double) scoreSum / recordCount);
        }

        private static double round1(double value) {
            return Math.round(value * 10) / 10.0;
        }
    }
}
