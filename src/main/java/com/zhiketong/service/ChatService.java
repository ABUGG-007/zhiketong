package com.zhiketong.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiketong.ai.AiChatService;
import com.zhiketong.ai.AiMessage;
import com.zhiketong.ai.ReplyPrompts;
import com.zhiketong.common.BusinessException;
import com.zhiketong.common.Constants;
import com.zhiketong.common.PageResult;
import com.zhiketong.common.ResultCode;
import com.zhiketong.config.properties.ChatProperties;
import com.zhiketong.dto.AiChatResponse;
import com.zhiketong.dto.agent.AgentBriefVO;
import com.zhiketong.dto.chat.CloseSessionRequest;
import com.zhiketong.dto.chat.CreateSessionResponse;
import com.zhiketong.dto.chat.MessageVO;
import com.zhiketong.dto.chat.RatingRequest;
import com.zhiketong.dto.chat.SendMessageRequest;
import com.zhiketong.dto.chat.SendMessageResponse;
import com.zhiketong.dto.chat.SessionVO;
import com.zhiketong.dto.chat.TransferRequest;
import com.zhiketong.dto.chat.TransferResponse;
import com.zhiketong.entity.AgentStatus;
import com.zhiketong.entity.ChatMessage;
import com.zhiketong.entity.ChatSession;
import com.zhiketong.entity.SysUser;
import com.zhiketong.enums.AnswerCategory;
import com.zhiketong.enums.CloseReason;
import com.zhiketong.enums.SenderType;
import com.zhiketong.enums.SessionStatus;
import com.zhiketong.mapper.AgentStatusMapper;
import com.zhiketong.mapper.ChatSessionMapper;
import com.zhiketong.mapper.SysUserMapper;
import com.zhiketong.security.LoginUser;
import com.zhiketong.websocket.ChatPushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 会话业务服务：会话生命周期、消息收发、AI 应答编排、结束与评价。
 *
 * <p>状态流转：{@code AI → WAITING → HUMAN → CLOSED}，
 * 另有 {@code AI → HUMAN}（坐席直接接入）与 {@code WAITING → CLOSED}（排队超时）两条合法旁路。</p>
 *
 * @author 智客通开发组
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    /** 会话编号前缀 */
    private static final String SESSION_NO_PREFIX = "CS";

    /** 会话编号时间格式 */
    private static final DateTimeFormatter SESSION_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** 进行中的会话状态 */
    private static final List<String> ACTIVE_STATUSES = Arrays.asList(
            SessionStatus.AI.getCode(),
            SessionStatus.WAITING.getCode(),
            SessionStatus.HUMAN.getCode());

    /** 创建会话时的欢迎语 */
    private static final String WELCOME_CONTENT =
            "您好，我是智客通智能助手，请问有什么可以帮您？如需要人工服务，请回复「转人工」。";

    /**
     * 排队中机器人兜底话术（RB-15）：坐席未接入时先安抚访客并告知问题已记录。
     */
    private static final String QUEUE_FALLBACK_REPLY =
            "人工客服正在为您接入，您的问题我已记录。若还有其他疑问，可以继续描述，我先为您解答。";

    /** 无任何可用答案时的兜底话术保留在 {@link AnswerSupport}，站内与嵌入端共用 */
    /** 单条消息最大长度 */
    private static final int MAX_CONTENT_LENGTH = 2000;

    /** 消息分页默认条数 */
    private static final int DEFAULT_MESSAGE_PAGE_SIZE = 20;

    /** 单用户每秒最大消息数 */
    private static final int MAX_MESSAGES_PER_SECOND = 3;

    /** 消息幂等键有效期（分钟） */
    private static final long DEDUP_TTL_MINUTES = 10;

    /** 降级计数有效期（小时） */
    private static final long FALLBACK_TTL_HOURS = 1;

    /** 统计日期最大跨度（天） */
    private static final long MAX_DATE_SPAN_DAYS = 366;

    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageService chatMessageService;
    private final SysUserMapper sysUserMapper;
    private final AgentStatusMapper agentStatusMapper;
    private final KbEntryService kbEntryService;
    private final KnowledgeMatcher knowledgeMatcher;
    private final AnswerSupport answerSupport;
    private final AiChatService aiChatService;
    private final ChatPushService chatPushService;
    private final ChatProperties chatProperties;
    private final StringRedisTemplate stringRedisTemplate;
    private final QueueService queueService;
    private final AgentDispatcherService agentDispatcherService;
    private final SkillGroupService skillGroupService;
    private final AgentAssistService agentAssistService;
    private final TenantUsageService tenantUsageService;
    private final SessionMetricService sessionMetricService;

    /**
     * 创建会话（R-31）：同一用户同时仅允许一个进行中的会话。
     */
    @Transactional(rollbackFor = Exception.class)
    public CreateSessionResponse createSession(Long userId) {
        // 会话额度按自然月计量，超额时直接拦截并提示升级套餐（S4-B PL-04）
        tenantUsageService.assertQuota(TenantPlanService.METRIC_SESSION);
        ChatSession active = chatSessionMapper.selectOne(new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getUserId, userId)
                .in(ChatSession::getStatus, ACTIVE_STATUSES)
                .orderByDesc(ChatSession::getId)
                .last("LIMIT 1"));
        if (active != null) {
            throw new BusinessException(ResultCode.CONFLICT,
                    "您已有进行中的会话（" + active.getSessionNo() + "），请先结束当前会话");
        }

        ChatSession session = new ChatSession();
        session.setSessionNo(nextSessionNo());
        session.setUserId(userId);
        session.setStatus(SessionStatus.AI.getCode());
        session.setMessageCount(0);
        session.setDeleted(0);
        chatSessionMapper.insert(session);

        ChatMessage welcome = chatMessageService.save(session.getId(), SenderType.AI,
                ChatMessageService.AI_SENDER_ID, WELCOME_CONTENT, "TEXT",
                null, null, "SYSTEM", false);

        ChatSession saved = chatSessionMapper.selectById(session.getId());
        CreateSessionResponse response = new CreateSessionResponse();
        response.setSession(toVO(saved == null ? session : saved));
        response.setWelcomeMessage(chatMessageService.toVO(welcome));
        return response;
    }

    /**
     * 会话分页列表（R-32）：按角色收敛数据权限。
     */
    public PageResult<SessionVO> pageSessions(LoginUser loginUser, Integer pageNum, Integer pageSize,
                                              String status, String keyword, String startDate, String endDate) {
        int num = pageNum == null ? 1 : pageNum;
        int size = pageSize == null ? Constants.DEFAULT_PAGE_SIZE : pageSize;
        validatePage(num, size);

        String statusFilter = trimToNull(status);
        if (statusFilter != null) {
            statusFilter = statusFilter.toUpperCase();
            if (SessionStatus.fromCode(statusFilter) == null) {
                throw new BusinessException(ResultCode.PARAM_ERROR,
                        "会话状态仅支持 " + SessionStatus.valuesDescription());
            }
        }
        String keywordFilter = trimToNull(keyword);
        if (keywordFilter != null && keywordFilter.length() > 50) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "关键词长度不能超过 50");
        }
        LocalDate start = parseDate(startDate);
        LocalDate end = parseDate(endDate);
        validateDateRange(start, end);

        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<>();
        applyDataScope(wrapper, loginUser);
        if (statusFilter != null) {
            wrapper.eq(ChatSession::getStatus, statusFilter);
        }
        if (keywordFilter != null) {
            List<Long> matchedUserIds = matchUserIds(keywordFilter);
            String finalKeyword = keywordFilter;
            wrapper.and(w -> {
                w.like(ChatSession::getSessionNo, finalKeyword);
                if (!matchedUserIds.isEmpty()) {
                    w.or().in(ChatSession::getUserId, matchedUserIds);
                }
            });
        }
        if (start != null) {
            wrapper.ge(ChatSession::getCreateTime, start.atStartOfDay());
        }
        if (end != null) {
            wrapper.lt(ChatSession::getCreateTime, end.plusDays(1).atStartOfDay());
        }
        wrapper.orderByDesc(ChatSession::getCreateTime).orderByDesc(ChatSession::getId);

        Page<ChatSession> page = chatSessionMapper.selectPage(new Page<>(num, size), wrapper);
        return PageResult.of(page, toVOs(page.getRecords()));
    }

    /**
     * 查询本人进行中的会话（R-33），没有时返回 null。
     */
    public SessionVO currentSession(Long userId) {
        ChatSession session = chatSessionMapper.selectOne(new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getUserId, userId)
                .in(ChatSession::getStatus, ACTIVE_STATUSES)
                .orderByDesc(ChatSession::getId)
                .last("LIMIT 1"));
        return session == null ? null : toVO(session);
    }

    /**
     * 会话详情（R-34）。
     */
    public SessionVO detail(Long id, LoginUser loginUser) {
        ChatSession session = requireSession(id);
        checkAccess(session, loginUser);
        return toVO(session);
    }

    /**
     * 会话消息分页（R-35）：afterId 存在时走增量补偿，最多 200 条。
     */
    public PageResult<MessageVO> pageMessages(Long id, LoginUser loginUser, Integer pageNum, Integer pageSize,
                                             Long afterId, boolean markRead) {
        ChatSession session = requireSession(id);
        checkAccess(session, loginUser);
        int num = pageNum == null ? 1 : pageNum;
        int size = pageSize == null ? DEFAULT_MESSAGE_PAGE_SIZE : pageSize;
        validatePage(num, size);
        if (afterId != null && afterId < 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "afterId 不能小于 0");
        }
        return chatMessageService.page(id, num, size, afterId, markRead, loginUser.getRoleCode());
    }

    /**
     * 发送消息（R-36，WebSocket 与 REST 共用）：
     * <ul>
     *   <li>AI 状态：同步执行知识库匹配与大模型应答；</li>
     *   <li>HUMAN 状态：落库并推送给对端；</li>
     *   <li>WAITING 状态：落库，并在开启排队机器人（RB-15）时继续应答。</li>
     * </ul>
     */
    public SendMessageResponse sendMessage(Long sessionId, LoginUser loginUser, SendMessageRequest request) {
        ChatSession session = requireSession(sessionId);
        if (SessionStatus.CLOSED.getCode().equals(session.getStatus())) {
            throw new BusinessException(ResultCode.CONFLICT, "会话已结束，无法继续发送消息");
        }
        SenderType senderType = resolveSenderType(session, loginUser);

        String content = request.getContent() == null ? "" : request.getContent().trim();
        if (content.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "消息内容不能为空");
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "消息内容不能超过 2000 个字符");
        }
        String clientMsgId = trimToNull(request.getClientMsgId());
        if (clientMsgId == null || clientMsgId.length() < 16 || clientMsgId.length() > 64
                || !clientMsgId.matches("^[A-Za-z0-9_-]+$")) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "clientMsgId 须为 16-64 位字母、数字、下划线或连字符");
        }
        String contentType = trimToNull(request.getContentType());
        if (contentType != null && !"TEXT".equalsIgnoreCase(contentType)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "内容类型仅支持 TEXT");
        }
        checkRateLimit(loginUser.getUserId());

        // 幂等：同一会话 10 分钟内相同 clientMsgId 直接返回原消息
        ChatMessage duplicated = findDuplicate(sessionId, clientMsgId);
        if (duplicated != null) {
            log.info("命中消息幂等：sessionId={}, clientMsgId={}, messageId={}",
                    sessionId, clientMsgId, duplicated.getId());
            return buildResponse(session, duplicated, null);
        }

        ChatMessage userMessage = chatMessageService.save(sessionId, senderType, loginUser.getUserId(),
                content, "TEXT", null, null, null, true);
        rememberMessage(sessionId, clientMsgId, userMessage.getId());
        if (SenderType.USER == senderType) {
            // 首响口径：访客首条有效消息计时，连续多条只记第一条（见 docs/产品推进路线图.md 第 3 章）
            markFirstUserMsgTime(sessionId);
        }
        MessageVO userMessageVO = chatMessageService.toVO(userMessage);

        ChatMessage aiReply = null;
        if (SenderType.AGENT == senderType) {
            // 坐席消息推送给用户；坐席首次回复计入首响
            chatPushService.pushAgentMessageToUser(session.getUserId(), userMessageVO);
            markFirstReplyTime(sessionId);
        } else if (SessionStatus.AI.getCode().equals(session.getStatus())) {
            aiReply = replyWithAi(session, userMessage);
            if (aiReply != null) {
                markFirstReplyTime(sessionId);
            }
        } else if (SessionStatus.HUMAN.getCode().equals(session.getStatus())) {
            // 用户消息推送给接入坐席
            chatPushService.pushUserMessageToAgent(session.getAgentId(), userMessageVO);
        } else if (SessionStatus.WAITING.getCode().equals(session.getStatus())
                && SenderType.USER == senderType
                && chatProperties.isWaitingBotEnabled()) {
            // RB-15：排队期继续由机器人应答，避免访客在等待中完全静默
            aiReply = replyWithAi(session, userMessage, true);
            if (aiReply != null) {
                markFirstReplyTime(sessionId);
            }
        }
        // 未开启排队机器人时：仅落库，坐席接入后通过历史消息展示

        ChatSession latest = refreshSession(session);
        return buildResponse(latest, userMessage, aiReply);
    }

    /**
     * 请求转人工（R-37）：AI → WAITING，写转人工时间并入队；
     * 事务提交后尝试自动分配空闲坐席，接入结果通过 WebSocket 推送。
     */
    @Transactional(rollbackFor = Exception.class)
    public TransferResponse transfer(Long id, Long userId, TransferRequest request) {
        ChatSession session = requireSession(id);
        if (userId == null || !userId.equals(session.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作该会话");
        }
        if (SessionStatus.CLOSED.getCode().equals(session.getStatus())) {
            throw new BusinessException(ResultCode.CONFLICT, "会话已结束，无法转人工");
        }
        if (SessionStatus.HUMAN.getCode().equals(session.getStatus())) {
            throw new BusinessException(ResultCode.CONFLICT, "会话已由人工接待，无需重复转人工");
        }
        // 幂等：已在排队中直接返回当前排位，不重复入队
        if (SessionStatus.WAITING.getCode().equals(session.getStatus())) {
            return buildTransferResponse(session);
        }

        String reason = trimToNull(request == null ? null : request.getReason());
        if (reason != null && reason.length() > 100) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "转人工原因不能超过 100 个字符");
        }
        // 咨询类型：访客可指定技能组，分配时优先同组坐席；未指定或组已停用时按不限处理
        Long skillGroupId = resolveSkillGroupId(request == null ? null : request.getSkillGroupId());
        LocalDateTime now = LocalDateTime.now();
        session.setStatus(SessionStatus.WAITING.getCode());
        session.setSkillGroupId(skillGroupId);
        session.setTransferTime(now);
        session.setQueueEnterTime(now);
        chatSessionMapper.updateById(session);

        queueService.enqueue(session, reason);
        // 已进入人工队列，AI 未命中计数不再有意义，重置避免坐席端误读
        resetFallbackCount(session.getId());
        chatMessageService.save(session.getId(), SenderType.SYSTEM, 0L,
                reason == null
                        ? "用户请求转接人工客服，正在排队，可继续向智能客服提问"
                        : "用户请求转接人工客服：" + reason + "（正在排队，可继续向智能客服提问）",
                "TEXT", null, null, "SYSTEM", false);

        int position = Math.max(1, queueService.position(session.getId()));
        int queueSize = Math.max(position, queueService.size());
        chatPushService.pushTransferQueued(userId, session.getId(), position, queueSize,
                Math.max(30, position * 60), agentDispatcherService.hasAvailableAgent());
        dispatchAfterCommit();
        // 事务提交后异步生成进线摘要，坐席接入即可看到客户来意，失败不影响转人工主链路
        generateBriefAfterCommit(session.getId());
        log.info("用户请求转人工：sessionId={}, position={}, queueSize={}", session.getId(), position, queueSize);
        return buildTransferResponse(session);
    }

    /**
     * 校验转人工时选择的咨询类型：必须为启用状态的技能组，空值表示不限。
     */
    private Long resolveSkillGroupId(Long skillGroupId) {
        if (skillGroupId == null) {
            return null;
        }
        if (skillGroupService.activeGroup(skillGroupId) == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "咨询类型不存在或已停用，请刷新后重试");
        }
        return skillGroupId;
    }

    /**
     * 结束会话（R-38）：已结束直接返回当前会话，保证前端重试幂等。
     */
    @Transactional(rollbackFor = Exception.class)
    public SessionVO closeSession(Long id, LoginUser loginUser, CloseSessionRequest request) {
        ChatSession session = requireSession(id);
        if (SessionStatus.CLOSED.getCode().equals(session.getStatus())) {
            return toVO(session);
        }
        checkCloseAccess(session, loginUser);
        // 排队中：用户可取消排队，管理员可强制关闭；坐席尚未接入，不允许结束
        if (SessionStatus.WAITING.getCode().equals(session.getStatus())
                && Constants.ROLE_AGENT.equals(loginUser.getRoleCode())) {
            throw new BusinessException(ResultCode.CONFLICT, "会话尚未接入，坐席无法结束");
        }

        String reasonCode = resolveCloseReason(loginUser, request == null ? null : request.getCloseReason());
        boolean wasHuman = SessionStatus.HUMAN.getCode().equals(session.getStatus());
        session.setStatus(SessionStatus.CLOSED.getCode());
        session.setCloseReason(reasonCode);
        session.setEndTime(LocalDateTime.now());
        chatSessionMapper.updateById(session);

        if (wasHuman && session.getAgentId() != null) {
            releaseAgent(session.getAgentId());
        }
        removeQueue(session.getId());
        resetFallbackCount(session.getId());

        CloseReason reason = CloseReason.fromCode(reasonCode);
        String notice = reason == null ? "会话已结束" : "会话已结束（" + reason.getDescription() + "）";
        String remark = request == null ? null : trimToNull(request.getRemark());
        if (remark != null) {
            notice = notice + "：" + remark;
        }
        chatMessageService.save(session.getId(), SenderType.SYSTEM, 0L, notice, "TEXT",
                null, null, "SYSTEM", false);

        chatPushService.pushSessionClosed(session, loginUser.getRoleCode());
        // 关闭即固化轮次指标（3 分钟回复率口径），避免统计时全量扫描消息表
        sessionMetricService.computeForSession(session);
        return toVO(session);
    }

    /**
     * 排队超时自动关闭，供 M4 队列定时任务调用。
     */
    @Transactional(rollbackFor = Exception.class)
    public void timeoutClose(ChatSession session) {
        if (session == null || SessionStatus.CLOSED.getCode().equals(session.getStatus())) {
            return;
        }
        session.setStatus(SessionStatus.CLOSED.getCode());
        session.setCloseReason(CloseReason.TIMEOUT.getCode());
        session.setEndTime(LocalDateTime.now());
        chatSessionMapper.updateById(session);
        removeQueue(session.getId());
        resetFallbackCount(session.getId());
        chatMessageService.save(session.getId(), SenderType.SYSTEM, 0L,
                "排队等待超时，会话已自动结束，请重新发起咨询。", "TEXT",
                null, null, "SYSTEM", false);
        chatPushService.pushSessionClosed(session, "SYSTEM");
        sessionMetricService.computeForSession(session);
        log.info("会话排队超时关闭：sessionId={}, sessionNo={}", session.getId(), session.getSessionNo());
    }

    /**
     * 会话评分（R-39）：仅本人、已结束且未评价的会话可评。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> rateSession(Long id, Long userId, RatingRequest request) {
        ChatSession session = requireSession(id);
        if (userId == null || !userId.equals(session.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权评价该会话");
        }
        if (!SessionStatus.CLOSED.getCode().equals(session.getStatus())) {
            throw new BusinessException(ResultCode.CONFLICT, "会话结束后才能评价");
        }
        if (session.getRating() != null) {
            throw new BusinessException(ResultCode.CONFLICT, "该会话已评价，不可重复评价");
        }
        if (request == null || request.getRating() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "评分不能为空");
        }
        if (request.getRating() < 1 || request.getRating() > 5) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "评分须为 1-5");
        }
        if (request.getRatingComment() != null && request.getRatingComment().length() > 255) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "评价内容不能超过 255 个字符");
        }
        session.setRating(request.getRating());
        session.setRatingComment(trimToNull(request.getRatingComment()));
        chatSessionMapper.updateById(session);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", session.getId());
        data.put("rating", session.getRating());
        data.put("ratingComment", session.getRatingComment());
        return data;
    }

    /**
     * 查询会话，不存在时抛出 404。
     */
    public ChatSession requireSession(Long id) {
        ChatSession session = id == null ? null : chatSessionMapper.selectById(id);
        if (session == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "会话不存在");
        }
        return session;
    }

    /**
     * 数据权限校验：USER 仅本人会话，AGENT 仅本人接待会话，ADMIN 不限。
     */
    public void checkAccess(ChatSession session, LoginUser loginUser) {
        if (loginUser == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        String role = loginUser.getRoleCode();
        if (Constants.ROLE_ADMIN.equals(role)) {
            return;
        }
        if (Constants.ROLE_USER.equals(role) && loginUser.getUserId().equals(session.getUserId())) {
            return;
        }
        if (Constants.ROLE_AGENT.equals(role) && loginUser.getUserId().equals(session.getAgentId())) {
            return;
        }
        throw new BusinessException(ResultCode.FORBIDDEN, "无权访问该会话");
    }

    /**
     * 读取会话连续降级次数，供 M4 判断是否自动转人工。
     */
    public long getFallbackCount(Long sessionId) {
        try {
            String value = stringRedisTemplate.opsForValue()
                    .get(Constants.SESSION_FALLBACK_PREFIX + sessionId);
            return StringUtils.hasText(value) ? Long.parseLong(value) : 0L;
        } catch (Exception e) {
            log.warn("读取会话降级计数失败：sessionId={}, error={}", sessionId, e.getMessage());
            return 0L;
        }
    }

    /**
     * 会话转 VO。
     */
    public SessionVO toVO(ChatSession session) {
        if (session == null) {
            return null;
        }
        return toVOs(Collections.singletonList(session)).get(0);
    }

    /**
     * AI 应答编排（五分类兜底策略）：
     * <ol>
     *   <li>知识库精准命中直答（DIRECT）；</li>
     *   <li>0.35 ~ 直答阈值之间：参考知识 + 大模型生成（REFERENCE），大模型不可用时推荐相近问法（SUGGEST）；</li>
     *   <li>低于 0.35：大模型自由生成（FREE），失败则兜底话术（FALLBACK）；</li>
     *   <li>闲聊（CHITCHAT）与无效输入（INVALID）走轻量话术，不调用大模型。</li>
     * </ol>
     * 只有 SUGGEST 与 FALLBACK 计入未命中采集，避免闲聊噪声污染知识运营看板。
     */
    private ChatMessage replyWithAi(ChatSession session, ChatMessage userMessage) {
        return replyWithAi(session, userMessage, false);
    }

    /**
     * 机器人应答主流程：直答 → 闲聊/无效轻量应答 → 大模型 → 降级回复。
     *
     * @param queueMode 是否为排队期应答（RB-15）：排队期不计未命中、不写未命中表
     */
    private ChatMessage replyWithAi(ChatSession session, ChatMessage userMessage, boolean queueMode) {
        String content = userMessage.getContent();
        List<KnowledgeMatcher.MatchResult> matches = knowledgeMatcher.match(content, null, 3);
        KnowledgeMatcher.MatchResult best = matches.isEmpty() ? null : matches.get(0);
        double bestScore = best == null ? 0D : best.getScore();
        AnswerCategory category = answerSupport.classify(content, bestScore);

        // 1. 达到直接回答阈值，走知识库直答，无需调用大模型
        if (category == AnswerCategory.DIRECT && best != null) {
            log.info("知识库直答：sessionId={}, kbId={}, score={}, matchedType={}, hitKeywords={}",
                    session.getId(), best.getId(), best.getScore(), best.getMatchedType(), best.getHitKeywords());
            kbEntryService.incrementHitCount(best.getId());
            resetFallbackCount(session.getId());
            ChatMessage message = chatMessageService.save(session.getId(), SenderType.AI,
                    ChatMessageService.AI_SENDER_ID, truncate(best.getAnswer()), "TEXT",
                    best.getId(), confidence(best.getScore()), "kb-direct", true);
            message.setAnswerCategory(category.name());
            pushAiMessage(session, message);
            return message;
        }

        // 2. 闲聊与无效输入：直接轻量话术，不调用大模型也不计入未命中
        if (category == AnswerCategory.CHITCHAT || category == AnswerCategory.INVALID) {
            String reply = category == AnswerCategory.CHITCHAT
                    ? answerSupport.chitchatReply(content) : answerSupport.invalidReply();
            ChatMessage message = chatMessageService.save(session.getId(), SenderType.AI,
                    ChatMessageService.AI_SENDER_ID, reply, "TEXT", null, null,
                    category == AnswerCategory.CHITCHAT ? "chitchat" : "invalid", true);
            message.setAnswerCategory(category.name());
            log.info("AI 轻量应答：sessionId={}, category={}", session.getId(), category);
            pushAiMessage(session, message);
            return message;
        }

        // 3. 调用大模型；0.35 ~ 阈值之间的候选作为参考知识注入提示词
        boolean reference = category == AnswerCategory.SUGGEST;
        if (aiChatService.isAvailable()) {
            String prompt = reference ? buildReferencePrompt(best, content) : content;
            List<AiMessage> history = aiHistoryWithoutLatest(session.getId(), content);
            try {
                AiChatResponse aiResponse = aiChatService.chat(
                        session.getId(), userMessage.getId(), prompt, history);
                if (aiResponse != null && !aiResponse.isFallback()
                        && StringUtils.hasText(aiResponse.getContent())
                        && stillBotSession(session.getId(), queueMode)) {
                    ChatMessage message = chatMessageService.save(session.getId(), SenderType.AI,
                            ChatMessageService.AI_SENDER_ID, truncate(aiResponse.getContent()), "TEXT",
                            reference ? best.getId() : null,
                            reference ? confidence(best.getScore()) : null,
                            aiResponse.getModel(), true);
                    message.setAnswerCategory(reference
                            ? AnswerCategory.REFERENCE.name() : AnswerCategory.FREE.name());
                    resetFallbackCount(session.getId());
                    pushAiMessage(session, message);
                    return message;
                }
                if (aiResponse != null && aiResponse.isFallback()) {
                    log.warn("大模型回复降级：sessionId={}, reason={}",
                            session.getId(), aiResponse.getFallbackReason());
                }
            } catch (Exception e) {
                log.warn("大模型调用异常，转入降级回复：sessionId={}, error={}",
                        session.getId(), e.getMessage());
            }
        }

        // 4. 会话在 AI 思考期间已接入人工/结束，不再追加 AI 回复
        if (!stillBotSession(session.getId(), queueMode)) {
            return null;
        }

        // 5. 降级：弱命中推荐相近问法，否则兜底话术；排队期不计入未命中，避免污染知识运营闭环
        String reply;
        AnswerCategory degraded;
        if (queueMode) {
            degraded = category == AnswerCategory.SUGGEST ? AnswerCategory.SUGGEST : AnswerCategory.FALLBACK;
            reply = degraded == AnswerCategory.SUGGEST
                    ? answerSupport.suggestReply(suggestions(matches))
                    : QUEUE_FALLBACK_REPLY;
        } else {
            int fallbackCount = incrementFallbackCount(session.getId());
            degraded = category == AnswerCategory.SUGGEST ? AnswerCategory.SUGGEST : AnswerCategory.FALLBACK;
            reply = degraded == AnswerCategory.SUGGEST
                    ? answerSupport.suggestReply(suggestions(matches))
                    : answerSupport.fallbackReply();
            log.info("AI 降级回复：sessionId={}, fallbackCount={}, category={}, referenceKbId={}, referenceScore={}",
                    session.getId(), fallbackCount, degraded,
                    degraded == AnswerCategory.SUGGEST ? best.getId() : null,
                    degraded == AnswerCategory.SUGGEST ? best.getScore() : null);
        }
        ChatMessage message = chatMessageService.save(session.getId(), SenderType.AI,
                ChatMessageService.AI_SENDER_ID, truncate(reply), "TEXT",
                degraded == AnswerCategory.SUGGEST ? best.getId() : null,
                degraded == AnswerCategory.SUGGEST ? confidence(best.getScore()) : null,
                queueMode ? "queue-bot"
                        : (degraded == AnswerCategory.SUGGEST ? "kb-suggest" : "fallback-template"), true);
        message.setAnswerCategory(degraded.name());
        if (!queueMode) {
            answerSupport.collect(content, degraded, KbUnmatchedService.CHANNEL_SITE,
                    session.getId(), session.getUserId(), bestScore);
        }
        pushAiMessage(session, message);
        return message;
    }

    /**
     * 从匹配结果中抽取推荐问法（优先实际命中的相似问法），去重后最多 3 条。
     */
    private List<String> suggestions(List<KnowledgeMatcher.MatchResult> matches) {
        List<String> suggestions = new ArrayList<>();
        for (KnowledgeMatcher.MatchResult match : matches) {
            String question = StringUtils.hasText(match.getMatchedQuestion())
                    ? match.getMatchedQuestion() : match.getQuestion();
            if (StringUtils.hasText(question) && !suggestions.contains(question)) {
                suggestions.add(question);
            }
            if (suggestions.size() >= 3) {
                break;
            }
        }
        return suggestions;
    }

    private void pushAiMessage(ChatSession session, ChatMessage message) {
        chatPushService.pushAiMessage(session.getUserId(), chatMessageService.toVO(message));
    }

    /**
     * 组装参考知识提示词：与嵌入端共用 {@link ReplyPrompts}，保证两端编排一致。
     */
    private String buildReferencePrompt(KnowledgeMatcher.MatchResult match, String content) {
        return ReplyPrompts.referencePrompt(match.getQuestion(), match.getAnswer(), content);
    }

    /**
     * 大模型历史上下文去除最后一条当前用户消息，避免与 userMessage 参数重复。
     */
    private List<AiMessage> aiHistoryWithoutLatest(Long sessionId, String latestContent) {
        List<AiMessage> history = chatMessageService.history(sessionId);
        if (history.isEmpty()) {
            return history;
        }
        AiMessage last = history.get(history.size() - 1);
        if ("user".equals(last.getRole()) && latestContent.equals(last.getContent())) {
            return new ArrayList<>(history.subList(0, history.size() - 1));
        }
        return history;
    }

    /**
     * 判断会话是否仍由机器人应答：AI 接待，或排队期机器人接待（RB-15）。
     */
    private boolean stillBotSession(Long sessionId, boolean queueMode) {
        ChatSession latest = chatSessionMapper.selectById(sessionId);
        if (latest == null) {
            return false;
        }
        if (SessionStatus.AI.getCode().equals(latest.getStatus())) {
            return true;
        }
        return queueMode && SessionStatus.WAITING.getCode().equals(latest.getStatus());
    }

    private BigDecimal confidence(double score) {
        return BigDecimal.valueOf(score).setScale(4, RoundingMode.HALF_UP);
    }

    private String truncate(String content) {
        if (content == null) {
            return "";
        }
        return content.length() > MAX_CONTENT_LENGTH ? content.substring(0, MAX_CONTENT_LENGTH) : content;
    }

    private SendMessageResponse buildResponse(ChatSession session, ChatMessage message, ChatMessage aiReply) {
        SendMessageResponse response = new SendMessageResponse();
        response.setMessage(chatMessageService.toVO(message));
        response.setAiReply(aiReply == null ? null : chatMessageService.toVO(aiReply));
        response.setSessionStatus(session.getStatus());
        // 未命中判定以「本轮是否给出有效答案」为准，计数由后端 Redis 统一维护，前端只做展示
        long fallbackCount = getFallbackCount(session.getId());
        response.setFallbackCount(fallbackCount);
        response.setSuggestTransfer(fallbackCount >= chatProperties.getFallbackTransferThreshold());
        return response;
    }

    /**
     * 判定消息发送方，并校验会话归属与状态。
     */
    private SenderType resolveSenderType(ChatSession session, LoginUser loginUser) {
        String role = loginUser.getRoleCode();
        if (Constants.ROLE_USER.equals(role)) {
            if (!loginUser.getUserId().equals(session.getUserId())) {
                throw new BusinessException(ResultCode.FORBIDDEN, "无权在该会话中发送消息");
            }
            return SenderType.USER;
        }
        if (Constants.ROLE_AGENT.equals(role)) {
            if (!loginUser.getUserId().equals(session.getAgentId())
                    || !SessionStatus.HUMAN.getCode().equals(session.getStatus())) {
                throw new BusinessException(ResultCode.FORBIDDEN, "仅可向本人接待中的会话发送消息");
            }
            return SenderType.AGENT;
        }
        throw new BusinessException(ResultCode.FORBIDDEN, "当前角色不支持发送会话消息");
    }

    /**
     * 会话列表数据权限：USER 本人、AGENT 本人接待的 HUMAN/CLOSED、ADMIN 全部。
     */
    private void applyDataScope(LambdaQueryWrapper<ChatSession> wrapper, LoginUser loginUser) {
        String role = loginUser.getRoleCode();
        if (Constants.ROLE_USER.equals(role)) {
            wrapper.eq(ChatSession::getUserId, loginUser.getUserId());
        } else if (Constants.ROLE_AGENT.equals(role)) {
            wrapper.eq(ChatSession::getAgentId, loginUser.getUserId())
                    .in(ChatSession::getStatus,
                            SessionStatus.HUMAN.getCode(), SessionStatus.CLOSED.getCode());
        }
    }

    /**
     * 校验结束会话权限：USER 本人、AGENT 本人接待、ADMIN 全部。
     */
    private void checkCloseAccess(ChatSession session, LoginUser loginUser) {
        checkAccess(session, loginUser);
        if (Constants.ROLE_AGENT.equals(loginUser.getRoleCode())
                && !SessionStatus.HUMAN.getCode().equals(session.getStatus())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅可结束本人接待中的会话");
        }
    }

    /**
     * 按角色确定关闭原因，显式传入时校验取值与角色一致性。
     */
    private String resolveCloseReason(LoginUser loginUser, String requested) {
        String role = loginUser.getRoleCode();
        String defaultReason;
        if (Constants.ROLE_USER.equals(role)) {
            defaultReason = CloseReason.USER_CLOSED.getCode();
        } else if (Constants.ROLE_AGENT.equals(role)) {
            defaultReason = CloseReason.AGENT_CLOSED.getCode();
        } else {
            defaultReason = CloseReason.SYSTEM.getCode();
        }
        if (!StringUtils.hasText(requested)) {
            return defaultReason;
        }
        CloseReason reason = CloseReason.fromCode(requested.trim().toUpperCase());
        if (reason == null || reason == CloseReason.TIMEOUT) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "关闭原因仅支持 USER_CLOSED/AGENT_CLOSED/SYSTEM");
        }
        if (Constants.ROLE_USER.equals(role) && reason != CloseReason.USER_CLOSED) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "用户只能以 USER_CLOSED 结束会话");
        }
        if (Constants.ROLE_AGENT.equals(role) && reason != CloseReason.AGENT_CLOSED) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "坐席只能以 AGENT_CLOSED 结束会话");
        }
        if (Constants.ROLE_ADMIN.equals(role) && reason != CloseReason.SYSTEM) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "管理员只能以 SYSTEM 关闭会话");
        }
        return reason.getCode();
    }

    /**
     * 扣减坐席当前接待数并累加服务量（仅人工接待结束）。
     */
    private void releaseAgent(Long agentId) {
        try {
            agentStatusMapper.update(null, new LambdaUpdateWrapper<AgentStatus>()
                    .setSql("current_count = CASE WHEN current_count > 0 THEN current_count - 1 ELSE 0 END")
                    .setSql("online_status = CASE WHEN online_status = 'BUSY' AND current_count < max_concurrent THEN 'ONLINE' ELSE online_status END")
                    .setSql("total_served = total_served + 1")
                    .eq(AgentStatus::getAgentId, agentId));
            queueService.cacheAgentStatus(agentId);
        } catch (Exception e) {
            log.warn("扣减坐席负载失败：agentId={}, error={}", agentId, e.getMessage());
        }
    }

    /**
     * 转人工响应：排队中返回排位，已接入返回坐席信息。
     */
    private TransferResponse buildTransferResponse(ChatSession session) {
        TransferResponse response = new TransferResponse();
        response.setSessionId(session.getId());
        response.setStatus(session.getStatus());
        response.setQueueSize(queueService.size());
        if (SessionStatus.WAITING.getCode().equals(session.getStatus())) {
            int position = Math.max(1, queueService.position(session.getId()));
            response.setQueuePosition(position);
            response.setEstimatedWaitSeconds(Math.max(30, position * 60));
        } else {
            response.setQueuePosition(0);
            response.setEstimatedWaitSeconds(0);
        }
        if (SessionStatus.HUMAN.getCode().equals(session.getStatus()) && session.getAgentId() != null) {
            SysUser agent = sysUserMapper.selectById(session.getAgentId());
            AgentBriefVO brief = new AgentBriefVO();
            brief.setId(session.getAgentId());
            brief.setNickname(displayName(agent));
            brief.setAvatar(agent == null ? null : agent.getAvatar());
            response.setAgent(brief);
        }
        return response;
    }

    /**
     * 事务提交后触发自动分配，保证接入事务独立提交。
     */
    private void dispatchAfterCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    agentDispatcherService.dispatchNext();
                }
            });
        } else {
            agentDispatcherService.dispatchNext();
        }
    }

    /**
     * 事务提交后异步生成进线摘要，保证异步线程能读到已提交的会话与消息。
     */
    private void generateBriefAfterCommit(Long sessionId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    agentAssistService.generateBriefAsync(sessionId);
                }
            });
        } else {
            agentAssistService.generateBriefAsync(sessionId);
        }
    }

    /**
     * 从排队队列与会话-坐席绑定中移除会话。
     */
    private void removeQueue(Long sessionId) {
        try {
            stringRedisTemplate.opsForZSet().remove(Constants.QUEUE_WAITING_KEY, String.valueOf(sessionId));
            stringRedisTemplate.delete(Constants.SESSION_AGENT_PREFIX + sessionId);
        } catch (Exception e) {
            log.warn("清理会话排队信息失败：sessionId={}, error={}", sessionId, e.getMessage());
        }
    }

    /**
     * 消息限流：同一用户每秒最多 3 条，Redis 不可用时放行。
     */
    private void checkRateLimit(Long userId) {
        try {
            String key = Constants.RATE_CHAT_PREFIX + userId + ":" + (System.currentTimeMillis() / 1000);
            Long count = stringRedisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                stringRedisTemplate.expire(key, 2, TimeUnit.SECONDS);
            }
            if (count != null && count > MAX_MESSAGES_PER_SECOND) {
                throw new BusinessException(429, "消息发送过于频繁，请稍后再试");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("消息限流校验失败，放行：{}", e.getMessage());
        }
    }

    private ChatMessage findDuplicate(Long sessionId, String clientMsgId) {
        try {
            String value = stringRedisTemplate.opsForValue().get(dedupKey(sessionId, clientMsgId));
            if (StringUtils.hasText(value)) {
                return chatMessageService.getById(Long.valueOf(value));
            }
        } catch (Exception e) {
            log.warn("消息幂等查询失败，按新消息处理：{}", e.getMessage());
        }
        return null;
    }

    private void rememberMessage(Long sessionId, String clientMsgId, Long messageId) {
        try {
            stringRedisTemplate.opsForValue().set(dedupKey(sessionId, clientMsgId),
                    String.valueOf(messageId), DEDUP_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("写入消息幂等键失败：{}", e.getMessage());
        }
    }

    private String dedupKey(Long sessionId, String clientMsgId) {
        return Constants.MSG_DEDUP_PREFIX + sessionId + ":" + clientMsgId;
    }

    private void resetFallbackCount(Long sessionId) {
        try {
            stringRedisTemplate.delete(Constants.SESSION_FALLBACK_PREFIX + sessionId);
        } catch (Exception e) {
            log.debug("清理会话降级计数失败：{}", e.getMessage());
        }
    }

    private int incrementFallbackCount(Long sessionId) {
        try {
            String key = Constants.SESSION_FALLBACK_PREFIX + sessionId;
            Long count = stringRedisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                stringRedisTemplate.expire(key, FALLBACK_TTL_HOURS, TimeUnit.HOURS);
            }
            return count == null ? 0 : count.intValue();
        } catch (Exception e) {
            log.warn("更新会话降级计数失败：{}", e.getMessage());
            return 0;
        }
    }

    /**
     * 记录访客首条有效消息时间：仅在为空时写入，天然排除连续多条消息的重复计时。
     */
    private void markFirstUserMsgTime(Long sessionId) {
        try {
            chatSessionMapper.update(null, new LambdaUpdateWrapper<ChatSession>()
                    .eq(ChatSession::getId, sessionId)
                    .isNull(ChatSession::getFirstUserMsgTime)
                    .set(ChatSession::getFirstUserMsgTime, LocalDateTime.now()));
        } catch (Exception e) {
            log.warn("记录首条用户消息时间失败：sessionId={}, error={}", sessionId, e.getMessage());
        }
    }

    /**
     * 记录首次有效回复时间（AI 或坐席回复；系统欢迎语不经此链路，天然排除）。
     */
    private void markFirstReplyTime(Long sessionId) {
        try {
            chatSessionMapper.update(null, new LambdaUpdateWrapper<ChatSession>()
                    .eq(ChatSession::getId, sessionId)
                    .isNull(ChatSession::getFirstReplyTime)
                    .set(ChatSession::getFirstReplyTime, LocalDateTime.now()));
        } catch (Exception e) {
            log.warn("记录首次回复时间失败：sessionId={}, error={}", sessionId, e.getMessage());
        }
    }

    private ChatSession refreshSession(ChatSession session) {
        ChatSession latest = chatSessionMapper.selectById(session.getId());
        return latest == null ? session : latest;
    }

    private String nextSessionNo() {
        String timestamp = SESSION_NO_FORMATTER.format(LocalDateTime.now());
        for (int i = 0; i < 5; i++) {
            String sessionNo = SESSION_NO_PREFIX + timestamp + ThreadLocalRandom.current().nextInt(1000, 10000);
            Long count = chatSessionMapper.selectCount(new LambdaQueryWrapper<ChatSession>()
                    .eq(ChatSession::getSessionNo, sessionNo));
            if (count == null || count == 0) {
                return sessionNo;
            }
        }
        return SESSION_NO_PREFIX + timestamp + String.format("%04d", System.currentTimeMillis() % 10000);
    }

    private List<Long> matchUserIds(String keyword) {
        List<SysUser> users = sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .select(SysUser::getId)
                .and(w -> w.like(SysUser::getUsername, keyword).or().like(SysUser::getNickname, keyword)));
        List<Long> ids = new ArrayList<>(users.size());
        for (SysUser user : users) {
            ids.add(user.getId());
        }
        return ids;
    }

    /**
     * 批量会话转 VO，批量补齐用户与坐席名称。
     */
    public List<SessionVO> toVOs(List<ChatSession> sessions) {
        if (sessions == null || sessions.isEmpty()) {
            return new ArrayList<>();
        }
        Set<Long> userIds = new HashSet<>();
        Set<Long> skillGroupIds = new HashSet<>();
        for (ChatSession session : sessions) {
            if (session.getUserId() != null) {
                userIds.add(session.getUserId());
            }
            if (session.getAgentId() != null) {
                userIds.add(session.getAgentId());
            }
            if (session.getSkillGroupId() != null) {
                skillGroupIds.add(session.getSkillGroupId());
            }
        }
        Map<Long, String> names = new HashMap<>();
        if (!userIds.isEmpty()) {
            List<SysUser> users = sysUserMapper.selectBatchIds(userIds);
            for (SysUser user : users) {
                names.put(user.getId(), displayName(user));
            }
        }
        Map<Long, String> skillGroupNames = skillGroupService.nameMap(skillGroupIds);
        List<SessionVO> list = new ArrayList<>(sessions.size());
        for (ChatSession session : sessions) {
            SessionVO vo = new SessionVO();
            vo.setId(session.getId());
            vo.setSessionNo(session.getSessionNo());
            vo.setUserId(session.getUserId());
            vo.setUserName(names.get(session.getUserId()));
            vo.setAgentId(session.getAgentId());
            vo.setAgentName(session.getAgentId() == null ? null : names.get(session.getAgentId()));
            vo.setSkillGroupId(session.getSkillGroupId());
            vo.setSkillGroupName(session.getSkillGroupId() == null
                    ? null : skillGroupNames.get(session.getSkillGroupId()));
            vo.setStatus(session.getStatus());
            vo.setCloseReason(session.getCloseReason());
            vo.setRating(session.getRating());
            vo.setRatingComment(session.getRatingComment());
            vo.setFirstUserMsgTime(session.getFirstUserMsgTime());
            vo.setFirstReplyTime(session.getFirstReplyTime());
            vo.setQueueEnterTime(session.getQueueEnterTime());
            vo.setTransferTime(session.getTransferTime());
            vo.setAcceptTime(session.getAcceptTime());
            vo.setEndTime(session.getEndTime());
            vo.setMessageCount(session.getMessageCount() == null ? 0 : session.getMessageCount());
            vo.setCreateTime(session.getCreateTime());
            list.add(vo);
        }
        return list;
    }

    private String displayName(SysUser user) {
        if (user == null) {
            return null;
        }
        return StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername();
    }

    private void validatePage(int pageNum, int pageSize) {
        if (pageNum < 1) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "pageNum 必须大于等于 1");
        }
        if (pageSize < 1 || pageSize > Constants.MAX_PAGE_SIZE) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "pageSize 必须在 1-100 之间");
        }
    }

    private LocalDate parseDate(String date) {
        if (!StringUtils.hasText(date)) {
            return null;
        }
        try {
            return LocalDate.parse(date.trim());
        } catch (DateTimeParseException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "日期格式须为 yyyy-MM-dd");
        }
    }

    private void validateDateRange(LocalDate start, LocalDate end) {
        if (start == null || end == null) {
            return;
        }
        if (start.isAfter(end)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "开始日期不能晚于结束日期");
        }
        if (ChronoUnit.DAYS.between(start, end) > MAX_DATE_SPAN_DAYS) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "统计时间跨度不能超过 366 天");
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
