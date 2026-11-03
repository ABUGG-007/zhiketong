package com.zhiketong.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiketong.entity.OrderRule;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工单规则配置持久层（S3-C）。
 *
 * @author 智客通开发组
 */
@Mapper
public interface OrderRuleMapper extends BaseMapper<OrderRule> {
}
