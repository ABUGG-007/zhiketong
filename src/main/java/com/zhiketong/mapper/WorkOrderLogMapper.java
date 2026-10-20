package com.zhiketong.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiketong.entity.WorkOrderLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工单流转日志 Mapper。
 *
 * @author 智客通开发组
 */
@Mapper
public interface WorkOrderLogMapper extends BaseMapper<WorkOrderLog> {
}
