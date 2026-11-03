package com.zhiketong.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiketong.entity.AlertEvent;
import org.apache.ibatis.annotations.Mapper;

/**
 * 告警事件 Mapper（S6-B）。
 *
 * @author 智客通开发组
 */
@Mapper
public interface AlertEventMapper extends BaseMapper<AlertEvent> {
}
