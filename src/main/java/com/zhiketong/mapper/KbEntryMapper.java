package com.zhiketong.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiketong.entity.KbEntry;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识库条目 Mapper。
 *
 * @author 智客通开发组
 */
@Mapper
public interface KbEntryMapper extends BaseMapper<KbEntry> {
}
