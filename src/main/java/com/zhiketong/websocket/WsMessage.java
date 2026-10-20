package com.zhiketong.websocket;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * WebSocket 统一消息信封：{ type, requestId, timestamp, data }。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WsMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 消息类型，见接口文档 */
    private String type;

    /** 请求 ID，客户端生成，用于回执匹配 */
    private String requestId;

    /** 服务端/客户端时间戳（毫秒） */
    private Long timestamp;

    /** 业务数据 */
    private Map<String, Object> data;
}
