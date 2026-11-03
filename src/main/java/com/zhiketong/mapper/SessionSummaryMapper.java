package com.zhiketong.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiketong.entity.SessionSummary;
import org.apache.ibatis.annotations.Mapper;

/**
 * 会话摘要与服务小结持久层。
 *
 * @author 智客通开发组
 */
@Mapper
public interface SessionSummaryMapper extends BaseMapper<SessionSummary> {
}
