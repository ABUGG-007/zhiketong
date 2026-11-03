package com.zhiketong.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiketong.entity.TenantPlan;
import org.apache.ibatis.annotations.Mapper;

/**
 * 套餐权益持久层（S4-A）。
 *
 * @author 智客通开发组
 */
@Mapper
public interface TenantPlanMapper extends BaseMapper<TenantPlan> {
}
