package com.zhiketong.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiketong.entity.OrderSlaPolicy;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工单 SLA 策略持久层（S3-B）。
 *
 * @author 智客通开发组
 */
@Mapper
public interface OrderSlaPolicyMapper extends BaseMapper<OrderSlaPolicy> {
}
