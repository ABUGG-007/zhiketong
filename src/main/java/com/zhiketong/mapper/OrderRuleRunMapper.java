package com.zhiketong.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiketong.entity.OrderRuleRun;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工单规则待执行队列与执行流水持久层（S3-C）。
 *
 * @author 智客通开发组
 */
@Mapper
public interface OrderRuleRunMapper extends BaseMapper<OrderRuleRun> {
}
