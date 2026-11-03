package com.zhiketong.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiketong.entity.OrderField;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工单自定义字段持久层（S3-A）。
 *
 * @author 智客通开发组
 */
@Mapper
public interface OrderFieldMapper extends BaseMapper<OrderField> {
}
