package com.zhiketong.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiketong.entity.KbEntryVersion;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识词条版本持久层。
 *
 * @author 智客通开发组
 */
@Mapper
public interface KbEntryVersionMapper extends BaseMapper<KbEntryVersion> {
}
