package com.zhiketong.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiketong.entity.OrderFieldValue;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工单自定义字段值持久层（S3-A）。
 *
 * @author 智客通开发组
 */
@Mapper
public interface OrderFieldValueMapper extends BaseMapper<OrderFieldValue> {
}
