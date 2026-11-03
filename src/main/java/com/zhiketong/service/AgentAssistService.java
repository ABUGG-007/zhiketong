package com.zhiketong.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiketong.ai.AiChatService;
import com.zhiketong.ai.ReplyPrompts;
import com.zhiketong.common.BusinessException;
import com.zhiketong.common.ResultCode;
import com.zhiketong.dto.AiChatResponse;
import com.zhiketong.dto.assist.RecommendationVO;
import com.zhiketong.dto.assist.SessionBriefVO;
import com.zhiketong.dto.assist.SessionSummaryVO;
import com.zhiketong.dto.assist.SummarySaveRequest;
import com.zhiketong.entity.ChatMessage;
import com.zhiketong.entity.ChatSession;
import com.zhiketong.entity.SessionSummary;
import com.zhiketong.entity.WorkOrder;
import com.zhiketong.enums.CloseReason;
import com.zhiketong.enums.OrderPriority;
import com.zhiketong.enums.OrderStatus;
import com.zhiketong.enums.SenderType;
import com.zhiketong.enums.SessionStatus;
import com.zhiketong.enums.SummarySource;
import com.zhiketong.enums.SummaryStatus;
import com.zhiketong.enums.SummaryType;
import com.zhiketong.mapper.ChatMessageMapper;
import com.zhiketong.mapper.ChatSessionMapper;
import com.zhiketong.mapper.SessionSummaryMapper;
import com.zhiketong.mapper.WorkOrderMapper;
import com.zhiketong.security.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 坐席辅助服务（R-104 ~ R-109）：实时推荐答案、进线摘要、服务小结。
 *
 * <p>三条硬约束：</p>
 * <ol>
 *   <li>所有 AI 产出只作为草稿，坐席确认后才对客生效，禁止自动发送；</li>
 *   <li>推荐答案优先使用知识库命中（带来源与可信度），未命中才由大模型起草；</li>
 *   <li>大模型不可用或调用失败时按会话事实做规则兜底，保证坐席面板永远有内容。</li>
 * </ol>
 *
 * <p>本类不注入 {@link ChatService}，避免与其转人工链路形成循环依赖。</p>
 *
 * @author 智客通开发组
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentAssistService {

    /** 推荐来源：知识库命中 */
    private static final String SOURCE_KB = "KB";

    /** 推荐来源：大模型起草 */
    private static final String SOURCE_AI = "AI";

    /** 推荐来源：无可用建议 */
    private static final String SOURCE_NONE = "NONE";

    /** 推荐答案默认条数 */
    private static final int DEFAULT_RECOMMEND_LIMIT = 3;

    /** 推荐答案最大条数 */
    private static final int MAX_RECOMMEND_LIMIT = 5;

    /** 推荐时携带的最近对话条数 */
    private static final int RECOMMEND_CONTEXT_MESSAGES = 8;

    /** 进线摘要使用的最近对话条数 */
    private static final int BRIEF_MESSAGE_LIMIT = 12;

    /** 服务小结使用的最近对话条数 */
    private static final int SUMMARY_MESSAGE_LIMIT = 40;

    /** 单条对话在提示词中的最大长度 */
    private static final int MAX_DIALOGUE_MESSAGE_LENGTH = 300;

    /** 大模型原始输出落库最大长度 */
    private static final int MAX_RAW_CONTENT_LENGTH = 4000;

    /** 近期工单最多展示条数 */
    private static final int RECENT_ORDER_LIMIT = 3;

    /** 关键词最多条数 */
    private static final int MAX_KEYWORDS = 8;

    /** 摘要字段标签 */
    private static final String LABEL_DEMAND = "客户诉求";

    private static final String LABEL_PROCESS = "服务过程";

    private static final String LABEL_RESULT = "处理结果";

    private static final String LABEL_FOLLOW_UP = "待办事项";

    private static final String LABEL_KEYWORDS = "关键词";

    private static final List<String> SECTION_LABELS = Collections.unmodifiableList(
            Arrays.asList(LABEL_DEMAND, LABEL_PROCESS, LABEL_RESULT, LABEL_FOLLOW_UP, LABEL_KEYWORDS));

    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final SessionSummaryMapper sessionSummaryMapper;
    private final WorkOrderMapper workOrderMapper;
    private final KnowledgeMatcher knowledgeMatcher;
    private final AiChatService aiChatService;
    private final AgentSessionGuard agentSessionGuard;

    /**
     * 实时推荐答案（R-104）：知识库命中优先，未命中由大模型起草，均只作为草稿返回。
     *
     * @param sessionId 会话 ID
     * @param loginUser 当前登录用户
     * @param limit     知识库候选条数，1-5
     * @return 推荐结果
     */
    public RecommendationVO recommend(Long sessionId, LoginUser loginUser, Integer limit) {
        requireAssistSession(sessionId, loginUser);
        int max = limit == null ? DEFAULT_RECOMMEND_LIMIT : Math.min(Math.max(limit, 1), MAX_RECOMMEND_LIMIT);

        RecommendationVO vo = new RecommendationVO();
        vo.setSessionId(sessionId);
        vo.setGeneratedAt(LocalDateTime.now());

        ChatMessage latest = latestUserMessage(sessionId);
        if (latest == null || !StringUtils.hasText(latest.getContent())) {
            vo.setAnswerSource(SOURCE_NONE);
            vo.setDirectHit(false);
            vo.setNote("访客尚未发送消息，暂无推荐");
            return vo;
        }
        String query = latest.getContent();
        vo.setQuery(query);

        List<RecommendationVO.Item> items = knowledgeItems(query, max);
        if (!items.isEmpty()) {
            double bestScore = items.get(0).getScore();
            boolean direct = bestScore >= knowledgeMatcher.getDirectAnswerThreshold();
            vo.setItems(items);
            vo.setAnswerSource(SOURCE_KB);
            vo.setDirectHit(direct);
            vo.setNote(direct
                    ? "知识库已精准命中，引用后请核对与客户问题一致再发送"
                    : "知识库弱命中，请核对答案后引用，必要时再补充说明");
            return vo;
        }

        AiChatResponse draft = aiDraft(sessionId, query);
        if (draft != null) {
            vo.setAnswerSource(SOURCE_AI);
            vo.setAiDraft(stripPromptResidue(draft.getContent()));
            vo.setModel(draft.getModel());
            vo.setDirectHit(false);
            vo.setNote("AI 起草内容，须坐席确认或修改后再发送");
            return vo;
        }

        vo.setAnswerSource(SOURCE_NONE);
        vo.setDirectHit(false);
        vo.setNote("知识库未命中且大模型暂不可用，请手工回复");
        return vo;
    }

    /**
     * 查询进线摘要（R-105）：不存在时按当前会话即时生成并落库。
     *
     * @param sessionId 会话 ID
     * @param loginUser 当前登录用户
     * @return 进线摘要
     */
    public SessionBriefVO brief(Long sessionId, LoginUser loginUser) {
        ChatSession session = requireAssistSession(sessionId, loginUser);
        SessionSummary record = findSummary(sessionId, SummaryType.BRIEF);
        if (record == null) {
            record = generateBrief(session, null);
        }
        return toBriefVO(record, session);
    }

    /**
     * 重新生成进线摘要（R-106）：覆盖同一条记录，不影响服务小结。
     *
     * @param sessionId 会话 ID
     * @param loginUser 当前登录用户
     * @return 进线摘要
     */
    @Transactional(rollbackFor = Exception.class)
    public SessionBriefVO regenerateBrief(Long sessionId, LoginUser loginUser) {
        ChatSession session = requireAssistSession(sessionId, loginUser);
        SessionSummary record = generateBrief(session, findSummary(sessionId, SummaryType.BRIEF));
        return toBriefVO(record, session);
    }

    /**
     * 转人工后异步生成进线摘要：坐席接起即能看到，失败只记日志不影响转人工主链路。
     *
     * @param sessionId 会话 ID
     */
    @Async
    public void generateBriefAsync(Long sessionId) {
        try {
            ChatSession session = chatSessionMapper.selectById(sessionId);
            if (session == null || findSummary(sessionId, SummaryType.BRIEF) != null) {
                return;
            }
            generateBrief(session, null);
            log.info("进线摘要已异步生成：sessionId={}", sessionId);
        } catch (Exception e) {
            log.warn("异步生成进线摘要失败：sessionId={}, error={}", sessionId, e.getMessage());
        }
    }

    /**
     * 查询服务小结（R-107）：未生成返回 null。
     *
     * @param sessionId 会话 ID
     * @param loginUser 当前登录用户
     * @return 服务小结，可能为 null
     */
    public SessionSummaryVO summary(Long sessionId, LoginUser loginUser) {
        ChatSession session = requireAssistSession(sessionId, loginUser);
        SessionSummary record = findSummary(sessionId, SummaryType.SUMMARY);
        return record == null ? null : toSummaryVO(record, session);
    }

    /**
     * 生成服务小结草稿（R-108）：AI 生成后仍为 DRAFT，需坐席确认归档。
     *
     * @param sessionId 会话 ID
     * @param loginUser 当前登录用户
     * @return 服务小结草稿
     */
    @Transactional(rollbackFor = Exception.class)
    public SessionSummaryVO generateSummary(Long sessionId, LoginUser loginUser) {
        ChatSession session = requireAssistSession(sessionId, loginUser);
        List<ChatMessage> messages = recentMessages(sessionId, SUMMARY_MESSAGE_LIMIT);
        if (messages.isEmpty()) {
            throw new BusinessException(ResultCode.CONFLICT, "会话尚无消息，暂不能生成服务小结");
        }
        SessionSummary record = generateSummaryRecord(session, messages,
                findSummary(sessionId, SummaryType.SUMMARY), loginUser.getUserId());
        return toSummaryVO(record, session);
    }

    /**
     * 保存并采纳服务小结（R-109）：坐席确认后置为 ADOPTED 归档。
     *
     * @param sessionId 会话 ID
     * @param loginUser 当前登录用户
     * @param request   小结内容
     * @return 服务小结
     */
    @Transactional(rollbackFor = Exception.class)
    public SessionSummaryVO saveSummary(Long sessionId, LoginUser loginUser, SummarySaveRequest request) {
        ChatSession session = requireAssistSession(sessionId, loginUser);
        if (SessionStatus.AI.getCode().equals(session.getStatus())
                || SessionStatus.WAITING.getCode().equals(session.getStatus())) {
            throw new BusinessException(ResultCode.CONFLICT, "会话尚未进入人工接待，暂不能归档服务小结");
        }
        String demand = trimToNull(request.getCustomerDemand());
        String process = trimToNull(request.getServiceProcess());
        String result = trimToNull(request.getResult());
        String followUp = trimToNull(request.getFollowUp());
        if (demand == null && process == null && result == null && followUp == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "服务小结内容不能为空");
        }

        SessionSummary record = findSummary(sessionId, SummaryType.SUMMARY);
        if (record == null) {
            record = new SessionSummary();
            record.setSessionId(sessionId);
            record.setSummaryType(SummaryType.SUMMARY.getCode());
            record.setCreateBy(loginUser.getUserId());
        }
        record.setCustomerDemand(demand == null ? "" : demand);
        record.setServiceProcess(process == null ? "" : process);
        record.setResult(result == null ? "" : result);
        record.setFollowUp(followUp == null ? "" : followUp);
        record.setKeywords(joinKeywords(request.getKeywords()));
        record.setSource(SummarySource.AGENT.getCode());
        record.setStatus(SummaryStatus.ADOPTED.getCode());
        record.setUpdateBy(loginUser.getUserId());
        saveRecord(record);
        log.info("服务小结已归档：sessionId={}, operatorId={}", sessionId, loginUser.getUserId());
        return toSummaryVO(record, session);
    }

    /**
     * 知识库推荐候选：低于参考知识下限（0.35）的候选不作为推荐。
     */
    private List<RecommendationVO.Item> knowledgeItems(String query, int limit) {
        List<RecommendationVO.Item> items = new ArrayList<>();
        for (KnowledgeMatcher.MatchResult match : knowledgeMatcher.match(query, null, limit)) {
            if (match.getScore() < knowledgeMatcher.getReferenceThreshold()) {
                continue;
            }
            RecommendationVO.Item item = new RecommendationVO.Item();
            item.setKbId(match.getId());
            item.setQuestion(match.getQuestion());
            item.setMatchedQuestion(match.getMatchedQuestion());
            item.setMatchedType(match.getMatchedType());
            item.setAnswer(match.getAnswer());
            item.setScore(match.getScore());
            item.setHitKeywords(match.getHitKeywords());
            item.setCategoryName(match.getCategoryName());
            item.setAnswerSource("知识库" + (StringUtils.hasText(match.getCategoryName())
                    ? "·" + match.getCategoryName() : ""));
            items.add(item);
        }
        return items;
    }

    /**
     * 大模型起草回复：不可用或失败返回 null，由调用方降级提示坐席手工回复。
     */
    private AiChatResponse aiDraft(Long sessionId, String query) {
        if (!aiChatService.isAvailable()) {
            return null;
        }
        try {
            String dialogue = dialogueText(recentMessages(sessionId, RECOMMEND_CONTEXT_MESSAGES));
            AiChatResponse response = aiChatService.complete(sessionId, ReplyPrompts.ASSIST_SYSTEM_PROMPT,
                    ReplyPrompts.recommendPrompt(query, dialogue));
            if (response == null || response.isFallback() || !StringUtils.hasText(response.getContent())) {
                return null;
            }
            return response;
        } catch (Exception e) {
            log.warn("生成推荐答案失败：sessionId={}, error={}", sessionId, e.getMessage());
            return null;
        }
    }

    /**
     * 生成进线摘要记录：AI 失败或未启用时按会话事实规则兜底。
     */
    private SessionSummary generateBrief(ChatSession session, SessionSummary existing) {
        List<ChatMessage> messages = recentMessages(session.getId(), BRIEF_MESSAGE_LIMIT);
        SessionSummary current = existing == null
                ? findSummary(session.getId(), SummaryType.BRIEF) : existing;
        SessionSummary record = current == null ? new SessionSummary() : current;
        record.setSessionId(session.getId());
        record.setSummaryType(SummaryType.BRIEF.getCode());
        record.setStatus(SummaryStatus.DRAFT.getCode());

        boolean generated = false;
        if (!messages.isEmpty() && aiChatService.isAvailable()) {
            try {
                AiChatResponse response = aiChatService.complete(session.getId(),
                        ReplyPrompts.ASSIST_SYSTEM_PROMPT,
                        ReplyPrompts.briefPrompt(dialogueText(messages), orderSummary(session)));
                if (response != null && !response.isFallback() && StringUtils.hasText(response.getContent())) {
                    applySections(record, response.getContent());
                    generated = StringUtils.hasText(record.getCustomerDemand());
                    if (generated) {
                        record.setSource(SummarySource.AI.getCode());
                        record.setModel(response.getModel());
                        record.setRawContent(abbreviate(response.getContent(), MAX_RAW_CONTENT_LENGTH));
                    }
                }
            } catch (Exception e) {
                log.warn("AI 生成进线摘要失败，转规则兜底：sessionId={}, error={}",
                        session.getId(), e.getMessage());
            }
        }
        if (!generated) {
            fillRuleBrief(record, session, messages);
        }
        saveRecord(record);
        return record;
    }

    /**
     * 生成服务小结记录：AI 失败或未启用时按会话事实规则兜底。
     */
    private SessionSummary generateSummaryRecord(ChatSession session, List<ChatMessage> messages,
                                                 SessionSummary existing, Long operatorId) {
        SessionSummary record = existing == null ? new SessionSummary() : existing;
        record.setSessionId(session.getId());
        record.setSummaryType(SummaryType.SUMMARY.getCode());
        record.setStatus(SummaryStatus.DRAFT.getCode());
        if (record.getCreateBy() == null) {
            record.setCreateBy(operatorId);
        }
        record.setUpdateBy(operatorId);

        boolean generated = false;
        if (aiChatService.isAvailable()) {
            try {
                AiChatResponse response = aiChatService.complete(session.getId(),
                        ReplyPrompts.ASSIST_SYSTEM_PROMPT,
                        ReplyPrompts.summaryPrompt(dialogueText(messages), orderSummary(session)));
                if (response != null && !response.isFallback() && StringUtils.hasText(response.getContent())) {
                    applySections(record, response.getContent());
                    generated = StringUtils.hasText(record.getCustomerDemand())
                            || StringUtils.hasText(record.getServiceProcess());
                    if (generated) {
                        record.setSource(SummarySource.AI.getCode());
                        record.setModel(response.getModel());
                        record.setRawContent(abbreviate(response.getContent(), MAX_RAW_CONTENT_LENGTH));
                    }
                }
            } catch (Exception e) {
                log.warn("AI 生成服务小结失败，转规则兜底：sessionId={}, error={}",
                        session.getId(), e.getMessage());
            }
        }
        if (!generated) {
            fillRuleSummary(record, session, messages);
        }
        saveRecord(record);
        return record;
    }

    /**
     * 按「标签 + 内容」解析大模型输出，兼容【标签】与「标签：」两种写法。
     */
    private void applySections(SessionSummary record, String content) {
        Map<String, String> sections = parseSections(content);
        record.setCustomerDemand(truncate(stripPromptResidue(sections.get(LABEL_DEMAND)), 500));
        record.setServiceProcess(truncate(stripPromptResidue(sections.get(LABEL_PROCESS)), 1000));
        record.setResult(truncate(stripPromptResidue(sections.get(LABEL_RESULT)), 500));
        record.setFollowUp(truncate(stripPromptResidue(sections.get(LABEL_FOLLOW_UP)), 500));
        record.setKeywords(joinKeywords(splitKeywords(sections.get(LABEL_KEYWORDS))));
    }

    private Map<String, String> parseSections(String content) {
        Map<String, String> sections = new LinkedHashMap<>();
        if (!StringUtils.hasText(content)) {
            return sections;
        }
        String current = null;
        for (String rawLine : content.split("\\r?\\n")) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            String matched = null;
            String value = null;
            for (String label : SECTION_LABELS) {
                if (line.startsWith("【" + label + "】")) {
                    matched = label;
                    value = line.substring(label.length() + 2).trim();
                    break;
                }
                if (line.startsWith(label + "：") || line.startsWith(label + ":")) {
                    matched = label;
                    value = line.substring(label.length() + 1).trim();
                    break;
                }
            }
            if (matched != null) {
                current = matched;
                sections.put(matched, value);
            } else if (current != null) {
                // 标签内换行视为同一段的续写
                sections.put(current, (sections.get(current) + " " + line).trim());
            }
        }
        return sections;
    }

    /**
     * 规则兜底进线摘要：只陈述会话事实，不做推断。
     */
    private void fillRuleBrief(SessionSummary record, ChatSession session, List<ChatMessage> messages) {
        String demand = firstUserContent(messages);
        record.setCustomerDemand(truncate(StringUtils.hasText(demand)
                ? demand : "访客未留下明确文字诉求", 500));
        record.setServiceProcess("访客发送 " + countByType(messages, SenderType.USER)
                + " 条消息，智能客服回复 " + countByType(messages, SenderType.AI)
                + " 条，已转入人工。");
        record.setResult(SessionStatus.HUMAN.getCode().equals(session.getStatus())
                ? "已由坐席接入" : "已转人工，等待坐席接入");
        record.setFollowUp("确认客户诉求后给出处理方案");
        record.setKeywords("");
        record.setSource(SummarySource.RULE.getCode());
        record.setModel("");
        record.setRawContent("");
    }

    /**
     * 规则兜底服务小结：统计消息构成与客户提问，不做推断。
     */
    private void fillRuleSummary(SessionSummary record, ChatSession session, List<ChatMessage> messages) {
        String demand = firstUserContent(messages);
        record.setCustomerDemand(truncate(StringUtils.hasText(demand)
                ? demand : "访客未留下明确文字诉求", 500));

        StringBuilder process = new StringBuilder();
        process.append("会话共 ").append(messages.size()).append(" 条消息（访客 ")
                .append(countByType(messages, SenderType.USER)).append(" / 智能客服 ")
                .append(countByType(messages, SenderType.AI)).append(" / 坐席 ")
                .append(countByType(messages, SenderType.AGENT)).append("）");
        List<String> questions = userQuestions(messages, 3);
        if (!questions.isEmpty()) {
            process.append("；客户主要问题：").append(String.join("；", questions));
        }
        record.setServiceProcess(truncate(process.toString(), 1000));

        if (SessionStatus.CLOSED.getCode().equals(session.getStatus())) {
            CloseReason reason = CloseReason.fromCode(session.getCloseReason());
            record.setResult(reason == null ? "会话已结束" : "会话已结束（" + reason.getDescription() + "）");
        } else {
            record.setResult("人工处理中");
        }
        record.setFollowUp("如客户问题仍未解决，请在工单中继续跟进");
        record.setKeywords("");
        record.setSource(SummarySource.RULE.getCode());
        record.setModel("");
        record.setRawContent("");
    }

    private SessionSummary findSummary(Long sessionId, SummaryType type) {
        return sessionSummaryMapper.selectOne(new LambdaQueryWrapper<SessionSummary>()
                .eq(SessionSummary::getSessionId, sessionId)
                .eq(SessionSummary::getSummaryType, type.getCode())
                .orderByDesc(SessionSummary::getId)
                .last("LIMIT 1"));
    }

    private void saveRecord(SessionSummary record) {
        if (record.getId() == null) {
            record.setTenantId(1L);
            record.setDeleted(0);
            try {
                sessionSummaryMapper.insert(record);
                return;
            } catch (DuplicateKeyException e) {
                // 转人工后的异步摘要与坐席主动刷新可能并发插入，命中唯一键后退化为覆盖更新
                SessionSummary existing = findSummary(record.getSessionId(),
                        SummaryType.fromCode(record.getSummaryType()));
                if (existing == null) {
                    throw e;
                }
                record.setId(existing.getId());
            }
        }
        // 空串而非 null：MyBatis-Plus 默认忽略 null 字段，重新生成时必须覆盖旧内容
        sessionSummaryMapper.updateById(record);
    }

    private ChatMessage latestUserMessage(Long sessionId) {
        return chatMessageMapper.selectOne(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId)
                .eq(ChatMessage::getSenderType, SenderType.USER.getCode())
                .orderByDesc(ChatMessage::getId)
                .last("LIMIT 1"));
    }

    private List<ChatMessage> recentMessages(Long sessionId, int limit) {
        List<ChatMessage> messages = chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId)
                .orderByDesc(ChatMessage::getId)
                .last("LIMIT " + limit));
        Collections.reverse(messages);
        return messages;
    }

    /**
     * 近期工单：优先按会话关联，其次按客户名下工单，最多 3 条。
     */
    private List<WorkOrder> recentOrders(ChatSession session) {
        LambdaQueryWrapper<WorkOrder> wrapper = new LambdaQueryWrapper<WorkOrder>()
                .orderByDesc(WorkOrder::getId)
                .last("LIMIT " + RECENT_ORDER_LIMIT);
        if (session.getUserId() == null) {
            wrapper.eq(WorkOrder::getSessionId, session.getId());
        } else {
            Long sessionId = session.getId();
            Long userId = session.getUserId();
            wrapper.and(w -> w.eq(WorkOrder::getSessionId, sessionId)
                    .or().eq(WorkOrder::getCreatorId, userId));
        }
        return workOrderMapper.selectList(wrapper);
    }

    private String orderSummary(ChatSession session) {
        List<WorkOrder> orders = recentOrders(session);
        if (orders.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (WorkOrder order : orders) {
            OrderStatus status = OrderStatus.fromCode(order.getStatus());
            OrderPriority priority = OrderPriority.fromCode(order.getPriority());
            builder.append(order.getOrderNo()).append(' ')
                    .append(order.getTitle() == null ? "" : order.getTitle())
                    .append('（').append(status == null ? order.getStatus() : status.getDescription())
                    .append(" / ").append(priority == null ? order.getPriority() : priority.getDescription())
                    .append("）\n");
        }
        return builder.toString().trim();
    }

    private String dialogueText(List<ChatMessage> messages) {
        StringBuilder builder = new StringBuilder();
        for (ChatMessage message : messages) {
            String speaker = speaker(message.getSenderType());
            if (speaker == null || !StringUtils.hasText(message.getContent())) {
                continue;
            }
            builder.append(speaker).append('：')
                    .append(abbreviate(message.getContent(), MAX_DIALOGUE_MESSAGE_LENGTH))
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

    private int countByType(List<ChatMessage> messages, SenderType senderType) {
        int count = 0;
        for (ChatMessage message : messages) {
            if (senderType.getCode().equals(message.getSenderType())) {
                count++;
            }
        }
        return count;
    }

    private String firstUserContent(List<ChatMessage> messages) {
        for (ChatMessage message : messages) {
            if (SenderType.USER.getCode().equals(message.getSenderType())
                    && StringUtils.hasText(message.getContent())) {
                return message.getContent().trim();
            }
        }
        return null;
    }

    private List<String> userQuestions(List<ChatMessage> messages, int limit) {
        List<String> questions = new ArrayList<>();
        for (ChatMessage message : messages) {
            if (!SenderType.USER.getCode().equals(message.getSenderType())
                    || !StringUtils.hasText(message.getContent())) {
                continue;
            }
            String content = abbreviate(message.getContent().trim(), 60);
            if (!questions.contains(content)) {
                questions.add(content);
            }
            if (questions.size() >= limit) {
                break;
            }
        }
        return questions;
    }

    private List<String> splitKeywords(String keywords) {
        if (!StringUtils.hasText(keywords)) {
            return new ArrayList<>();
        }
        List<String> list = new ArrayList<>();
        for (String item : keywords.replace('，', ',').replace('、', ',')
                .replace('；', ',').replace(';', ',').split(",")) {
            String keyword = item.trim();
            if (!keyword.isEmpty() && !list.contains(keyword)) {
                list.add(keyword);
            }
            if (list.size() >= MAX_KEYWORDS) {
                break;
            }
        }
        return list;
    }

    private String joinKeywords(List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return "";
        }
        List<String> cleaned = new ArrayList<>();
        for (String item : keywords) {
            String keyword = trimToNull(item);
            if (keyword != null && !cleaned.contains(keyword)) {
                cleaned.add(keyword.length() > 32 ? keyword.substring(0, 32) : keyword);
            }
            if (cleaned.size() >= MAX_KEYWORDS) {
                break;
            }
        }
        String joined = String.join(",", cleaned);
        return joined.length() > 255 ? joined.substring(0, 255) : joined;
    }

    /**
     * 坐席辅助数据权限：ADMIN 不限，AGENT 仅本人接待中的会话。
     */
    private ChatSession requireAssistSession(Long sessionId, LoginUser loginUser) {
        return agentSessionGuard.requireOwned(sessionId, loginUser);
    }

    private SessionBriefVO toBriefVO(SessionSummary record, ChatSession session) {
        SessionBriefVO vo = new SessionBriefVO();
        vo.setId(record.getId());
        vo.setSessionId(record.getSessionId());
        vo.setCustomerDemand(record.getCustomerDemand());
        vo.setServiceProcess(record.getServiceProcess());
        vo.setResult(record.getResult());
        vo.setFollowUp(record.getFollowUp());
        vo.setKeywords(splitKeywords(record.getKeywords()));
        vo.setSource(record.getSource());
        vo.setStatus(record.getStatus());
        vo.setModel(StringUtils.hasText(record.getModel()) ? record.getModel() : null);
        vo.setCreateTime(record.getCreateTime());
        vo.setUpdateTime(record.getUpdateTime());
        vo.setRecentOrders(toRecentOrders(session));
        vo.setNote("进线摘要仅作辅助参考，请以客户实际描述为准");
        return vo;
    }

    private List<SessionBriefVO.RecentOrder> toRecentOrders(ChatSession session) {
        List<SessionBriefVO.RecentOrder> list = new ArrayList<>();
        for (WorkOrder order : recentOrders(session)) {
            SessionBriefVO.RecentOrder item = new SessionBriefVO.RecentOrder();
            item.setId(order.getId());
            item.setOrderNo(order.getOrderNo());
            item.setTitle(order.getTitle());
            item.setStatus(order.getStatus());
            OrderStatus status = OrderStatus.fromCode(order.getStatus());
            item.setStatusName(status == null ? order.getStatus() : status.getDescription());
            item.setPriority(order.getPriority());
            item.setCreateTime(order.getCreateTime());
            list.add(item);
        }
        return list;
    }

    private SessionSummaryVO toSummaryVO(SessionSummary record, ChatSession session) {
        SessionSummaryVO vo = new SessionSummaryVO();
        vo.setId(record.getId());
        vo.setSessionId(record.getSessionId());
        vo.setCustomerDemand(record.getCustomerDemand());
        vo.setServiceProcess(record.getServiceProcess());
        vo.setResult(record.getResult());
        vo.setFollowUp(record.getFollowUp());
        vo.setKeywords(splitKeywords(record.getKeywords()));
        vo.setSource(record.getSource());
        vo.setStatus(record.getStatus());
        vo.setModel(StringUtils.hasText(record.getModel()) ? record.getModel() : null);
        vo.setCreateBy(record.getCreateBy());
        vo.setUpdateBy(record.getUpdateBy());
        vo.setEditable(session != null && (SessionStatus.HUMAN.getCode().equals(session.getStatus())
                || SessionStatus.CLOSED.getCode().equals(session.getStatus())));
        vo.setCreateTime(record.getCreateTime());
        vo.setUpdateTime(record.getUpdateTime());
        return vo;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    /**
     * 清理大模型照抄提示词留下的字数说明，如「（80 字以内）」「(不超过 200 字)」。
     * <p>提示词已禁止夹带此类注释，解析层再做一次兜底，避免坐席看到提示词残留。</p>
     */
    private String stripPromptResidue(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim()
                .replaceAll("[（(]\\s*[^（）()\n]{0,12}?\\d+\\s*字(?:以内|左右|内)?\\s*[）)]", "")
                .replaceAll("[ \\t]{2,}", " ")
                .trim();
    }

    private String truncate(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
    }

    private String abbreviate(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) + "…" : trimmed;
    }
}
