package com.zhiketong.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiketong.entity.Tenant;
import org.apache.ibatis.annotations.Mapper;

/**
 * 租户主数据持久层（S4-A）。
 *
 * @author 智客通开发组
 */
@Mapper
public interface TenantMapper extends BaseMapper<Tenant> {
}
