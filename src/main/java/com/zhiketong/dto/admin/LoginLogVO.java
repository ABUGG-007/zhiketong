package com.zhiketong.dto.admin;

import com.zhiketong.entity.SysLoginLog;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 登录日志视图对象（R-70）。
 */
@Data
public class LoginLogVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long userId;
    private String username;
    private String ip;
    private String userAgent;
    private Integer success;
    private String message;
    private LocalDateTime createTime;

    /**
     * 实体转 VO。
     *
     * @param log 登录日志实体
     * @return 登录日志视图对象
     */
    public static LoginLogVO of(SysLoginLog log) {
        if (log == null) {
            return null;
        }
        LoginLogVO vo = new LoginLogVO();
        vo.setId(log.getId());
        vo.setUserId(log.getUserId());
        vo.setUsername(log.getUsername());
        vo.setIp(log.getIp());
        vo.setUserAgent(log.getUserAgent());
        vo.setSuccess(log.getSuccess());
        vo.setMessage(log.getMessage());
        vo.setCreateTime(log.getCreateTime());
        return vo;
    }
}
