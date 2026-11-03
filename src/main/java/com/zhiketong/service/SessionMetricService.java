package com.zhiketong.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhiketong.common.BusinessException;
import com.zhiketong.common.ResultCode;
import com.zhiketong.entity.ChatMessage;
import com.zhiketong.entity.ChatSession;
import com.zhiketong.enums.SenderType;
import com.zhiketong.enums.SessionStatus;
import com.zhiketong.mapper.ChatMessageMapper;
import com.zhiketong.mapper.ChatSessionMapper;
import com.zhiketong.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

/**
 * 会话轮次指标服务（S6-A，DB-04 口径支撑）。
 *
 * <p>「3 分钟回复率」按轮次统计：坐席接入时间（{@code accept_time}）之后的每一条访客消息算一轮，
 * 坐席在 180 秒内给出首次回复即计入分子。轮次在会话关闭时一次性算好并落到
 * {@code chat_session.reply_round_count / reply_3min_count}，避免区间统计时全量扫描消息表。</p>
 *
 * <p>口径约束：</p>
 * <ul>
 *   <li>只统计人工接待期（{@code accept_time} 之后），AI 自动应答轮次不计入分母；</li>
 *   <li>回复方限定为坐席（{@code sender_type = AGENT}），系统提示、内部备注、AI 消息不算回复；</li>
 *   <li>访客连续多条消息各自独立成轮，与路线图第 3 章「按轮次统计，不按会话统计」一致；</li>
 *   <li>{@code ops_metric_time} 为空表示尚未计算，统计接口按覆盖率披露，不把未计算的会话当成 0 轮。</li>
 * </ul>
 *
 * @author 智客通开发组
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SessionMetricService {

    /** 回复时限：180 秒（3 分钟） */
    public static final long REPLY_WINDOW_SECONDS = 180L;

    /** 单轮回填最多处理的会话数 */
    private static final int MAX_REBUILD_SESSIONS = 500;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;

    /**
     * 计算并落库单个会话的轮次指标（会话关闭时调用）。
     *
     * <p>重复调用幂等：结果只取决于会话消息与接入/结束时间，可安全用于回填。</p>
     *
     * @param session 会话，需已带 {@code id} 与 {@code acceptTime}
     * @return 计算后的会话对象（同步回填内存字段，便于响应直接复用）
     */
    @Transactional(rollbackFor = Exception.class)
    public ChatSession computeForSession(ChatSession session) {
        if (session == null || session.getId() == null) {
            return session;
        }
        int rounds = 0;
        int within = 0;
        int messageCount = 0;
        LocalDateTime acceptTime = session.getAcceptTime();
        if (acceptTime != null) {
            LocalDateTime limit = session.getEndTime() == null ? LocalDateTime.now() : session.getEndTime();
            // 消息时间与业务时间都按秒对齐：MySQL DATETIME 只存到秒且按四舍五入进位，
            // 上界必须取「结束秒 + 1 秒」开区间，否则同一秒内（不足 1 秒差）的坐席回复会被误判为结束之后而漏算
            LocalDateTime from = acceptTime.truncatedTo(ChronoUnit.SECONDS);
            LocalDateTime to = limit.truncatedTo(ChronoUnit.SECONDS).plusSeconds(1);
            // 只取访客与坐席消息：AI / SYSTEM / INTERNAL_NOTE 不参与轮次配对
            List<ChatMessage> messages = chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                    .eq(ChatMessage::getSessionId, session.getId())
                    .ge(ChatMessage::getCreateTime, from)
                    .lt(ChatMessage::getCreateTime, to)
                    .in(ChatMessage::getSenderType, Arrays.asList(
                            SenderType.USER.getCode(), SenderType.AGENT.getCode()))
                    .orderByAsc(ChatMessage::getId));
            messageCount = messages.size();
            int cursor = 0;
            for (int i = 0; i < messages.size(); i++) {
                ChatMessage message = messages.get(i);
                if (!SenderType.USER.getCode().equals(message.getSenderType())) {
                    continue;
                }
                rounds++;
                // 从当前访客消息之后找第一条坐席消息，按消息 ID 顺序配对，避免同秒消息乱序
                if (cursor < i + 1) {
                    cursor = i + 1;
                }
                while (cursor < messages.size()
                        && !SenderType.AGENT.getCode().equals(messages.get(cursor).getSenderType())) {
                    cursor++;
                }
                if (cursor >= messages.size()) {
                    break;
                }
                ChatMessage reply = messages.get(cursor);
                long seconds = ChronoUnit.SECONDS.between(message.getCreateTime(), reply.getCreateTime());
                if (seconds >= 0 && seconds <= REPLY_WINDOW_SECONDS) {
                    within++;
                }
                cursor++;
            }
        }
        LocalDateTime now = LocalDateTime.now();
        log.info("会话轮次指标：sessionId={}, acceptTime={}, endTime={}, 参与消息={}, 轮次={}, 3分钟内={}",
                session.getId(), acceptTime, session.getEndTime(), messageCount, rounds, within);
        chatSessionMapper.update(null, new LambdaUpdateWrapper<ChatSession>()
                .eq(ChatSession::getId, session.getId())
                .set(ChatSession::getReplyRoundCount, rounds)
                .set(ChatSession::getReply3minCount, within)
                .set(ChatSession::getOpsMetricTime, now));
        session.setReplyRoundCount(rounds);
        session.setReply3minCount(within);
        session.setOpsMetricTime(now);
        return session;
    }

    /**
     * 回填区间内尚未计算指标的已关闭会话（管理员手动触发或发布后补数）。
     *
     * <p>跨租户扫描：先以系统模式捞候选，再逐条切到记录所属租户计算，
     * 保证消息查询与更新都落在正确租户作用域。</p>
     *
     * @param startDate 结束时间起（yyyy-MM-dd，含当天），为空取 90 天前
     * @param endDate   结束时间止（yyyy-MM-dd，含当天），为空取今天
     * @return 本次实际回填的会话数
     */
    @Transactional(rollbackFor = Exception.class)
    public int rebuild(String startDate, String endDate) {
        LocalDate end = StringUtils.hasText(endDate) ? parseDate(endDate, "endDate") : LocalDate.now();
        LocalDate start = StringUtils.hasText(startDate) ? parseDate(startDate, "startDate") : end.minusDays(90);
        if (start.isAfter(end)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "开始日期不能晚于结束日期");
        }
        LocalDateTime from = start.atStartOfDay();
        LocalDateTime to = end.plusDays(1).atStartOfDay();
        List<ChatSession> candidates = TenantContext.callAs(TenantContext.PLATFORM_TENANT_ID,
                () -> chatSessionMapper.selectList(new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getStatus, SessionStatus.CLOSED.getCode())
                        .isNull(ChatSession::getOpsMetricTime)
                        .ge(ChatSession::getCreateTime, from)
                        .lt(ChatSession::getCreateTime, to)
                        .orderByAsc(ChatSession::getId)
                        .last("LIMIT " + MAX_REBUILD_SESSIONS)));
        int processed = 0;
        for (ChatSession candidate : candidates) {
            Long tenantId = candidate.getTenantId() == null
                    ? TenantContext.DEFAULT_TENANT_ID : candidate.getTenantId();
            TenantContext.runAs(tenantId, () -> computeForSession(candidate));
            processed++;
        }
        log.info("会话轮次指标回填完成：区间={} ~ {}，处理={}", start, end, processed);
        return processed;
    }

    private LocalDate parseDate(String value, String field) {
        try {
            return LocalDate.parse(value.trim(), DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, field + " 格式必须为 yyyy-MM-dd");
        }
    }
}
