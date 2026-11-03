package com.zhiketong.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiketong.entity.KbSimilarQuestion;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识库相似问法持久层。
 *
 * @author 智客通开发组
 */
@Mapper
public interface KbSimilarQuestionMapper extends BaseMapper<KbSimilarQuestion> {
}
