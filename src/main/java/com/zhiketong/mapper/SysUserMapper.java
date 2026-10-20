package com.zhiketong.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiketong.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统用户 Mapper。
 *
 * @author 智客通开发组
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
}
