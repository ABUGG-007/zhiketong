package com.zhiketong.dto.monitor;

import com.zhiketong.dto.alert.AlertEventVO;
import com.zhiketong.dto.chat.MessageVO;
import com.zhiketong.dto.chat.SessionVO;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 组长监听详情（R-200，AS-10）：会话 + 最近消息 + 未闭环告警。
 *
 * @author 智客通开发组
 */
@Data
public class MonitorDetailVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 会话信息 */
    private SessionVO session;

    /** 访客名称 */
    private String userName;

    /** 接待坐席名称 */
    private String agentName;

    /** 最近消息（按时间正序，含内部备注） */
    private List<MessageVO> messages = new ArrayList<>();

    /** 该会话未关闭的告警 */
    private List<AlertEventVO> alerts = new ArrayList<>();

    /** 距最后一条访客消息的等待秒数 */
    private Long pendingSeconds;

    /** 是否可强插发言（会话未结束） */
    private boolean whisperAllowed;

    /** 是否可强制接管（会话处于排队或人工接待中） */
    private boolean takeoverAllowed;

    /** 不可接管时的原因说明 */
    private String takeoverHint;

    /** 服务端时间 */
    private LocalDateTime serverTime;
}
