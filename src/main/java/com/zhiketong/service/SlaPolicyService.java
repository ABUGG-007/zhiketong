package com.zhiketong.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhiketong.common.BusinessException;
import com.zhiketong.common.ResultCode;
import com.zhiketong.dto.sla.SlaPolicyRequest;
import com.zhiketong.dto.sla.SlaPolicyVO;
import com.zhiketong.entity.OrderSlaPolicy;
import com.zhiketong.mapper.OrderSlaPolicyMapper;
import com.zhiketong.security.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 工单 SLA 策略配置服务（R-127 ~ R-132）：按优先级维护三级时限、预警阈值与升级规则。
 *
 * <p>控制层只做参数接收，唯一性、默认策略互斥与业务校验均在此实现。策略被工单计时记录以
 * 快照方式引用，改名或停用不影响已在跑的工单。租户隔离由 MyBatis-Plus 多租户拦截器按
 * {@code TenantContext} 自动注入 {@code tenant_id} 条件，业务代码不再手写租户过滤。</p>
 *
 * @author 智客通开发组
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SlaPolicyService {

    private final OrderSlaPolicyMapper policyMapper;

    /**
     * 策略列表（R-127）：可按优先级与状态过滤，按排序号升序。
     */
    public List<SlaPolicyVO> listPolicies(String priority, Integer status) {
        LambdaQueryWrapper<OrderSlaPolicy> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(priority), OrderSlaPolicy::getPriority, priority)
                .eq(status != null, OrderSlaPolicy::getStatus, status)
                .orderByAsc(OrderSlaPolicy::getSort).orderByAsc(OrderSlaPolicy::getId);
        List<OrderSlaPolicy> list = policyMapper.selectList(wrapper);
        List<SlaPolicyVO> result = new ArrayList<>(list.size());
        for (OrderSlaPolicy policy : list) {
            result.add(toVO(policy));
        }
        return result;
    }

    /**
     * 策略详情（R-128）。
     */
    public SlaPolicyVO policyDetail(Long id) {
        return toVO(requirePolicy(id));
    }

    /**
     * 新增策略（R-129）：策略名称同租户唯一；设为默认时清除同优先级其他默认策略。
     */
    @Transactional(rollbackFor = Exception.class)
    public SlaPolicyVO createPolicy(SlaPolicyRequest request, LoginUser loginUser) {
        validateLevels(request);
        String name = request.getPolicyName().trim();
        if (existsName(name, null)) {
            throw new BusinessException(ResultCode.CONFLICT, "策略名称已存在");
        }
        OrderSlaPolicy policy = new OrderSlaPolicy();
        applyRequest(policy, request, name);
        policy.setCreateBy(loginUser.getUserId());
        policy.setUpdateBy(loginUser.getUserId());
        if (Integer.valueOf(1).equals(policy.getDefaultFlag())) {
            clearOtherDefaults(policy.getPriority(), null);
        }
        policyMapper.insert(policy);
        log.info("新增 SLA 策略：id={}, name={}, priority={}", policy.getId(), name, policy.getPriority());
        return toVO(policy);
    }

    /**
     * 修改策略（R-130）：名称唯一性排除自身；已在跑的工单不受影响（使用快照）。
     */
    @Transactional(rollbackFor = Exception.class)
    public SlaPolicyVO updatePolicy(Long id, SlaPolicyRequest request, LoginUser loginUser) {
        OrderSlaPolicy policy = requirePolicy(id);
        validateLevels(request);
        String name = request.getPolicyName().trim();
        if (existsName(name, id)) {
            throw new BusinessException(ResultCode.CONFLICT, "策略名称已存在");
        }
        applyRequest(policy, request, name);
        policy.setUpdateBy(loginUser.getUserId());
        policy.setUpdateTime(LocalDateTime.now());
        if (Integer.valueOf(1).equals(policy.getDefaultFlag())) {
            clearOtherDefaults(policy.getPriority(), id);
        }
        policyMapper.updateById(policy);
        log.info("修改 SLA 策略：id={}, name={}", id, name);
        return toVO(policy);
    }

    /**
     * 启用/停用策略（R-131）：停用后新建工单不再匹配该策略。
     */
    @Transactional(rollbackFor = Exception.class)
    public SlaPolicyVO updatePolicyStatus(Long id, Integer status, LoginUser loginUser) {
        OrderSlaPolicy policy = requirePolicy(id);
        policy.setStatus(status);
        policy.setUpdateBy(loginUser.getUserId());
        policy.setUpdateTime(LocalDateTime.now());
        policyMapper.updateById(policy);
        return toVO(policy);
    }

    /**
     * 删除策略（R-132）：逻辑删除，历史工单仍按快照计时。
     */
    @Transactional(rollbackFor = Exception.class)
    public void deletePolicy(Long id, LoginUser loginUser) {
        OrderSlaPolicy policy = requirePolicy(id);
        policyMapper.deleteById(policy.getId());
        log.info("删除 SLA 策略：id={}, name={}, operatorId={}", id, policy.getPolicyName(), loginUser.getUserId());
    }

    /**
     * 匹配指定优先级的默认启用策略，无匹配返回 null（由调用方回退配置文件兜底时限）。
     *
     * @param priority 优先级 P0-P3
     * @return 默认启用策略，可能为 null
     */
    public OrderSlaPolicy matchDefaultPolicy(String priority) {
        LambdaQueryWrapper<OrderSlaPolicy> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderSlaPolicy::getPriority, priority)
                .eq(OrderSlaPolicy::getStatus, 1)
                .eq(OrderSlaPolicy::getDefaultFlag, 1)
                .orderByAsc(OrderSlaPolicy::getSort)
                .last("LIMIT 1");
        return policyMapper.selectOne(wrapper);
    }

    private OrderSlaPolicy requirePolicy(Long id) {
        // selectById 经多租户拦截器自动附加 tenant_id 条件，跨租户访问直接查不到
        OrderSlaPolicy policy = id == null ? null : policyMapper.selectById(id);
        if (policy == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "SLA 策略不存在");
        }
        return policy;
    }

    private boolean existsName(String name, Long excludeId) {
        LambdaQueryWrapper<OrderSlaPolicy> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderSlaPolicy::getPolicyName, name)
                .ne(excludeId != null, OrderSlaPolicy::getId, excludeId);
        Long count = policyMapper.selectCount(wrapper);
        return count != null && count > 0;
    }

    private void clearOtherDefaults(String priority, Long excludeId) {
        LambdaUpdateWrapper<OrderSlaPolicy> update = new LambdaUpdateWrapper<>();
        update.eq(OrderSlaPolicy::getPriority, priority)
                .ne(excludeId != null, OrderSlaPolicy::getId, excludeId)
                .set(OrderSlaPolicy::getDefaultFlag, 0);
        policyMapper.update(null, update);
    }

    /**
     * 校验至少启用一级时限，避免创建无任何 SLA 约束的空策略。
     */
    private void validateLevels(SlaPolicyRequest request) {
        int first = request.getFirstResponseMinutes() == null ? 0 : request.getFirstResponseMinutes();
        int reply = request.getReplyIntervalMinutes() == null ? 0 : request.getReplyIntervalMinutes();
        int resolve = request.getResolveMinutes() == null ? 0 : request.getResolveMinutes();
        if (first <= 0 && reply <= 0 && resolve <= 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "至少需要启用一级时限（首响 / 回复间隔 / 解决）");
        }
    }

    private void applyRequest(OrderSlaPolicy policy, SlaPolicyRequest request, String name) {
        policy.setPolicyName(name);
        policy.setPriority(request.getPriority());
        policy.setFirstResponseMinutes(request.getFirstResponseMinutes() == null ? 0 : request.getFirstResponseMinutes());
        policy.setReplyIntervalMinutes(request.getReplyIntervalMinutes() == null ? 0 : request.getReplyIntervalMinutes());
        policy.setResolveMinutes(request.getResolveMinutes() == null ? 0 : request.getResolveMinutes());
        policy.setWarnPercent(request.getWarnPercent() == null ? 80 : request.getWarnPercent());
        policy.setEscalateMinutes(request.getEscalateMinutes() == null ? 0 : request.getEscalateMinutes());
        policy.setEscalateRole(StringUtils.hasText(request.getEscalateRole()) ? request.getEscalateRole() : "ADMIN");
        policy.setDefaultFlag(request.getDefaultFlag() == null ? 0 : request.getDefaultFlag());
        policy.setDescription(trimToNull(request.getDescription()));
        policy.setSort(request.getSort() == null ? 0 : request.getSort());
        policy.setStatus(request.getStatus() == null ? 1 : request.getStatus());
    }

    private SlaPolicyVO toVO(OrderSlaPolicy policy) {
        SlaPolicyVO vo = new SlaPolicyVO();
        vo.setId(policy.getId());
        vo.setPolicyName(policy.getPolicyName());
        vo.setPriority(policy.getPriority());
        vo.setFirstResponseMinutes(policy.getFirstResponseMinutes());
        vo.setReplyIntervalMinutes(policy.getReplyIntervalMinutes());
        vo.setResolveMinutes(policy.getResolveMinutes());
        vo.setWarnPercent(policy.getWarnPercent());
        vo.setEscalateMinutes(policy.getEscalateMinutes());
        vo.setEscalateRole(policy.getEscalateRole());
        vo.setDefaultFlag(policy.getDefaultFlag());
        vo.setDescription(policy.getDescription());
        vo.setSort(policy.getSort());
        vo.setStatus(policy.getStatus());
        vo.setCreateTime(policy.getCreateTime());
        vo.setUpdateTime(policy.getUpdateTime());
        return vo;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
