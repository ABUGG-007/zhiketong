package com.zhiketong.dto.sla;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 工单 SLA 手动扫描 / 演练请求（R-136，仅管理员）。
 *
 * <p>{@code simulateTime} 为空时按服务端当前时间扫描；传入未来时间可在不等待真实时限的情况下
 * 验证预警、超时与升级链路，用于 SLA 演练与自动化回归。</p>
 *
 * @author 智客通开发组
 */
@Data
public class SlaScanRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 模拟扫描时刻（ISO-8601，如 2026-10-24T18:30:00），为空表示按当前时间扫描 */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime simulateTime;
}
