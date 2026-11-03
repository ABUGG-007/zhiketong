package com.zhiketong.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiketong.entity.KbImportTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识包导入批次持久层。
 *
 * @author 智客通开发组
 */
@Mapper
public interface KbImportTaskMapper extends BaseMapper<KbImportTask> {
}
