package com.zhiketong.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiketong.entity.AlertRule;
import org.apache.ibatis.annotations.Mapper;

/**
 * 告警规则 Mapper（S6-B）。
 *
 * @author 智客通开发组
 */
@Mapper
public interface AlertRuleMapper extends BaseMapper<AlertRule> {
}
