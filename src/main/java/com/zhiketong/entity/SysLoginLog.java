package com.zhiketong.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 登录日志实体，对应表 sys_login_log。
 * <p>
 * 该表只有 create_time、没有 update_time，因此不继承 BaseEntity。
 *
 * @author 智客通开发组
 */
@Data
@TableName("sys_login_log")
public class SysLoginLog implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 ID，数据库自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 ID（登录账号不存在时为空） */
    private Long userId;

    /** 登录账号 */
    private String username;

    /** 登录 IP */
    private String ip;

    /** 浏览器 UA */
    private String userAgent;

    /** 是否成功：1 是 0 否 */
    private Integer success;

    /** 结果说明 */
    private String message;

    /** 创建时间 */
    private LocalDateTime createTime;
}
