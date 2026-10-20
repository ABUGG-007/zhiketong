package com.zhiketong.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiketong.entity.ChatSession;
import org.apache.ibatis.annotations.Mapper;

/**
 * 客服会话 Mapper。
 *
 * @author 智客通开发组
 */
@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {
}
