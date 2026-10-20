package com.zhiketong.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 实体基类：统一主键与审计时间字段。
 * <p>
 * 仅包含 id、create_time、update_time 三个通用字段，供具备这三列的表继承；
 * 没有 update_time 的表（chat_message、work_order_log、ai_call_log、sys_login_log）
 * 以及逻辑删除字段 deleted 由各实体自行声明，避免继承字段与表结构错位。
 * <p>
 * 注意：createTime / updateTime 不开启 MyBatis-Plus 自动填充，
 * 由数据库 DEFAULT CURRENT_TIMESTAMP / ON UPDATE CURRENT_TIMESTAMP 维护；
 * 插入时字段为 null 会被 MyBatis-Plus 忽略，从而使用数据库默认值。
 *
 * @author 智客通开发组
 */
@Data
public class BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 ID，数据库自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
