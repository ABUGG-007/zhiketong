package com.zhiketong.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiketong.entity.AuditLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 通用操作审计持久层（S4-C，PL-06）。
 *
 * @author 智客通开发组
 */
@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {
}
