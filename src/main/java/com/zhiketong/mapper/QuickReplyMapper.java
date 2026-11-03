package com.zhiketong.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiketong.entity.QuickReply;
import org.apache.ibatis.annotations.Mapper;

/**
 * 快捷回复话术持久层。
 *
 * @author 智客通开发组
 */
@Mapper
public interface QuickReplyMapper extends BaseMapper<QuickReply> {
}
