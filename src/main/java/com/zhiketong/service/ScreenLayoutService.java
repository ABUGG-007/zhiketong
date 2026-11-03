package com.zhiketong.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiketong.common.BusinessException;
import com.zhiketong.common.ResultCode;
import com.zhiketong.dto.ops.ScreenLayoutRequest;
import com.zhiketong.dto.ops.ScreenLayoutVO;
import com.zhiketong.entity.ScreenLayout;
import com.zhiketong.enums.AuditAction;
import com.zhiketong.enums.AuditModule;
import com.zhiketong.mapper.ScreenLayoutMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 大屏布局配置服务（S6-C，DB-07：R-215 ~ R-217）。
 *
 * <p>布局只描述「怎么摆」：组件显隐、顺序、展示密度与轮询间隔。组件白名单固定在本类中，
 * 指标口径仍由 {@link OpsService} 统一提供，配置化不会衍生第二套统计规则。</p>
 *
 * <p>写入策略：一个租户只有一行 {@code layout_code = default} 的配置，
 * 首次保存执行插入，之后执行更新；重置即删除该行回到内置默认布局。</p>
 *
 * @author 智客通开发组
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScreenLayoutService {

    /** 默认布局编码 */
    public static final String DEFAULT_CODE = "default";

    /** 默认布局名称 */
    private static final String DEFAULT_NAME = "默认大屏";

    /** 展示密度：舒适 */
    public static final String DENSITY_COMFORTABLE = "COMFORTABLE";

    /** 展示密度：紧凑 */
    public static final String DENSITY_COMPACT = "COMPACT";

    /** 默认刷新间隔（秒） */
    private static final int DEFAULT_REFRESH_SECONDS = 15;

    /** 刷新间隔下限（秒） */
    private static final int MIN_REFRESH_SECONDS = 5;

    /** 刷新间隔上限（秒） */
    private static final int MAX_REFRESH_SECONDS = 300;

    /**
     * 组件白名单：编码 -> 名称与说明，顺序即默认布局顺序。
     * 新增组件必须先加到这里，前端才能配置与渲染。
     */
    private static final Map<String, String[]> WIDGET_CATALOG = new LinkedHashMap<>();

    static {
        WIDGET_CATALOG.put("REALTIME", new String[]{"实时接待指标", "排队人数、最长等待、在线坐席等实时值"});
        WIDGET_CATALOG.put("TODAY", new String[]{"今日概览", "今日会话量、首响均值、满意度等累计值"});
        WIDGET_CATALOG.put("AGENTS", new String[]{"坐席实时表现", "在线坐席负载、接待量、平均响应"});
        WIDGET_CATALOG.put("TREND", new String[]{"分时趋势", "当日各小时会话量与转人工量走势"});
        WIDGET_CATALOG.put("ALERTS", new String[]{"实时告警", "未处理告警与预警提示"});
    }

    private final ScreenLayoutMapper screenLayoutMapper;
    private final AuditLogService auditLogService;

    /**
     * 查询当前租户大屏布局（R-215）：未保存过自定义配置时返回内置默认布局。
     *
     * @return 布局配置
     */
    public ScreenLayoutVO layout() {
        ScreenLayout record = findLayout();
        return record == null ? defaultLayout() : toVO(record);
    }

    /**
     * 保存布局（R-216）：组件编码与顺序按白名单校验后落库。
     *
     * @param request    布局请求
     * @param operatorId 操作人 ID
     * @return 保存后的布局
     */
    @Transactional(rollbackFor = Exception.class)
    public ScreenLayoutVO save(ScreenLayoutRequest request, Long operatorId) {
        String density = normalizeDensity(request.getDensity());
        int refreshSeconds = normalizeRefreshSeconds(request.getRefreshSeconds());
        Map<String, ScreenLayoutVO.Widget> merged = mergeWidgets(request.getWidgets());

        ScreenLayout existing = findLayout();
        ScreenLayout target = existing == null ? new ScreenLayout() : existing;
        if (existing == null) {
            target.setLayoutCode(DEFAULT_CODE);
            target.setStatus(1);
            target.setDeleted(0);
        }
        target.setLayoutName(StringUtils.hasText(request.getLayoutName())
                ? request.getLayoutName().trim() : DEFAULT_NAME);
        target.setDensity(density);
        target.setRefreshSeconds(refreshSeconds);
        target.setWidgets(serialize(merged));
        target.setUpdateBy(operatorId);
        target.setUpdateTime(LocalDateTime.now());
        if (existing == null) {
            screenLayoutMapper.insert(target);
        } else {
            screenLayoutMapper.updateById(target);
        }

        auditLogService.record(AuditModule.VOC, AuditAction.UPDATE)
                .target("SCREEN_LAYOUT", target.getId(), target.getLayoutName())
                .detail("保存大屏布局：" + target.getLayoutName())
                .after(target.getWidgets())
                .save();
        log.info("保存大屏布局：tenantLayoutId={}, density={}, refresh={}s, operatorId={}",
                target.getId(), density, refreshSeconds, operatorId);
        return toVO(target);
    }

    /**
     * 重置为内置默认布局（R-217）：删除租户自定义配置。
     *
     * @param operatorId 操作人 ID
     * @return 默认布局
     */
    @Transactional(rollbackFor = Exception.class)
    public ScreenLayoutVO reset(Long operatorId) {
        ScreenLayout existing = findLayout();
        if (existing != null) {
            screenLayoutMapper.deleteById(existing.getId());
            auditLogService.record(AuditModule.VOC, AuditAction.DELETE)
                    .target("SCREEN_LAYOUT", existing.getId(), existing.getLayoutName())
                    .detail("重置大屏布局为默认")
                    .before(existing.getWidgets())
                    .save();
            log.info("重置大屏布局：id={}, operatorId={}", existing.getId(), operatorId);
        }
        return defaultLayout();
    }

    // ------------------------------------------------------------------
    // 内部方法
    // ------------------------------------------------------------------

    private ScreenLayout findLayout() {
        List<ScreenLayout> records = screenLayoutMapper.selectList(
                new LambdaQueryWrapper<ScreenLayout>()
                        .eq(ScreenLayout::getLayoutCode, DEFAULT_CODE)
                        .last("LIMIT 1"));
        return records.isEmpty() ? null : records.get(0);
    }

    /**
     * 内置默认布局：白名单全部展示，顺序与白名单一致。
     */
    private ScreenLayoutVO defaultLayout() {
        ScreenLayoutVO vo = new ScreenLayoutVO();
        vo.setLayoutCode(DEFAULT_CODE);
        vo.setLayoutName(DEFAULT_NAME);
        vo.setDensity(DENSITY_COMFORTABLE);
        vo.setDensityName(densityName(DENSITY_COMFORTABLE));
        vo.setRefreshSeconds(DEFAULT_REFRESH_SECONDS);
        vo.setDefaultLayout(true);
        int order = 1;
        for (Map.Entry<String, String[]> entry : WIDGET_CATALOG.entrySet()) {
            vo.getWidgets().add(widget(entry.getKey(), entry.getValue(), Boolean.TRUE, order++));
        }
        return vo;
    }

    private ScreenLayoutVO toVO(ScreenLayout record) {
        ScreenLayoutVO vo = defaultLayout();
        vo.setDefaultLayout(false);
        vo.setUpdateTime(record.getUpdateTime());
        if (StringUtils.hasText(record.getLayoutName())) {
            vo.setLayoutName(record.getLayoutName());
        }
        vo.setDensity(normalizeDensity(record.getDensity()));
        vo.setDensityName(densityName(vo.getDensity()));
        vo.setRefreshSeconds(normalizeRefreshSeconds(record.getRefreshSeconds()));
        Map<String, Boolean> visibleMap = new LinkedHashMap<>();
        Map<String, Integer> orderMap = new LinkedHashMap<>();
        parseStored(record.getWidgets(), visibleMap, orderMap);
        List<ScreenLayoutVO.Widget> widgets = new ArrayList<>();
        for (Map.Entry<String, String[]> entry : WIDGET_CATALOG.entrySet()) {
            String code = entry.getKey();
            widgets.add(widget(code, entry.getValue(),
                    visibleMap.getOrDefault(code, Boolean.TRUE),
                    orderMap.getOrDefault(code, widgets.size() + 1)));
        }
        widgets.sort(Comparator.comparingInt(ScreenLayoutVO.Widget::getOrder));
        for (int i = 0; i < widgets.size(); i++) {
            widgets.get(i).setOrder(i + 1);
        }
        vo.setWidgets(widgets);
        return vo;
    }

    /**
     * 合并请求与默认布局：未提交的组件沿用默认值，未知编码直接拒绝。
     */
    private Map<String, ScreenLayoutVO.Widget> mergeWidgets(List<ScreenLayoutRequest.WidgetRequest> requests) {
        Map<String, ScreenLayoutVO.Widget> merged = new LinkedHashMap<>();
        int order = 1;
        for (Map.Entry<String, String[]> entry : WIDGET_CATALOG.entrySet()) {
            merged.put(entry.getKey(), widget(entry.getKey(), entry.getValue(), Boolean.TRUE, order++));
        }
        if (requests == null || requests.isEmpty()) {
            return merged;
        }
        for (ScreenLayoutRequest.WidgetRequest item : requests) {
            String code = item.getCode() == null ? "" : item.getCode().trim().toUpperCase();
            if (!WIDGET_CATALOG.containsKey(code)) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "不支持的大屏组件：" + item.getCode());
            }
            ScreenLayoutVO.Widget widget = merged.get(code);
            widget.setVisible(item.getVisible() == null ? Boolean.TRUE : item.getVisible());
            widget.setOrder(item.getOrder() == null ? widget.getOrder() : item.getOrder());
        }
        List<ScreenLayoutVO.Widget> ordered = new ArrayList<>(merged.values());
        ordered.sort(Comparator.comparingInt(ScreenLayoutVO.Widget::getOrder));
        Map<String, ScreenLayoutVO.Widget> result = new LinkedHashMap<>();
        for (int i = 0; i < ordered.size(); i++) {
            ScreenLayoutVO.Widget widget = ordered.get(i);
            widget.setOrder(i + 1);
            result.put(widget.getCode(), widget);
        }
        return result;
    }

    private ScreenLayoutVO.Widget widget(String code, String[] meta, Boolean visible, int order) {
        ScreenLayoutVO.Widget widget = new ScreenLayoutVO.Widget();
        widget.setCode(code);
        widget.setName(meta[0]);
        widget.setDescription(meta[1]);
        widget.setVisible(visible);
        widget.setOrder(order);
        return widget;
    }

    /**
     * 序列化为 {@code [{code,visible,order}]}，只存配置不存名称，名称始终取白名单。
     */
    private String serialize(Map<String, ScreenLayoutVO.Widget> widgets) {
        StringBuilder builder = new StringBuilder("[");
        boolean first = true;
        for (ScreenLayoutVO.Widget widget : widgets.values()) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append("{\"code\":\"").append(widget.getCode())
                    .append("\",\"visible\":").append(Boolean.TRUE.equals(widget.getVisible()))
                    .append(",\"order\":").append(widget.getOrder()).append('}');
        }
        return builder.append(']').toString();
    }

    /**
     * 解析已存配置：容忍格式异常，解析不到时回落默认布局。
     */
    private void parseStored(String stored, Map<String, Boolean> visibleMap, Map<String, Integer> orderMap) {
        if (!StringUtils.hasText(stored)) {
            return;
        }
        String text = stored.replace(" ", "");
        int cursor = 0;
        while (cursor < text.length()) {
            int start = text.indexOf('{', cursor);
            int end = text.indexOf('}', start + 1);
            if (start < 0 || end < 0) {
                return;
            }
            String item = text.substring(start + 1, end);
            cursor = end + 1;
            String code = extractString(item, "code");
            if (code == null || !WIDGET_CATALOG.containsKey(code)) {
                continue;
            }
            String visible = extractRaw(item, "visible");
            String order = extractRaw(item, "order");
            if (visible != null) {
                visibleMap.put(code, Boolean.parseBoolean(visible));
            }
            if (order != null) {
                try {
                    orderMap.put(code, Integer.parseInt(order));
                } catch (NumberFormatException ignored) {
                    // 顺序值异常时按默认顺序渲染
                }
            }
        }
    }

    private String extractString(String item, String key) {
        String raw = extractRaw(item, key);
        if (raw == null) {
            return null;
        }
        return raw.replace("\"", "").toUpperCase();
    }

    private String extractRaw(String item, String key) {
        String marker = "\"" + key + "\":";
        int index = item.indexOf(marker);
        if (index < 0) {
            return null;
        }
        int valueStart = index + marker.length();
        int valueEnd = valueStart;
        while (valueEnd < item.length() && item.charAt(valueEnd) != ',') {
            valueEnd++;
        }
        String value = item.substring(valueStart, valueEnd).trim();
        return value.isEmpty() ? null : value;
    }

    private String normalizeDensity(String density) {
        if (!StringUtils.hasText(density)) {
            return DENSITY_COMFORTABLE;
        }
        String value = density.trim().toUpperCase();
        if (!DENSITY_COMFORTABLE.equals(value) && !DENSITY_COMPACT.equals(value)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "展示密度只能为 COMFORTABLE 或 COMPACT");
        }
        return value;
    }

    private String densityName(String density) {
        return Objects.equals(density, DENSITY_COMPACT) ? "紧凑" : "舒适";
    }

    private int normalizeRefreshSeconds(Integer refreshSeconds) {
        if (refreshSeconds == null) {
            return DEFAULT_REFRESH_SECONDS;
        }
        if (refreshSeconds < MIN_REFRESH_SECONDS || refreshSeconds > MAX_REFRESH_SECONDS) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "刷新间隔须在 " + MIN_REFRESH_SECONDS + "-" + MAX_REFRESH_SECONDS + " 秒之间");
        }
        return refreshSeconds;
    }
}
