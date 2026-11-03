package com.zhiketong.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiketong.ai.QualityPrompts;
import com.zhiketong.common.BusinessException;
import com.zhiketong.common.ResultCode;
import com.zhiketong.dto.quality.QualityRuleRequest;
import com.zhiketong.dto.quality.QualityRuleVO;
import com.zhiketong.entity.QualityRule;
import com.zhiketong.enums.AuditAction;
import com.zhiketong.enums.AuditModule;
import com.zhiketong.enums.QualityRuleType;
import com.zhiketong.enums.QualitySeverity;
import com.zhiketong.mapper.QualityRuleMapper;
import com.zhiketong.security.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 质检规则配置服务（S5-A，QC-04 / QC-05）。
 *
 * <p>四条归一化约束，保证规则进入引擎后语义唯一、可判定：</p>
 * <ol>
 *   <li>语义类与情感类由大模型判定，不接收关键词，匹配方式固定 CONTAINS、阈值固定 0；</li>
 *   <li>服务规范类与敏感词类必须配置关键词，否则无法判定；</li>
 *   <li>内置「回复超时」规则按阈值分钟数计时，不接收关键词；</li>
 *   <li>规则编码由服务端维护：内置规则编码固定，自定义规则生成 {@code CUSTOM_} 前缀编码。</li>
 * </ol>
 *
 * @author 智客通开发组
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QualityRuleService {

    /** 内置「回复超时」规则编码，质检引擎据此走计时判定 */
    public static final String RULE_SLOW_REPLY = "SLOW_REPLY";

    /** 自定义规则编码前缀 */
    private static final String CUSTOM_PREFIX = "CUSTOM_";

    private static final int DEFAULT_DEDUCT_SCORE = 5;

    private final QualityRuleMapper qualityRuleMapper;
    private final AuditLogService auditLogService;

    /**
     * 规则列表（R-160）：按排序号升序，可按类型与状态过滤。
     *
     * @param ruleType 规则类型，为空不过滤
     * @param status   状态 1/0，为空不过滤
     * @return 规则列表
     */
    public List<QualityRuleVO> listRules(String ruleType, Integer status) {
        LambdaQueryWrapper<QualityRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(ruleType), QualityRule::getRuleType, ruleType)
                .eq(status != null, QualityRule::getStatus, status)
                .orderByAsc(QualityRule::getSort)
                .orderByAsc(QualityRule::getId);
        List<QualityRule> rules = qualityRuleMapper.selectList(wrapper);
        List<QualityRuleVO> result = new ArrayList<>(rules.size());
        for (QualityRule rule : rules) {
            result.add(QualityRuleVO.of(rule));
        }
        return result;
    }

    /**
     * 规则详情（R-161）。
     *
     * @param id 规则 ID
     * @return 规则详情
     */
    public QualityRuleVO ruleDetail(Long id) {
        return QualityRuleVO.of(requireRule(id));
    }

    /**
     * 新增规则（R-162）：规则名称同租户唯一，编码由服务端生成。
     *
     * @param request   规则请求
     * @param loginUser 登录人
     * @return 新建规则
     */
    @Transactional(rollbackFor = Exception.class)
    public QualityRuleVO createRule(QualityRuleRequest request, LoginUser loginUser) {
        String name = request.getRuleName().trim();
        if (existsName(name, null)) {
            throw new BusinessException(ResultCode.CONFLICT, "规则名称已存在");
        }
        QualityRule rule = new QualityRule();
        rule.setRuleCode(generateCode());
        applyRequest(rule, request, name);
        rule.setCreateBy(loginUser.getUserId());
        rule.setUpdateBy(loginUser.getUserId());
        qualityRuleMapper.insert(rule);

        auditLogService.record(AuditModule.QUALITY, AuditAction.CREATE)
                .target("QUALITY_RULE", rule.getId(), rule.getRuleName())
                .detail("新增质检规则：" + rule.getRuleName())
                .after(rule)
                .save();
        log.info("新增质检规则：id={}, code={}, name={}, type={}",
                rule.getId(), rule.getRuleCode(), name, rule.getRuleType());
        return QualityRuleVO.of(rule);
    }

    /**
     * 修改规则（R-163）：编码不可变，历史质检明细已按快照留存不受影响。
     *
     * @param id        规则 ID
     * @param request   规则请求
     * @param loginUser 登录人
     * @return 修改后的规则
     */
    @Transactional(rollbackFor = Exception.class)
    public QualityRuleVO updateRule(Long id, QualityRuleRequest request, LoginUser loginUser) {
        QualityRule rule = requireRule(id);
        String name = request.getRuleName().trim();
        if (existsName(name, id)) {
            throw new BusinessException(ResultCode.CONFLICT, "规则名称已存在");
        }
        QualityRule before = copyOf(rule);
        applyRequest(rule, request, name);
        rule.setUpdateBy(loginUser.getUserId());
        rule.setUpdateTime(LocalDateTime.now());
        qualityRuleMapper.updateById(rule);

        auditLogService.record(AuditModule.QUALITY, AuditAction.UPDATE)
                .target("QUALITY_RULE", rule.getId(), rule.getRuleName())
                .detail("修改质检规则：" + rule.getRuleName())
                .before(before)
                .after(rule)
                .save();
        log.info("修改质检规则：id={}, name={}, operatorId={}", id, name, loginUser.getUserId());
        return QualityRuleVO.of(rule);
    }

    /**
     * 启用/停用规则（R-164）：停用后不参与后续质检，历史记录不受影响。
     *
     * @param id        规则 ID
     * @param status    目标状态 1/0
     * @param loginUser 登录人
     * @return 修改后的规则
     */
    @Transactional(rollbackFor = Exception.class)
    public QualityRuleVO updateRuleStatus(Long id, Integer status, LoginUser loginUser) {
        QualityRule rule = requireRule(id);
        rule.setStatus(status);
        rule.setUpdateBy(loginUser.getUserId());
        rule.setUpdateTime(LocalDateTime.now());
        qualityRuleMapper.updateById(rule);

        auditLogService.record(AuditModule.QUALITY,
                        Integer.valueOf(1).equals(status) ? AuditAction.ENABLE : AuditAction.DISABLE)
                .target("QUALITY_RULE", rule.getId(), rule.getRuleName())
                .detail((Integer.valueOf(1).equals(status) ? "启用" : "停用") + "质检规则：" + rule.getRuleName())
                .after(rule)
                .save();
        return QualityRuleVO.of(rule);
    }

    /**
     * 删除规则（R-165）：逻辑删除；已生成的质检明细保留规则快照，回看不受影响。
     *
     * @param id        规则 ID
     * @param loginUser 登录人
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteRule(Long id, LoginUser loginUser) {
        QualityRule rule = requireRule(id);
        qualityRuleMapper.deleteById(rule.getId());

        auditLogService.record(AuditModule.QUALITY, AuditAction.DELETE)
                .target("QUALITY_RULE", rule.getId(), rule.getRuleName())
                .detail("删除质检规则：" + rule.getRuleName())
                .before(rule)
                .save();
        log.info("删除质检规则：id={}, name={}, operatorId={}", id, rule.getRuleName(), loginUser.getUserId());
    }

    /**
     * 载入当前租户全部启用规则，供质检引擎评估（按排序号升序）。
     *
     * @return 启用规则列表，可能为空
     */
    public List<QualityRule> listEnabledRules() {
        LambdaQueryWrapper<QualityRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(QualityRule::getStatus, 1)
                .orderByAsc(QualityRule::getSort)
                .orderByAsc(QualityRule::getId);
        return qualityRuleMapper.selectList(wrapper);
    }

    private QualityRule requireRule(Long id) {
        // selectById 经多租户拦截器自动附加 tenant_id 条件，跨租户访问直接查不到
        QualityRule rule = id == null ? null : qualityRuleMapper.selectById(id);
        if (rule == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "质检规则不存在");
        }
        return rule;
    }

    private boolean existsName(String name, Long excludeId) {
        LambdaQueryWrapper<QualityRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(QualityRule::getRuleName, name)
                .ne(excludeId != null, QualityRule::getId, excludeId);
        Long count = qualityRuleMapper.selectCount(wrapper);
        return count != null && count > 0;
    }

    /**
     * 请求归一化 + 按规则类型做可判定性校验。
     */
    private void applyRequest(QualityRule rule, QualityRuleRequest request, String name) {
        QualityRuleType type = QualityRuleType.fromCode(request.getRuleType());
        if (type == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "规则类型不合法");
        }
        List<String> keywords = QualityPrompts.splitKeywords(request.getKeywords());

        rule.setRuleName(name);
        rule.setRuleType(type.getCode());
        QualitySeverity severity = QualitySeverity.fromCode(request.getSeverity());
        rule.setSeverity(severity == null ? QualitySeverity.MEDIUM.getCode() : severity.getCode());
        rule.setDeductScore(normalizeDeduct(request.getDeductScore()));

        if (type.isAiEvaluated()) {
            // 语义/情感类由大模型判定，关键词与阈值无意义，清空避免误配
            rule.setKeywords(null);
            rule.setMatchMode("CONTAINS");
            rule.setThresholdValue(0);
        } else if (RULE_SLOW_REPLY.equals(rule.getRuleCode())) {
            // 内置回复超时规则按阈值分钟数计时，不需要关键词
            int threshold = request.getThresholdValue() == null ? 0 : request.getThresholdValue();
            if (threshold <= 0) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "「回复超时」规则的阈值必须大于 0 分钟");
            }
            rule.setKeywords(null);
            rule.setMatchMode("CONTAINS");
            rule.setThresholdValue(threshold);
        } else {
            if (keywords.isEmpty()) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "服务规范类与敏感词类规则必须配置关键词");
            }
            rule.setKeywords(String.join(",", keywords));
            rule.setMatchMode("ABSENT".equalsIgnoreCase(request.getMatchMode()) ? "ABSENT" : "CONTAINS");
            rule.setThresholdValue(0);
        }
        rule.setDescription(trimToNull(request.getDescription()));
        rule.setSort(request.getSort() == null ? 0 : request.getSort());
        rule.setStatus(request.getStatus() == null ? 1 : request.getStatus());
    }

    private int normalizeDeduct(Integer deductScore) {
        if (deductScore == null) {
            return DEFAULT_DEDUCT_SCORE;
        }
        return Math.max(0, Math.min(30, deductScore));
    }

    /**
     * 生成自定义规则编码：CUSTOM_ + 6 位随机十六进制，冲突概率可忽略且受唯一键兜底。
     */
    private String generateCode() {
        String suffix = UUID.randomUUID().toString().replace("-", "")
                .substring(0, 6).toUpperCase(Locale.ROOT);
        return CUSTOM_PREFIX + suffix;
    }

    private QualityRule copyOf(QualityRule source) {
        QualityRule copy = new QualityRule();
        copy.setId(source.getId());
        copy.setRuleCode(source.getRuleCode());
        copy.setRuleName(source.getRuleName());
        copy.setRuleType(source.getRuleType());
        copy.setSeverity(source.getSeverity());
        copy.setDeductScore(source.getDeductScore());
        copy.setKeywords(source.getKeywords());
        copy.setMatchMode(source.getMatchMode());
        copy.setThresholdValue(source.getThresholdValue());
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
