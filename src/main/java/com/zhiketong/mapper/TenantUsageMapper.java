package com.zhiketong.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiketong.entity.TenantUsage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 租户用量快照持久层（S4-B）。
 *
 * @author 智客通开发组
 */
@Mapper
public interface TenantUsageMapper extends BaseMapper<TenantUsage> {
}
