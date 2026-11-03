package com.zhiketong.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiketong.entity.KbUnmatchedQuestion;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识库未命中问题持久层。
 *
 * @author 智客通开发组
 */
@Mapper
public interface KbUnmatchedQuestionMapper extends BaseMapper<KbUnmatchedQuestion> {
}
