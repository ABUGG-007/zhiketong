package com.zhiketong.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiketong.common.BusinessException;
import com.zhiketong.common.PageResult;
import com.zhiketong.common.ResultCode;
import com.zhiketong.dto.admin.LoginLogVO;
import com.zhiketong.entity.SysLoginLog;
import com.zhiketong.mapper.SysLoginLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * 登录日志查询服务（M1，R-70）。
 *
 * <p>支持按账号/IP 模糊搜索、登录结果筛选与登录日期区间（包含起止当天）过滤，按 id 倒序分页。</p>
 */
@Service
@RequiredArgsConstructor
public class LoginLogService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final SysLoginLogMapper sysLoginLogMapper;

    /**
     * 登录日志分页列表（R-70）。
     */
    @Transactional(readOnly = true)
    public PageResult<LoginLogVO> page(int pageNum, int pageSize, String keyword,
                                       Integer success, String startDate, String endDate) {
        LambdaQueryWrapper<SysLoginLog> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(SysLoginLog::getUsername, kw)
                    .or().like(SysLoginLog::getIp, kw));
        }
        if (success != null) {
            wrapper.eq(SysLoginLog::getSuccess, success);
        }
        LocalDateTime start = parseStartDate(startDate);
        if (start != null) {
            wrapper.ge(SysLoginLog::getCreateTime, start);
        }
        LocalDateTime endExclusive = parseEndDateExclusive(endDate);
        if (endExclusive != null) {
            wrapper.lt(SysLoginLog::getCreateTime, endExclusive);
        }
        wrapper.orderByDesc(SysLoginLog::getId);

        Page<SysLoginLog> page = sysLoginLogMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<LoginLogVO> list = new ArrayList<>(page.getRecords().size());
        for (SysLoginLog loginLog : page.getRecords()) {
            list.add(LoginLogVO.of(loginLog));
        }
        return PageResult.of(page, list);
    }

    /**
     * 起始日期（含当天）：yyyy-MM-dd 00:00:00。
     */
    private LocalDateTime parseStartDate(String startDate) {
        LocalDate date = parseDate(startDate, "startDate");
        return date == null ? null : date.atStartOfDay();
    }

    /**
     * 结束日期（含当天）：yyyy-MM-dd 次日 00:00:00，查询时使用小于该时刻。
     */
    private LocalDateTime parseEndDateExclusive(String endDate) {
        LocalDate date = parseDate(endDate, "endDate");
        return date == null ? null : date.plusDays(1).atStartOfDay();
    }

    private LocalDate parseDate(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim(), DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, fieldName + " 格式须为 yyyy-MM-dd");
        }
    }
}
