package com.zhiketong.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiketong.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 聊天消息 Mapper。
 *
 * @author 智客通开发组
 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
}
