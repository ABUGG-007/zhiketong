package com.zhiketong.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiketong.ai.AiMessage;
import com.zhiketong.common.PageResult;
import com.zhiketong.dto.chat.MessageVO;
import com.zhiketong.entity.ChatMessage;
import com.zhiketong.entity.ChatSession;
import com.zhiketong.entity.SysUser;
import com.zhiketong.enums.SenderType;
import com.zhiketong.mapper.ChatMessageMapper;
import com.zhiketong.mapper.ChatSessionMapper;
import com.zhiketong.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 会话消息服务：消息落库、分页查询、已读标记与 AI 历史组装。
 *
 * @author 智客通开发组
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessageService {

    /** AI 消息的保留发送人 ID */
    public static final long AI_SENDER_ID = 0L;

    /** AI 消息展示名称 */
    public static final String AI_DISPLAY_NAME = "AI 助手";

    /** 系统消息展示名称 */
    public static final String SYSTEM_DISPLAY_NAME = "系统";

    /** 内部备注展示名称 */
    public static final String INTERNAL_NOTE_DISPLAY_NAME = "内部备注";

    /** 增量拉取最大条数 */
    private static final int MAX_AFTER_ID_MESSAGES = 200;

    /** AI 上下文历史最大条数（6 轮） */
    private static final int AI_HISTORY_MESSAGES = 12;

    private final ChatMessageMapper chatMessageMapper;
    private final ChatSessionMapper chatSessionMapper;
    private final SysUserMapper sysUserMapper;

    /**
     * 按 ID 查询消息，不存在返回 null。
     */
    public ChatMessage getById(Long id) {
        return id == null ? null : chatMessageMapper.selectById(id);
    }

    /**
     * 保存一条消息，并按需累加会话消息数。
     *
     * @param sessionId    会话 ID
     * @param senderType   发送方类型
     * @param senderId     发送人 ID，AI/SYSTEM 传 0
     * @param content      消息内容
     * @param contentType  内容类型，默认 TEXT
     * @param matchedKbId  命中的知识条目 ID
     * @param aiConfidence AI 置信度
     * @param aiModel      AI 模型名称
     * @param countMessage 是否计入 chat_session.message_count
     * @return 落库后的消息
     */
    @Transactional(rollbackFor = Exception.class)
    public ChatMessage save(Long sessionId, SenderType senderType, Long senderId,
                            String content, String contentType, Long matchedKbId,
                            BigDecimal aiConfidence, String aiModel, boolean countMessage) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setSenderType(senderType.getCode());
        message.setSenderId(senderId == null ? 0L : senderId);
        message.setContent(content);
        message.setContentType(contentType == null ? "TEXT" : contentType);
        message.setMatchedKbId(matchedKbId);
        message.setAiConfidence(aiConfidence);
        message.setAiModel(aiModel);
        message.setReadFlag(0);
        message.setCreateTime(LocalDateTime.now());
        chatMessageMapper.insert(message);
        if (countMessage) {
            chatSessionMapper.update(null, new LambdaUpdateWrapper<ChatSession>()
                    .setSql("message_count = message_count + 1")
                    .eq(ChatSession::getId, sessionId));
        }
        return message;
    }

    /**
     * 消息分页（R-35）：afterId 存在时按增量拉取。
     * <p>内部备注只对坐席与管理端可见，访客侧查询一律排除。</p>
     */
    public PageResult<MessageVO> page(Long sessionId, Integer pageNum, Integer pageSize,
                                      Long afterId, boolean markRead, String readerRole) {
        if (markRead) {
            markRead(sessionId, readerRole);
        }
        boolean includeNotes = isSeatRole(readerRole);
        if (afterId != null) {
            LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<ChatMessage>()
                    .eq(ChatMessage::getSessionId, sessionId)
                    .gt(ChatMessage::getId, afterId)
                    .orderByAsc(ChatMessage::getId)
                    .last("LIMIT " + MAX_AFTER_ID_MESSAGES);
            if (!includeNotes) {
                wrapper.ne(ChatMessage::getSenderType, SenderType.INTERNAL_NOTE.getCode());
            }
            List<ChatMessage> messages = chatMessageMapper.selectList(wrapper);
            PageResult<MessageVO> result = new PageResult<>();
            result.setList(toVOs(messages));
            result.setTotal(messages.size());
            result.setPageNum(1);
            result.setPageSize(MAX_AFTER_ID_MESSAGES);
            result.setPages(messages.isEmpty() ? 0 : 1);
            return result;
        }
        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId)
                .orderByAsc(ChatMessage::getId);
        if (!includeNotes) {
            wrapper.ne(ChatMessage::getSenderType, SenderType.INTERNAL_NOTE.getCode());
        }
        Page<ChatMessage> page = chatMessageMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return PageResult.of(page, toVOs(page.getRecords()));
    }

    /**
     * 最近若干条消息，按时间正序返回，供坐席接入时展示上下文。
     */
    public List<MessageVO> recent(Long sessionId, int limit) {
        if (limit <= 0) {
            return new ArrayList<>();
        }
        List<ChatMessage> messages = chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId)
                .orderByDesc(ChatMessage::getId)
                .last("LIMIT " + limit));
        Collections.reverse(messages);
        return toVOs(messages);
    }

    /**
     * 组装大模型历史上下文：USER → user，AI/AGENT → assistant，SYSTEM 跳过。
     */
    public List<AiMessage> history(Long sessionId) {
        List<ChatMessage> messages = chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId)
                .in(ChatMessage::getSenderType, SenderType.USER.getCode(),
                        SenderType.AI.getCode(), SenderType.AGENT.getCode(),
                        SenderType.SUPERVISOR.getCode())
                .orderByDesc(ChatMessage::getId)
                .last("LIMIT " + AI_HISTORY_MESSAGES));
        Collections.reverse(messages);
        List<AiMessage> history = new ArrayList<>(messages.size());
        for (ChatMessage message : messages) {
            String role = SenderType.USER.getCode().equals(message.getSenderType()) ? "user" : "assistant";
            history.add(new AiMessage(role, message.getContent()));
        }
        return history;
    }

    /**
     * 标记对方消息为已读。
     *
     * @param readerRole 读取方角色：USER/AGENT/ADMIN
     */
    public void markRead(Long sessionId, String readerRole) {
        LambdaUpdateWrapper<ChatMessage> wrapper = new LambdaUpdateWrapper<ChatMessage>()
                .set(ChatMessage::getReadFlag, 1)
                .eq(ChatMessage::getSessionId, sessionId)
                .eq(ChatMessage::getReadFlag, 0);
        if ("USER".equals(readerRole)) {
            wrapper.in(ChatMessage::getSenderType, SenderType.AI.getCode(),
                    SenderType.AGENT.getCode(), SenderType.SYSTEM.getCode());
        } else if ("AGENT".equals(readerRole)) {
            wrapper.eq(ChatMessage::getSenderType, SenderType.USER.getCode());
        }
        chatMessageMapper.update(null, wrapper);
    }

    /**
     * 单条消息转 VO。
     */
    public MessageVO toVO(ChatMessage message) {
        return toVOs(Collections.singletonList(message)).get(0);
    }

    /**
     * 批量消息转 VO，批量补齐发送人名称。
     */
    public List<MessageVO> toVOs(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Long, String> userNames = userNames(messages);
        List<MessageVO> list = new ArrayList<>(messages.size());
        for (ChatMessage message : messages) {
            MessageVO vo = new MessageVO();
            vo.setId(message.getId());
            vo.setSessionId(message.getSessionId());
            vo.setSenderType(message.getSenderType());
            vo.setSenderId(message.getSenderId());
            vo.setSenderName(senderName(message, userNames));
            vo.setContent(message.getContent());
            vo.setContentType(message.getContentType());
            vo.setMatchedKbId(message.getMatchedKbId());
            vo.setAiConfidence(message.getAiConfidence());
            vo.setAiModel(message.getAiModel());
            vo.setReadFlag(message.getReadFlag());
            vo.setAnswerCategory(message.getAnswerCategory());
            vo.setCreateTime(message.getCreateTime());
            list.add(vo);
        }
        return list;
    }

    private String senderName(ChatMessage message, Map<Long, String> userNames) {
        String senderType = message.getSenderType();
        if (SenderType.AI.getCode().equals(senderType)) {
            return AI_DISPLAY_NAME;
        }
        if (SenderType.SYSTEM.getCode().equals(senderType)) {
            return SYSTEM_DISPLAY_NAME;
        }
        if (SenderType.INTERNAL_NOTE.getCode().equals(senderType)) {
            return INTERNAL_NOTE_DISPLAY_NAME;
        }
        String name = userNames.get(message.getSenderId());
        return name == null ? senderType : name;
    }

    /**
     * 是否为坐席侧读取（可看内部备注）：AGENT / ADMIN。
     */
    private boolean isSeatRole(String readerRole) {
        return "AGENT".equals(readerRole) || "ADMIN".equals(readerRole);
    }

    private Map<Long, String> userNames(List<ChatMessage> messages) {
        Set<Long> userIds = new HashSet<>();
        for (ChatMessage message : messages) {
            boolean userMessage = SenderType.USER.getCode().equals(message.getSenderType())
                    || SenderType.AGENT.getCode().equals(message.getSenderType())
                    || SenderType.SUPERVISOR.getCode().equals(message.getSenderType())
                    || SenderType.INTERNAL_NOTE.getCode().equals(message.getSenderType());
            if (userMessage && message.getSenderId() != null) {
                userIds.add(message.getSenderId());
            }
        }
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<SysUser> users = sysUserMapper.selectBatchIds(userIds);
        Map<Long, String> names = new HashMap<>();
        for (SysUser user : users) {
            names.put(user.getId(), user.getNickname() == null ? user.getUsername() : user.getNickname());
        }
        return names;
    }
}
