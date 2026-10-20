package com.zhiketong.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiketong.entity.WorkOrder;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工单 Mapper。
 *
 * @author 智客通开发组
 */
@Mapper
public interface WorkOrderMapper extends BaseMapper<WorkOrder> {
}
