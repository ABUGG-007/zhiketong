package com.zhiketong.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiketong.common.BusinessException;
import com.zhiketong.common.ResultCode;
import com.zhiketong.dto.alert.AlertRuleRequest;
import com.zhiketong.dto.alert.AlertRuleVO;
import com.zhiketong.entity.AlertRule;
import com.zhiketong.enums.AlertMetric;
import com.zhiketong.enums.AlertRuleType;
import com.zhiketong.enums.AlertSeverity;
import com.zhiketong.enums.AuditAction;
import com.zhiketong.enums.AuditModule;
import com.zhiketong.mapper.AlertRuleMapper;
import com.zhiketong.security.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 告警规则配置服务（S6-B，AS-11）。
 *
 * <p>规则 = 「指标 + 阈值 + 冷却 + 通知对象」：</p>
 * <ul>
 *   <li>{@code ownerId = 0} 为租户级规则，管理员维护并对全租户生效；内置 9 条规则同样归属租户级；</li>
 *   <li>{@code ownerId > 0} 为坐席个人规则，只在该坐席接待的会话上生效，坐席只能操作自己的规则；</li>
 *   <li>指标编码由 {@link AlertMetric} 白名单约束，规则类型与阈值单位由指标推导，避免出现无法判定的组合；</li>
 *   <li>内置规则允许调整阈值、冷却与启停，但不允许删除，保证告警中心始终具备基础兜底能力。</li>
 * </ul>
 *
 * @author 智客通开发组
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertRuleService {

    /** 自定义规则编码前缀 */
    private static final String CUSTOM_PREFIX = "CUSTOM_";

    /** 通知对象白名单 */
    private static final List<String> NOTIFY_ROLES = Arrays.asList("ADMIN", "AGENT", "ALL");

    private final AlertRuleMapper alertRuleMapper;
    private final AuditLogService auditLogService;

    /**
     * 规则列表（R-180 管理端）：租户内全部规则，租户级在前、个人规则在后。
     *
     * @param ruleType 规则类型，为空不过滤
     * @param status   状态 1/0，为空不过滤
     */
    public List<AlertRuleVO> listRules(String ruleType, Integer status) {
        LambdaQueryWrapper<AlertRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(ruleType), AlertRule::getRuleType, ruleType)
                .eq(status != null, AlertRule::getStatus, status)
                .orderByAsc(AlertRule::getOwnerId)
                .orderByAsc(AlertRule::getSort)
                .orderByAsc(AlertRule::getId);
        return toVOs(alertRuleMapper.selectList(wrapper));
    }

    /**
     * 规则列表（R-190 坐席端）：租户级规则 + 本人个人规则。
     *
     * @param agentId 当前坐席用户 ID
     */
    public List<AlertRuleVO> listAgentRules(Long agentId) {
        LambdaQueryWrapper<AlertRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> w.eq(AlertRule::getOwnerId, 0L).or().eq(AlertRule::getOwnerId, agentId))
                .orderByAsc(AlertRule::getOwnerId)
                .orderByAsc(AlertRule::getSort)
                .orderByAsc(AlertRule::getId);
        return toVOs(alertRuleMapper.selectList(wrapper));
    }

    /**
     * 规则详情（管理端与坐席端共用的内部校验入口）。
     */
    public AlertRuleVO detail(Long id) {
        return AlertRuleVO.of(requireRule(id));
    }

    /**
     * 新增规则（R-181 管理端租户级 / R-191 坐席个人）。
     *
     * @param request   规则请求
     * @param loginUser 登录人
     * @param ownerId   归属坐席 ID，0 表示租户级
     */
    @Transactional(rollbackFor = Exception.class)
    public AlertRuleVO createRule(AlertRuleRequest request, LoginUser loginUser, Long ownerId) {
        long owner = ownerId == null ? 0L : ownerId;
        String name = request.getRuleName().trim();
        if (existsName(name, owner, null)) {
            throw new BusinessException(ResultCode.CONFLICT, "规则名称已存在");
        }
        AlertRule rule = new AlertRule();
        rule.setRuleCode(generateCode());
        rule.setOwnerId(owner);
        rule.setBuiltIn(0);
        applyRequest(rule, request, name, owner);
        rule.setCreateBy(loginUser.getUserId());
        rule.setUpdateBy(loginUser.getUserId());
        alertRuleMapper.insert(rule);

        auditLogService.record(AuditModule.ALERT, AuditAction.CREATE)
                .target("ALERT_RULE", rule.getId(), rule.getRuleName())
                .detail((owner > 0 ? "新增坐席告警规则：" : "新增告警规则：") + rule.getRuleName())
                .after(rule)
                .save();
        log.info("新增告警规则：id={}, code={}, metric={}, owner={}, threshold={}",
                rule.getId(), rule.getRuleCode(), rule.getMetricCode(), owner, rule.getThresholdValue());
        return AlertRuleVO.of(rule);
    }

    /**
     * 修改规则（R-182 / R-192）：规则编码与归属不可变。
     *
     * @param id        规则 ID
     * @param request   规则请求
     * @param loginUser 登录人
     * @param ownerId   操作人归属坐席 ID，null 表示管理端（可改租户级规则）
     */
    @Transactional(rollbackFor = Exception.class)
    public AlertRuleVO updateRule(Long id, AlertRuleRequest request, LoginUser loginUser, Long ownerId) {
        AlertRule rule = requireRule(id);
        assertOperable(rule, ownerId);
        String name = request.getRuleName().trim();
        if (existsName(name, rule.getOwnerId(), id)) {
            throw new BusinessException(ResultCode.CONFLICT, "规则名称已存在");
        }
        AlertRule before = copyOf(rule);
        applyRequest(rule, request, name, rule.getOwnerId());
        rule.setUpdateBy(loginUser.getUserId());
        rule.setUpdateTime(LocalDateTime.now());
        alertRuleMapper.updateById(rule);

        auditLogService.record(AuditModule.ALERT, AuditAction.UPDATE)
                .target("ALERT_RULE", rule.getId(), rule.getRuleName())
                .detail("修改告警规则：" + rule.getRuleName())
                .before(before)
                .after(rule)
                .save();
        log.info("修改告警规则：id={}, name={}, operatorId={}", id, name, loginUser.getUserId());
        return AlertRuleVO.of(rule);
    }

    /**
     * 启用/停用规则（R-183）。
     */
    @Transactional(rollbackFor = Exception.class)
    public AlertRuleVO updateRuleStatus(Long id, Integer status, LoginUser loginUser, Long ownerId) {
        AlertRule rule = requireRule(id);
        assertOperable(rule, ownerId);
        rule.setStatus(Integer.valueOf(0).equals(status) ? 0 : 1);
        rule.setUpdateBy(loginUser.getUserId());
        rule.setUpdateTime(LocalDateTime.now());
        alertRuleMapper.updateById(rule);

        boolean enabled = Integer.valueOf(1).equals(rule.getStatus());
        auditLogService.record(AuditModule.ALERT, enabled ? AuditAction.ENABLE : AuditAction.DISABLE)
                .target("ALERT_RULE", rule.getId(), rule.getRuleName())
                .detail((enabled ? "启用" : "停用") + "告警规则：" + rule.getRuleName())
                .after(rule)
                .save();
        return AlertRuleVO.of(rule);
    }

    /**
     * 删除规则：内置规则不可删除，历史告警事件保留规则快照不受影响。
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteRule(Long id, LoginUser loginUser, Long ownerId) {
        AlertRule rule = requireRule(id);
        assertOperable(rule, ownerId);
        if (Integer.valueOf(1).equals(rule.getBuiltIn())) {
            throw new BusinessException(ResultCode.CONFLICT, "内置规则不允许删除，可停用或调整阈值");
        }
        alertRuleMapper.deleteById(rule.getId());

        auditLogService.record(AuditModule.ALERT, AuditAction.DELETE)
                .target("ALERT_RULE", rule.getId(), rule.getRuleName())
                .detail("删除告警规则：" + rule.getRuleName())
                .before(rule)
                .save();
        log.info("删除告警规则：id={}, name={}, operatorId={}", id, rule.getRuleName(), loginUser.getUserId());
    }

    /**
     * 载入当前租户全部启用规则，供扫描引擎逐条评估。
     *
     * @return 启用规则列表，按归属与排序号升序
     */
    public List<AlertRule> listEnabledRules() {
        return alertRuleMapper.selectList(new LambdaQueryWrapper<AlertRule>()
                .eq(AlertRule::getStatus, 1)
                .orderByAsc(AlertRule::getOwnerId)
                .orderByAsc(AlertRule::getSort)
                .orderByAsc(AlertRule::getId));
    }

    private List<AlertRuleVO> toVOs(List<AlertRule> rules) {
        List<AlertRuleVO> list = new ArrayList<>(rules.size());
        for (AlertRule rule : rules) {
            list.add(AlertRuleVO.of(rule));
        }
        return list;
    }

    private AlertRule requireRule(Long id) {
        // selectById 经多租户拦截器自动附加 tenant_id 条件，跨租户访问直接查不到
        AlertRule rule = id == null ? null : alertRuleMapper.selectById(id);
        if (rule == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "告警规则不存在");
        }
        return rule;
    }

    /**
     * 坐席侧只能操作本人规则；管理端（ownerId 为 null）可操作租户内任意规则。
     */
    private void assertOperable(AlertRule rule, Long ownerId) {
        if (ownerId == null) {
            return;
        }
        if (rule.getOwnerId() == null || rule.getOwnerId() <= 0) {
            throw new BusinessException(ResultCode.FORBIDDEN, "租户级规则仅管理员可修改");
        }
        if (!ownerId.equals(rule.getOwnerId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能操作本人创建的告警规则");
        }
    }

    private boolean existsName(String name, Long ownerId, Long excludeId) {
        LambdaQueryWrapper<AlertRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AlertRule::getRuleName, name)
                .eq(AlertRule::getOwnerId, ownerId == null ? 0L : ownerId)
                .ne(excludeId != null, AlertRule::getId, excludeId);
        Long count = alertRuleMapper.selectCount(wrapper);
        return count != null && count > 0;
    }

    /**
     * 请求归一化：指标决定规则类型与阈值单位，阈值/冷却/通知对象在此处收敛为合法值。
     */
    private void applyRequest(AlertRule rule, AlertRuleRequest request, String name, long ownerId) {
        AlertMetric metric = AlertMetric.fromCode(request.getMetricCode());
        if (metric == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "指标编码不合法");
        }
        int threshold = request.getThresholdValue() == null ? 0 : request.getThresholdValue();
        if (metric == AlertMetric.LOW_RATING) {
            if (threshold < 1 || threshold > 5) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "「客户差评」阈值须为 1-5 星");
            }
        } else if (threshold <= 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "触发阈值必须大于 0");
        }

        String notifyRole = StringUtils.hasText(request.getNotifyRole())
                ? request.getNotifyRole().trim().toUpperCase(Locale.ROOT) : null;
        if (notifyRole != null && !NOTIFY_ROLES.contains(notifyRole)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "通知对象只能为 ADMIN / AGENT / ALL");
        }
        if (notifyRole == null) {
            notifyRole = ownerId > 0 ? "AGENT" : "ADMIN";
        }

        String keywords = trimToNull(request.getKeywords());
        if (metric == AlertMetric.SENSITIVE_WORD) {
            if (keywords == null) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "「会话命中敏感词」规则必须配置关键词");
            }
            keywords = String.join(",", splitKeywords(keywords));
        } else {
            keywords = null;
        }

        AlertSeverity severity = AlertSeverity.fromCode(request.getSeverity());
        rule.setRuleName(name);
        rule.setMetricCode(metric.getCode());
        rule.setRuleType(metric.getRuleType().getCode());
        rule.setThresholdUnit(metric.getUnit());
        rule.setThresholdValue(threshold);
        rule.setSeverity(severity == null ? AlertSeverity.WARN.getCode() : severity.getCode());
        rule.setCooldownMinutes(clamp(request.getCooldownMinutes() == null ? 10 : request.getCooldownMinutes(), 0, 1440));
        rule.setNotifyRole(notifyRole);
        rule.setKeywords(keywords);
        rule.setDescription(trimToNull(request.getDescription()));
        rule.setSort(request.getSort() == null ? 100 : request.getSort());
        rule.setStatus(request.getStatus() == null ? 1 : (Integer.valueOf(0).equals(request.getStatus()) ? 0 : 1));
    }

    /**
     * 关键词按中英文逗号、分号、竖线切分后去重，保持原有顺序。
     */
    private List<String> splitKeywords(String keywords) {
        List<String> result = new ArrayList<>();
        for (String part : keywords.split("[,，;；|]")) {
            String word = part.trim();
            if (!word.isEmpty() && !result.contains(word)) {
                result.add(word);
            }
        }
        return result;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * 生成自定义规则编码：CUSTOM_ + 6 位随机十六进制，冲突由唯一键兜底。
     */
    private String generateCode() {
        String suffix = UUID.randomUUID().toString().replace("-", "")
                .substring(0, 6).toUpperCase(Locale.ROOT);
        return CUSTOM_PREFIX + suffix;
    }

    private AlertRule copyOf(AlertRule source) {
        AlertRule copy = new AlertRule();
        copy.setId(source.getId());
        copy.setRuleCode(source.getRuleCode());
        copy.setMetricCode(source.getMetricCode());
        copy.setRuleName(source.getRuleName());
        copy.setRuleType(source.getRuleType());
        copy.setSeverity(source.getSeverity());
        copy.setThresholdValue(source.getThresholdValue());
        copy.setThresholdUnit(source.getThresholdUnit());
        copy.setCooldownMinutes(source.getCooldownMinutes());
        copy.setNotifyRole(source.getNotifyRole());
        copy.setOwnerId(source.getOwnerId());
        copy.setKeywords(source.getKeywords());
        copy.setDescription(source.getDescription());
        copy.setSort(source.getSort());
        copy.setStatus(source.getStatus());
        return copy;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
