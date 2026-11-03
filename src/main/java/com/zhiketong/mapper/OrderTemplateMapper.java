package com.zhiketong.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiketong.entity.OrderTemplate;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工单模板持久层（S3-A）。
 *
 * @author 智客通开发组
 */
@Mapper
public interface OrderTemplateMapper extends BaseMapper<OrderTemplate> {
}
