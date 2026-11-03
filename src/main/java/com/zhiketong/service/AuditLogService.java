package com.zhiketong.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zhiketong.common.BusinessException;
import com.zhiketong.common.Constants;
import com.zhiketong.common.PageResult;
import com.zhiketong.common.ResultCode;
import com.zhiketong.dto.audit.AuditLogDetailVO;
import com.zhiketong.dto.audit.AuditLogVO;
import com.zhiketong.entity.AuditLog;
import com.zhiketong.entity.Tenant;
import com.zhiketong.enums.AuditAction;
import com.zhiketong.enums.AuditModule;
import com.zhiketong.mapper.AuditLogMapper;
import com.zhiketong.mapper.TenantMapper;
import com.zhiketong.security.LoginUser;
import com.zhiketong.security.SecurityUtils;
import com.zhiketong.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 通用操作审计服务（S4-C，PL-06）。
 *
 * <p>统一的埋点入口，业务侧只需声明「谁对哪个对象做了什么」，操作人、来源 IP、
 * 请求方法路径由本服务从 SecurityContext 与 RequestContextHolder 自动补全：</p>
 * <pre>{@code
 * auditLogService.record(AuditModule.KB, AuditAction.UPDATE)
 *         .target("KB_ENTRY", entry.getId(), entry.getQuestion())
 *         .detail("编辑知识词条：" + entry.getQuestion())
 *         .before(oldEntry).after(entry).save();
 * }</pre>
 *
 * <p>三条硬约束：</p>
 * <ol>
 *   <li>只追加：审计行写入后不再改写，也不做逻辑删除；</li>
 *   <li>只记录成功提交的变更，与业务同事务，业务回滚时审计行一并回滚；</li>
 *   <li>审计写入失败只告警，绝不影响业务主流程。</li>
 * </ol>
 *
 * <p>租户归属：平台管理员的上下文租户为 {@link TenantContext#PLATFORM_TENANT_ID}，
 * 直接写库会被数据库默认值落到租户 1，因此平台侧埋点必须调用
 * {@link AuditRecord#tenant(Long)} 声明被操作租户，由本服务切换到该租户作用域后落库。</p>
 *
 * @author 智客通开发组
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 需要脱敏的字段名（小写比较），密码、密钥、令牌一律不落审计表 */
    private static final Set<String> SENSITIVE_FIELDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "password", "oldpassword", "newpassword", "appsecret", "apikey", "secret", "token",
            "accesstoken", "privatekey", "signsecret")));

    /** 脱敏占位符 */
    private static final String MASK = "******";

    /** 单个快照 JSON 最大长度，超出截断，避免 TEXT 字段被超大对象撑爆 */
    private static final int MAX_SNAPSHOT_LENGTH = 4000;

    private static final int MAX_DETAIL_LENGTH = 1000;

    private static final int MAX_NAME_LENGTH = 128;

    private final AuditLogMapper auditLogMapper;
    private final TenantMapper tenantMapper;
    private final ObjectMapper objectMapper;

    /**
     * 创建一条审计记录（流式写法），调用 {@link AuditRecord#save()} 才会落库。
     *
     * @param module 业务模块
     * @param action 动作
     * @return 审计记录构造器
     */
    public AuditRecord record(AuditModule module, AuditAction action) {
        return new AuditRecord(module, action);
    }

    /**
     * 审计分页查询（R-158 平台端 / R-159 租户端）。
     *
     * <p>平台管理员上下文不过滤租户，可查全平台；传入 {@code tenantId} 时切换到该租户
     * 作用域由拦截器注入条件，禁止业务代码手写 tenant_id 过滤。租户管理员调用时
     * 拦截器自动限定本租户，若强行传其他 tenantId 直接 403。</p>
     *
     * @param startDate 开始日期（含当天），yyyy-MM-dd
     * @param endDate   结束日期（含当天），yyyy-MM-dd
     */
    @Transactional(readOnly = true)
    public PageResult<AuditLogVO> page(int pageNum, int pageSize, Long tenantId, String module,
                                       String action, String keyword,
                                       String startDate, String endDate) {
        if (tenantId != null) {
            if (!SecurityUtils.hasRole(Constants.ROLE_PLATFORM_ADMIN)) {
                throw new BusinessException(ResultCode.FORBIDDEN, "无权查看其他租户的操作审计");
            }
            return TenantContext.callAs(tenantId, () -> doPage(pageNum, pageSize, module, action,
                    keyword, startDate, endDate));
        }
        return doPage(pageNum, pageSize, module, action, keyword, startDate, endDate);
    }

    /**
     * 审计详情（R-158 平台端 / R-159 租户端），含变更前后快照。
     *
     * <p>租户管理员查询其他租户的记录时，拦截器会让查询结果为空，返回 404。</p>
     */
    @Transactional(readOnly = true)
    public AuditLogDetailVO detail(Long id) {
        AuditLog log = auditLogMapper.selectById(id);
        if (log == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "审计记录不存在");
        }
        AuditLogDetailVO vo = AuditLogDetailVO.of(log);
        vo.setTenantName(loadTenantNames(Collections.singletonList(log.getTenantId())).get(log.getTenantId()));
        return vo;
    }

    private PageResult<AuditLogVO> doPage(int pageNum, int pageSize, String module, String action,
                                          String keyword, String startDate, String endDate) {
        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(module)) {
            wrapper.eq(AuditLog::getModule, module.trim());
        }
        if (StringUtils.hasText(action)) {
            wrapper.eq(AuditLog::getAction, action.trim());
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(AuditLog::getTargetName, kw)
                    .or().like(AuditLog::getDetail, kw)
                    .or().like(AuditLog::getOperatorName, kw));
        }
        LocalDateTime start = parseStartDate(startDate);
        if (start != null) {
            wrapper.ge(AuditLog::getCreateTime, start);
        }
        LocalDateTime endExclusive = parseEndDateExclusive(endDate);
        if (endExclusive != null) {
            wrapper.lt(AuditLog::getCreateTime, endExclusive);
        }
        wrapper.orderByDesc(AuditLog::getId);

        Page<AuditLog> page = auditLogMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        Map<Long, String> tenantNames = loadTenantNames(tenantIdsOf(page.getRecords()));
        List<AuditLogVO> list = new ArrayList<>(page.getRecords().size());
        for (AuditLog log : page.getRecords()) {
            AuditLogVO vo = AuditLogVO.of(log);
            vo.setTenantName(tenantNames.get(log.getTenantId()));
            list.add(vo);
        }
        return PageResult.of(page, list);
    }

    private Set<Long> tenantIdsOf(List<AuditLog> records) {
        Set<Long> ids = new LinkedHashSet<>();
        for (AuditLog log : records) {
            if (log.getTenantId() != null) {
                ids.add(log.getTenantId());
            }
        }
        return ids;
    }

    /**
     * 批量取租户名称（tenant 表为系统级表，不参与租户过滤，任意上下文均可读）。
     */
    private Map<Long, String> loadTenantNames(Iterable<Long> tenantIds) {
        List<Long> ids = new ArrayList<>();
        for (Long id : tenantIds) {
            if (id != null) {
                ids.add(id);
            }
        }
        Map<Long, String> names = new HashMap<>();
        if (ids.isEmpty()) {
            return names;
        }
        for (Tenant tenant : tenantMapper.selectBatchIds(ids)) {
            names.put(tenant.getId(), tenant.getTenantName());
        }
        return names;
    }

    private LocalDateTime parseStartDate(String startDate) {
        LocalDate date = parseDate(startDate, "startDate");
        return date == null ? null : date.atStartOfDay();
    }

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

    /**
     * 对象转审计快照 JSON：敏感字段递归脱敏，超长截断。
     */
    private String toSnapshot(Object value) {
        if (value == null) {
            return null;
        }
        try {
            JsonNode node = objectMapper.valueToTree(value);
            maskSensitive(node);
            String json = objectMapper.writeValueAsString(node);
            if (json.length() > MAX_SNAPSHOT_LENGTH) {
                return json.substring(0, MAX_SNAPSHOT_LENGTH) + "...";
            }
            return json;
        } catch (Exception e) {
            log.warn("审计快照序列化失败，已跳过：{}", e.getMessage());
            return null;
        }
    }

    private void maskSensitive(JsonNode node) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;
            List<String> fields = new ArrayList<>();
            objectNode.fieldNames().forEachRemaining(fields::add);
            for (String field : fields) {
                if (SENSITIVE_FIELDS.contains(field.toLowerCase(Locale.ROOT))) {
                    objectNode.put(field, MASK);
                } else {
                    maskSensitive(objectNode.get(field));
                }
            }
        } else if (node.isArray()) {
            node.forEach(this::maskSensitive);
        }
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() > max ? value.substring(0, max) : value;
    }

    private HttpServletRequest currentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes) {
            return ((ServletRequestAttributes) attributes).getRequest();
        }
        return null;
    }

    private String resolveIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            // 多级代理时取首个客户端地址
            return truncate(forwarded.split(",")[0].trim(), 64);
        }
        String realIp = request.getHeader("X-Real-IP");
        return truncate(StringUtils.hasText(realIp) ? realIp.trim() : request.getRemoteAddr(), 64);
    }

    /**
     * 审计记录构造器：补齐上下文后写入 audit_log。
     */
    public class AuditRecord {

        private final AuditModule module;
        private final AuditAction action;
        private Long tenantId;
        private String targetType;
        private Long targetId;
        private String targetName;
        private String detail;
        private Object before;
        private Object after;

        private AuditRecord(AuditModule module, AuditAction action) {
            this.module = module;
            this.action = action;
        }

        /**
         * 声明被操作对象所属租户，平台侧埋点必填；租户内埋点留空即写当前租户。
         */
        public AuditRecord tenant(Long tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        /**
         * 声明被操作对象。
         */
        public AuditRecord target(String targetType, Long targetId, String targetName) {
            this.targetType = targetType;
            this.targetId = targetId;
            this.targetName = truncate(targetName, MAX_NAME_LENGTH);
            return this;
        }

        /**
         * 变更摘要，建议写成可直接展示的中文句子。
         */
        public AuditRecord detail(String detail) {
            this.detail = truncate(detail, MAX_DETAIL_LENGTH);
            return this;
        }

        /**
         * 变更前快照（新增类操作留空）。
         */
        public AuditRecord before(Object before) {
            this.before = before;
            return this;
        }

        /**
         * 变更后快照（删除类操作留空）。
         */
        public AuditRecord after(Object after) {
            this.after = after;
            return this;
        }

        /**
         * 落库：补齐操作人与请求上下文，异常只告警不抛出。
         */
        public void save() {
            AuditLog entity = build();
            try {
                if (tenantId != null && !tenantId.equals(TenantContext.getTenantId())) {
                    TenantContext.runAs(tenantId, () -> auditLogMapper.insert(entity));
                } else {
                    auditLogMapper.insert(entity);
                }
            } catch (Exception e) {
                AuditLogService.log.warn("审计落库失败，不影响业务：module={}, action={}, target={}, error={}",
                        entity.getModule(), entity.getAction(), entity.getTargetName(), e.getMessage());
            }
        }

        private AuditLog build() {
            LoginUser loginUser = SecurityUtils.getLoginUser();
            HttpServletRequest request = currentRequest();
            AuditLog entity = new AuditLog();
            entity.setModule(module.getCode());
            entity.setAction(action.getCode());
            entity.setTargetType(targetType);
            entity.setTargetId(targetId);
            entity.setTargetName(targetName);
            entity.setDetail(detail);
            entity.setBeforeData(toSnapshot(before));
            entity.setAfterData(toSnapshot(after));
            if (loginUser != null) {
                entity.setOperatorId(loginUser.getUserId());
                entity.setOperatorName(truncate(
                        StringUtils.hasText(loginUser.getNickname()) ? loginUser.getNickname() : loginUser.getUsername(),
                        50));
                entity.setOperatorRole(loginUser.getRoleCode());
            }
            entity.setIp(resolveIp(request));
            if (request != null) {
                entity.setUserAgent(truncate(request.getHeader("User-Agent"), 255));
                entity.setRequestMethod(request.getMethod());
                entity.setRequestUri(truncate(request.getRequestURI(), 255));
            }
            return entity;
        }
    }
}
