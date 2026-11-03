package com.zhiketong.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiketong.common.BusinessException;
import com.zhiketong.common.ResultCode;
import com.zhiketong.dto.chat.MessageVO;
import com.zhiketong.entity.ChatMessage;
import com.zhiketong.entity.ChatSession;
import com.zhiketong.enums.SenderType;
import com.zhiketong.enums.SessionStatus;
import com.zhiketong.mapper.ChatMessageMapper;
import com.zhiketong.security.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 内部备注服务（R-113 / R-114，对应 AS-07）。
 *
 * <p>备注复用 {@code chat_message}，sender_type 固定为 INTERNAL_NOTE：</p>
 * <ul>
 *   <li>仅坐席与管理端可见，访客侧消息分页、大模型上下文一律排除；</li>
 *   <li>不计入 {@code chat_session.message_count}，不影响首响与消息数口径；</li>
 *   <li>不对客推送，保存后仅返回给当前坐席。</li>
 * </ul>
 *
 * @author 智客通开发组
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentNoteService {

    /** 备注单条最大长度 */
    private static final int MAX_NOTE_LENGTH = 500;

    private final ChatMessageMapper chatMessageMapper;
    private final ChatMessageService chatMessageService;
    private final AgentSessionGuard agentSessionGuard;

    /**
     * 查询会话内部备注（R-113），按时间正序返回。
     *
     * @param sessionId 会话 ID
     * @param loginUser 当前登录用户
     * @return 备注列表
     */
    public List<MessageVO> list(Long sessionId, LoginUser loginUser) {
        agentSessionGuard.requireOwned(sessionId, loginUser);
        List<ChatMessage> notes = chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId)
                .eq(ChatMessage::getSenderType, SenderType.INTERNAL_NOTE.getCode())
                .orderByAsc(ChatMessage::getId));
        return chatMessageService.toVOs(notes);
    }

    /**
     * 新增内部备注（R-114）：仅人工接待中或已结束的会话可记录。
     *
     * @param sessionId 会话 ID
     * @param loginUser 当前登录用户
     * @param content   备注内容
     * @return 落库后的备注
     */
    @Transactional(rollbackFor = Exception.class)
    public MessageVO add(Long sessionId, LoginUser loginUser, String content) {
        ChatSession session = agentSessionGuard.requireOwned(sessionId, loginUser);
        if (SessionStatus.AI.getCode().equals(session.getStatus())
                || SessionStatus.WAITING.getCode().equals(session.getStatus())) {
            throw new BusinessException(ResultCode.CONFLICT, "会话尚未进入人工接待，暂不能添加内部备注");
        }
        String text = content == null ? "" : content.trim();
        if (text.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "备注内容不能为空");
        }
        if (text.length() > MAX_NOTE_LENGTH) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "备注内容不能超过 500 个字符");
        }
        ChatMessage note = chatMessageService.save(sessionId, SenderType.INTERNAL_NOTE,
                loginUser.getUserId(), text, "TEXT", null, null, null, false);
        log.info("坐席新增内部备注：sessionId={}, operatorId={}", sessionId, loginUser.getUserId());
        return chatMessageService.toVO(note);
    }

    /**
     * 会话内部备注条数，供客户信息卡等场景复用。
     *
     * @param sessionId 会话 ID
     * @return 备注条数
     */
    public long count(Long sessionId) {
        if (sessionId == null) {
            return 0L;
        }
        return chatMessageMapper.selectCount(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId)
                .eq(ChatMessage::getSenderType, SenderType.INTERNAL_NOTE.getCode()));
    }
}
