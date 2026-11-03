package com.zhiketong.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiketong.common.BusinessException;
import com.zhiketong.common.Constants;
import com.zhiketong.common.ResultCode;
import com.zhiketong.dto.orderform.OrderFieldRequest;
import com.zhiketong.dto.orderform.OrderFieldVO;
import com.zhiketong.dto.orderform.OrderFieldValueRequest;
import com.zhiketong.dto.orderform.OrderFieldValueVO;
import com.zhiketong.dto.orderform.OrderFormVO;
import com.zhiketong.dto.orderform.OrderTemplateRequest;
import com.zhiketong.dto.orderform.OrderTemplateVO;
import com.zhiketong.entity.OrderField;
import com.zhiketong.entity.OrderFieldValue;
import com.zhiketong.entity.OrderTemplate;
import com.zhiketong.enums.OrderFieldType;
import com.zhiketong.mapper.OrderFieldMapper;
import com.zhiketong.mapper.OrderFieldValueMapper;
import com.zhiketong.mapper.OrderTemplateMapper;
import com.zhiketong.security.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 工单表单引擎服务（S3-A：R-115 ~ R-126）。
 *
 * <p>管理员维护自定义字段与工单模板，创建工单时按模板渲染字段并校验字段值。
 * 字段值落库时保存字段名称与类型快照，字段后续改名、停用或删除都不影响历史工单展示。</p>
 *
 * <p>必填校验只在「按模板建单」时生效：模板通过 field_ids 显式绑定字段，
 * 未绑定字段的工单不受必填约束，避免全局必填字段打断既有建单流程。</p>
 *
 * @author 智客通开发组
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderFormService {

    /** 模板最多绑定字段数 */
    private static final int MAX_TEMPLATE_FIELDS = 30;

    /** 下拉字段最多选项数 */
    private static final int MAX_FIELD_OPTIONS = 20;

    /** 单个选项最大长度 */
    private static final int MAX_OPTION_LENGTH = 32;

    /** 选项串总长度上限，与 order_field.options VARCHAR(500) 对齐 */
    private static final int MAX_OPTIONS_LENGTH = 500;

    /** 日期字段格式 */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final OrderFieldMapper orderFieldMapper;
    private final OrderTemplateMapper orderTemplateMapper;
    private final OrderFieldValueMapper orderFieldValueMapper;
    private final SkillGroupService skillGroupService;

    // ------------------------------------------------------------------
    // 自定义字段
    // ------------------------------------------------------------------

    /**
     * 字段列表（R-115）：按排序号升序，支持名称/编码关键词与状态筛选。
     */
    public List<OrderFieldVO> listFields(String keyword, Integer status) {
        LambdaQueryWrapper<OrderField> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            String value = keyword.trim();
            wrapper.and(w -> w.like(OrderField::getFieldName, value).or().like(OrderField::getFieldCode, value));
        }
        if (status != null) {
            wrapper.eq(OrderField::getStatus, normalizeStatus(status));
        }
        wrapper.orderByAsc(OrderField::getSort).orderByAsc(OrderField::getId);
        return toFieldVOs(orderFieldMapper.selectList(wrapper));
    }

    /**
     * 字段详情（R-115 单条查询）。
     */
    public OrderFieldVO fieldDetail(Long id) {
        return toFieldVO(requireField(id));
    }

    /**
     * 新增字段（R-116）：字段编码全局唯一且创建后不可修改。
     */
    @Transactional(rollbackFor = Exception.class)
    public OrderFieldVO createField(OrderFieldRequest request, LoginUser loginUser) {
        if (request == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "字段信息不能为空");
        }
        if (!StringUtils.hasText(request.getFieldCode())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "字段编码不能为空");
        }
        String fieldCode = request.getFieldCode().trim();
        checkFieldCodeUnique(fieldCode, null);
        OrderFieldType type = requireFieldType(request.getFieldType());
        String options = normalizeOptions(request.getOptions(), type);
        checkTypedValue(type, options, request.getDefaultValue(), "默认值");

        OrderField field = new OrderField();
        field.setFieldCode(fieldCode);
        field.setFieldName(requireFieldName(request.getFieldName()));
        field.setFieldType(type.getCode());
        field.setOptions(options);
        field.setRequired(request.getRequired() == null ? 0 : normalizeFlag(request.getRequired(), "必填标记"));
        field.setDefaultValue(trimToNull(request.getDefaultValue()));
        field.setPlaceholder(trimToNull(request.getPlaceholder()));
        field.setDescription(trimToNull(request.getDescription()));
        field.setSort(request.getSort() == null ? 0 : request.getSort());
        field.setStatus(request.getStatus() == null ? Constants.STATUS_ENABLED : normalizeStatus(request.getStatus()));
        field.setCreateBy(userId(loginUser));
        field.setUpdateBy(userId(loginUser));
        field.setDeleted(0);
        orderFieldMapper.insert(field);
        log.info("新增工单字段：id={}, code={}, type={}, operatorId={}",
                field.getId(), fieldCode, type.getCode(), userId(loginUser));
        return toFieldVO(field);
    }

    /**
     * 修改字段（R-117）：字段编码不可变更，类型与选项可调整（历史值按快照展示）。
     */
    @Transactional(rollbackFor = Exception.class)
    public OrderFieldVO updateField(Long id, OrderFieldRequest request, LoginUser loginUser) {
        OrderField field = requireField(id);
        if (request == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "字段信息不能为空");
        }
        if (StringUtils.hasText(request.getFieldCode())
                && !request.getFieldCode().trim().equals(field.getFieldCode())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "字段编码创建后不可修改");
        }
        OrderFieldType type = requireFieldType(request.getFieldType());
        String options = normalizeOptions(request.getOptions(), type);
        checkTypedValue(type, options, request.getDefaultValue(), "默认值");

        field.setFieldName(requireFieldName(request.getFieldName()));
        field.setFieldType(type.getCode());
        field.setOptions(options);
        if (request.getRequired() != null) {
            field.setRequired(normalizeFlag(request.getRequired(), "必填标记"));
        }
        field.setDefaultValue(trimToNull(request.getDefaultValue()));
        field.setPlaceholder(trimToNull(request.getPlaceholder()));
        field.setDescription(trimToNull(request.getDescription()));
        if (request.getSort() != null) {
            field.setSort(request.getSort());
        }
        if (request.getStatus() != null) {
            field.setStatus(normalizeStatus(request.getStatus()));
        }
        field.setUpdateBy(userId(loginUser));
        orderFieldMapper.updateById(field);
        log.info("修改工单字段：id={}, code={}, operatorId={}", field.getId(), field.getFieldCode(), userId(loginUser));
        return toFieldVO(field);
    }

    /**
     * 启用/停用字段（R-118）：停用后不再出现在新建工单表单，历史工单不受影响。
     */
    @Transactional(rollbackFor = Exception.class)
    public OrderFieldVO updateFieldStatus(Long id, Integer status, LoginUser loginUser) {
        OrderField field = requireField(id);
        field.setStatus(normalizeStatus(status));
        field.setUpdateBy(userId(loginUser));
        orderFieldMapper.updateById(field);
        log.info("工单字段状态变更：id={}, status={}, operatorId={}", field.getId(), field.getStatus(), userId(loginUser));
        return toFieldVO(field);
    }

    /**
     * 删除字段（R-119）：仍被模板引用时拒绝删除，避免模板表单出现空洞。
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteField(Long id, LoginUser loginUser) {
        OrderField field = requireField(id);
        checkFieldNotReferenced(field);
        orderFieldMapper.deleteById(field.getId());
        log.info("删除工单字段：id={}, code={}, operatorId={}", field.getId(), field.getFieldCode(), userId(loginUser));
    }

    // ------------------------------------------------------------------
    // 工单模板
    // ------------------------------------------------------------------

    /**
     * 模板列表（R-120）：附带绑定字段明细，供管理端直接编辑。
     */
    public List<OrderTemplateVO> listTemplates(Integer status) {
        LambdaQueryWrapper<OrderTemplate> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(OrderTemplate::getStatus, normalizeStatus(status));
        }
        wrapper.orderByAsc(OrderTemplate::getSort).orderByAsc(OrderTemplate::getId);
        return toTemplateVOs(orderTemplateMapper.selectList(wrapper), true);
    }

    /**
     * 模板详情（R-121）。
     */
    public OrderTemplateVO templateDetail(Long id) {
        return toTemplateVOs(Collections.singletonList(requireTemplate(id)), true).get(0);
    }

    /**
     * 新增模板（R-122）：名称唯一，绑定字段须存在且启用。
     */
    @Transactional(rollbackFor = Exception.class)
    public OrderTemplateVO createTemplate(OrderTemplateRequest request, LoginUser loginUser) {
        if (request == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "模板信息不能为空");
        }
        String name = requireTemplateName(request.getTemplateName());
        checkTemplateNameUnique(name, null);
        String category = requireCategory(request.getCategory());
        validateSkillGroup(request.getSkillGroupId());
        String fieldIds = buildFieldIds(request.getFieldIds());

        OrderTemplate template = new OrderTemplate();
        template.setTemplateName(name);
        template.setCategory(category);
        template.setDefaultPriority(trimToNull(request.getDefaultPriority()));
        template.setSkillGroupId(request.getSkillGroupId());
        template.setFieldIds(fieldIds);
        template.setDescription(trimToNull(request.getDescription()));
        template.setSort(request.getSort() == null ? 0 : request.getSort());
        template.setStatus(request.getStatus() == null ? Constants.STATUS_ENABLED : normalizeStatus(request.getStatus()));
        template.setCreateBy(userId(loginUser));
        template.setUpdateBy(userId(loginUser));
        template.setDeleted(0);
        orderTemplateMapper.insert(template);
        log.info("新增工单模板：id={}, name={}, fields={}, operatorId={}",
                template.getId(), name, parseFieldIds(fieldIds).size(), userId(loginUser));
        return templateDetail(template.getId());
    }

    /**
     * 修改模板（R-123）：绑定字段以本次提交为准全量覆盖。
     */
    @Transactional(rollbackFor = Exception.class)
    public OrderTemplateVO updateTemplate(Long id, OrderTemplateRequest request, LoginUser loginUser) {
        OrderTemplate template = requireTemplate(id);
        if (request == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "模板信息不能为空");
        }
        String name = requireTemplateName(request.getTemplateName());
        checkTemplateNameUnique(name, template.getId());
        String category = requireCategory(request.getCategory());
        validateSkillGroup(request.getSkillGroupId());
        String fieldIds = buildFieldIds(request.getFieldIds());

        template.setTemplateName(name);
        template.setCategory(category);
        template.setDefaultPriority(trimToNull(request.getDefaultPriority()));
        template.setSkillGroupId(request.getSkillGroupId());
        template.setFieldIds(fieldIds);
        template.setDescription(trimToNull(request.getDescription()));
        if (request.getSort() != null) {
            template.setSort(request.getSort());
        }
        if (request.getStatus() != null) {
            template.setStatus(normalizeStatus(request.getStatus()));
        }
        template.setUpdateBy(userId(loginUser));
        orderTemplateMapper.updateById(template);
        log.info("修改工单模板：id={}, name={}, operatorId={}", template.getId(), name, userId(loginUser));
        return templateDetail(template.getId());
    }

    /**
     * 启用/停用模板（R-124）：停用后不再出现在新建工单模板选择中。
     */
    @Transactional(rollbackFor = Exception.class)
    public OrderTemplateVO updateTemplateStatus(Long id, Integer status, LoginUser loginUser) {
        OrderTemplate template = requireTemplate(id);
        template.setStatus(normalizeStatus(status));
        template.setUpdateBy(userId(loginUser));
        orderTemplateMapper.updateById(template);
        log.info("工单模板状态变更：id={}, status={}, operatorId={}", template.getId(), template.getStatus(), userId(loginUser));
        return templateDetail(template.getId());
    }

    /**
     * 删除模板（R-125）：历史工单已保存字段值快照，删除模板不影响历史数据。
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteTemplate(Long id, LoginUser loginUser) {
        OrderTemplate template = requireTemplate(id);
        orderTemplateMapper.deleteById(template.getId());
        log.info("删除工单模板：id={}, name={}, operatorId={}", template.getId(), template.getTemplateName(), userId(loginUser));
    }

    // ------------------------------------------------------------------
    // 建单表单与字段值
    // ------------------------------------------------------------------

    /**
     * 新建工单表单（R-126）：返回启用字段、启用模板、问题分类与字段类型字典。
     */
    public OrderFormVO form() {
        OrderFormVO vo = new OrderFormVO();
        vo.setCategories(new ArrayList<>(Constants.ORDER_CATEGORIES));
        for (OrderFieldType type : OrderFieldType.values()) {
            vo.getFieldTypes().add(new OrderFormVO.FieldTypeOption(type.getCode(), type.getDescription()));
        }
        List<OrderField> enabledFields = orderFieldMapper.selectList(new LambdaQueryWrapper<OrderField>()
                .eq(OrderField::getStatus, Constants.STATUS_ENABLED)
                .orderByAsc(OrderField::getSort)
                .orderByAsc(OrderField::getId));
        List<OrderFieldVO> fieldVOs = toFieldVOs(enabledFields);
        vo.setFields(fieldVOs);

        Map<Long, OrderFieldVO> fieldMap = new HashMap<>(fieldVOs.size());
        for (OrderFieldVO fieldVO : fieldVOs) {
            fieldMap.put(fieldVO.getId(), fieldVO);
        }
        List<OrderTemplate> templates = orderTemplateMapper.selectList(new LambdaQueryWrapper<OrderTemplate>()
                .eq(OrderTemplate::getStatus, Constants.STATUS_ENABLED)
                .orderByAsc(OrderTemplate::getSort)
                .orderByAsc(OrderTemplate::getId));
        Map<Long, String> groupNames = skillGroupService.nameMap(skillGroupIds(templates));
        List<OrderTemplateVO> templateVOs = new ArrayList<>(templates.size());
        for (OrderTemplate template : templates) {
            OrderTemplateVO templateVO = toTemplateVO(template, fieldMap, groupNames, false);
            // 表单只渲染启用字段：字段停用后对应位置直接隐藏
            List<OrderFieldVO> usable = new ArrayList<>();
            for (Long fieldId : parseFieldIds(template.getFieldIds())) {
                OrderFieldVO field = fieldMap.get(fieldId);
                if (field != null) {
                    usable.add(field);
                }
            }
            templateVO.setFields(usable);
            templateVOs.add(templateVO);
        }
        vo.setTemplates(templateVOs);
        return vo;
    }

    /**
     * 取启用状态的模板，供创建工单校验；不存在或已停用直接拦截。
     */
    public OrderTemplate requireActiveTemplate(Long templateId) {
        OrderTemplate template = templateId == null ? null : orderTemplateMapper.selectById(templateId);
        if (template == null || !Integer.valueOf(Constants.STATUS_ENABLED).equals(template.getStatus())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "工单模板不存在或已停用");
        }
        return template;
    }

    /**
     * 批量取模板名称，供工单列表展示来源模板。
     */
    public Map<Long, String> templateNames(Collection<Long> templateIds) {
        if (templateIds == null || templateIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<OrderTemplate> templates = orderTemplateMapper.selectBatchIds(templateIds);
        Map<Long, String> map = new HashMap<>(templates.size());
        for (OrderTemplate template : templates) {
            map.put(template.getId(), template.getTemplateName());
        }
        return map;
    }

    /**
     * 工单自定义字段值（工单详情展示），按写入顺序返回。
     */
    public List<OrderFieldValueVO> values(Long orderId) {
        if (orderId == null) {
            return new ArrayList<>();
        }
        List<OrderFieldValue> entities = orderFieldValueMapper.selectList(new LambdaQueryWrapper<OrderFieldValue>()
                .eq(OrderFieldValue::getOrderId, orderId)
                .orderByAsc(OrderFieldValue::getSort)
                .orderByAsc(OrderFieldValue::getId));
        List<OrderFieldValueVO> list = new ArrayList<>(entities.size());
        for (OrderFieldValue entity : entities) {
            OrderFieldValueVO vo = new OrderFieldValueVO();
            vo.setFieldId(entity.getFieldId());
            vo.setFieldCode(entity.getFieldCode());
            vo.setFieldName(entity.getFieldName());
            vo.setFieldType(entity.getFieldType());
            vo.setValue(entity.getFieldValue());
            vo.setSort(entity.getSort());
            list.add(vo);
        }
        return list;
    }

    /**
     * 保存工单自定义字段值（创建工单时调用）。
     *
     * <p>校验顺序：模板绑定关系 → 字段重复提交 → 类型格式 → 必填。
     * 未选模板却提交字段值视为参数错误，避免产生无法追溯的散装字段。</p>
     *
     * @param orderId    工单 ID
     * @param templateId 模板 ID，为空表示普通建单
     * @param values     提交的字段值
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveValues(Long orderId, Long templateId, List<OrderFieldValueRequest> values) {
        boolean hasValues = values != null && !values.isEmpty();
        if (templateId == null) {
            if (hasValues) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "填写自定义字段前请先选择工单模板");
            }
            return;
        }
        OrderTemplate template = requireActiveTemplate(templateId);
        List<Long> boundIds = parseFieldIds(template.getFieldIds());
        Map<Long, OrderField> fieldMap = fieldMapOf(boundIds);
        Map<Long, String> submitted = collectSubmitted(boundIds, values);

        List<OrderFieldValue> entities = new ArrayList<>(boundIds.size());
        int sort = 0;
        for (Long fieldId : boundIds) {
            OrderField field = fieldMap.get(fieldId);
            if (field == null) {
                // 字段已被删除：模板中的失效引用直接跳过，历史模板不影响建单
                continue;
            }
            OrderFieldType type = OrderFieldType.fromCode(field.getFieldType());
            if (type == null) {
                continue;
            }
            String raw = submitted.get(fieldId);
            String value;
            if (!submitted.containsKey(fieldId) && StringUtils.hasText(field.getDefaultValue())) {
                value = normalizeValue(type, field.getOptions(), field.getDefaultValue(), field.getFieldName());
            } else {
                value = normalizeValue(type, field.getOptions(), raw, field.getFieldName());
            }
            boolean required = Integer.valueOf(1).equals(field.getRequired())
                    && Integer.valueOf(Constants.STATUS_ENABLED).equals(field.getStatus());
            if (required && !StringUtils.hasText(value)) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "「" + field.getFieldName() + "」为必填项");
            }
            if (!StringUtils.hasText(value)) {
                continue;
            }
            OrderFieldValue entity = new OrderFieldValue();
            entity.setOrderId(orderId);
            entity.setFieldId(field.getId());
            entity.setFieldCode(field.getFieldCode());
            entity.setFieldName(field.getFieldName());
            entity.setFieldType(field.getFieldType());
            entity.setFieldValue(value);
            entity.setSort(sort++);
            entity.setDeleted(0);
            entities.add(entity);
        }
        for (OrderFieldValue entity : entities) {
            orderFieldValueMapper.insert(entity);
        }
        log.info("工单字段值落库：orderId={}, templateId={}, filled={}", orderId, templateId, entities.size());
    }

    // ------------------------------------------------------------------
    // 内部方法
    // ------------------------------------------------------------------

    private Map<Long, String> collectSubmitted(List<Long> boundIds, List<OrderFieldValueRequest> values) {
        Map<Long, String> submitted = new LinkedHashMap<>();
        if (values == null || values.isEmpty()) {
            return submitted;
        }
        Set<Long> submittedIds = new LinkedHashSet<>();
        for (OrderFieldValueRequest item : values) {
            if (item == null || item.getFieldId() == null) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "字段 ID 不能为空");
            }
            if (!boundIds.contains(item.getFieldId())) {
                throw new BusinessException(ResultCode.PARAM_ERROR,
                        "字段不属于所选工单模板：fieldId=" + item.getFieldId());
            }
            if (!submittedIds.add(item.getFieldId())) {
                throw new BusinessException(ResultCode.PARAM_ERROR,
                        "字段重复提交：fieldId=" + item.getFieldId());
            }
            submitted.put(item.getFieldId(), item.getValue());
        }
        return submitted;
    }

    private void checkFieldNotReferenced(OrderField field) {
        List<OrderTemplate> templates = orderTemplateMapper.selectList(new LambdaQueryWrapper<OrderTemplate>()
                .select(OrderTemplate::getId, OrderTemplate::getTemplateName, OrderTemplate::getFieldIds));
        for (OrderTemplate template : templates) {
            if (parseFieldIds(template.getFieldIds()).contains(field.getId())) {
                throw new BusinessException(ResultCode.CONFLICT,
                        "字段已被工单模板「" + template.getTemplateName() + "」引用，请先在模板中移除");
            }
        }
    }

    private void checkFieldCodeUnique(String fieldCode, Long excludeId) {
        LambdaQueryWrapper<OrderField> wrapper = new LambdaQueryWrapper<OrderField>()
                .eq(OrderField::getFieldCode, fieldCode);
        if (excludeId != null) {
            wrapper.ne(OrderField::getId, excludeId);
        }
        Long count = orderFieldMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "字段编码已存在：" + fieldCode);
        }
    }

    private void checkTemplateNameUnique(String name, Long excludeId) {
        LambdaQueryWrapper<OrderTemplate> wrapper = new LambdaQueryWrapper<OrderTemplate>()
                .eq(OrderTemplate::getTemplateName, name);
        if (excludeId != null) {
            wrapper.ne(OrderTemplate::getId, excludeId);
        }
        Long count = orderTemplateMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "模板名称已存在：" + name);
        }
    }

    /**
     * 模板绑定字段校验：去重保序，字段必须存在且启用。
     */
    private String buildFieldIds(List<Long> fieldIds) {
        if (fieldIds == null || fieldIds.isEmpty()) {
            return null;
        }
        Set<Long> distinct = new LinkedHashSet<>();
        for (Long fieldId : fieldIds) {
            if (fieldId != null) {
                distinct.add(fieldId);
            }
        }
        if (distinct.size() > MAX_TEMPLATE_FIELDS) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "单个模板最多绑定 " + MAX_TEMPLATE_FIELDS + " 个字段");
        }
        Map<Long, OrderField> fields = fieldMapOf(distinct);
        List<String> parts = new ArrayList<>(distinct.size());
        for (Long fieldId : distinct) {
            OrderField field = fields.get(fieldId);
            if (field == null) {
                throw new BusinessException(ResultCode.NOT_FOUND, "字段不存在：fieldId=" + fieldId);
            }
            if (!Integer.valueOf(Constants.STATUS_ENABLED).equals(field.getStatus())) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "字段已停用，不能绑定到模板：" + field.getFieldName());
            }
            parts.add(String.valueOf(fieldId));
        }
        return String.join(",", parts);
    }

    private String normalizeOptions(List<String> options, OrderFieldType type) {
        if (type != OrderFieldType.SELECT) {
            // 非下拉字段忽略选项，避免历史值残留干扰
            return null;
        }
        if (options == null || options.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "下拉字段必须至少配置一个选项");
        }
        Set<String> distinct = new LinkedHashSet<>();
        for (String option : options) {
            if (!StringUtils.hasText(option)) {
                continue;
            }
            String trimmed = option.trim();
            if (trimmed.length() > MAX_OPTION_LENGTH) {
                throw new BusinessException(ResultCode.PARAM_ERROR,
                        "单个选项不能超过 " + MAX_OPTION_LENGTH + " 个字符：" + trimmed);
            }
            distinct.add(trimmed);
        }
        if (distinct.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "下拉字段必须至少配置一个有效选项");
        }
        if (distinct.size() > MAX_FIELD_OPTIONS) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "下拉字段最多 " + MAX_FIELD_OPTIONS + " 个选项");
        }
        String joined = String.join(",", distinct);
        if (joined.length() > MAX_OPTIONS_LENGTH) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "下拉选项总长度不能超过 " + MAX_OPTIONS_LENGTH + " 个字符，请精简选项文案");
        }
        return joined;
    }

    private void checkTypedValue(OrderFieldType type, String options, String value, String label) {
        normalizeValue(type, options, value, label);
    }

    /**
     * 校验并规整字段值：数字、日期、下拉选项按类型校验，文本仅做长度限制。
     *
     * @return 规整后的值，空值返回 null
     */
    private String normalizeValue(OrderFieldType type, String options, String raw, String fieldName) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String value = raw.trim();
        if (value.length() > 2000) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "「" + fieldName + "」不能超过 2000 个字符");
        }
        switch (type) {
            case NUMBER:
                try {
                    new BigDecimal(value);
                } catch (NumberFormatException e) {
                    throw new BusinessException(ResultCode.PARAM_ERROR, "「" + fieldName + "」必须为数字");
                }
                break;
            case DATE:
                try {
                    LocalDate.parse(value, DATE_FORMATTER);
                } catch (DateTimeParseException e) {
                    throw new BusinessException(ResultCode.PARAM_ERROR, "「" + fieldName + "」日期格式须为 yyyy-MM-dd");
                }
                break;
            case SELECT:
                if (!parseOptions(options).contains(value)) {
                    throw new BusinessException(ResultCode.PARAM_ERROR, "「" + fieldName + "」只能选择预设选项");
                }
                break;
            default:
                break;
        }
        return value;
    }

    private void validateSkillGroup(Long skillGroupId) {
        if (skillGroupId != null && skillGroupService.activeGroup(skillGroupId) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "技能组不存在或已停用");
        }
    }

    private List<OrderFieldVO> toFieldVOs(List<OrderField> fields) {
        if (fields == null || fields.isEmpty()) {
            return new ArrayList<>();
        }
        List<OrderFieldVO> list = new ArrayList<>(fields.size());
        for (OrderField field : fields) {
            list.add(toFieldVO(field));
        }
        return list;
    }

    private OrderFieldVO toFieldVO(OrderField field) {
        OrderFieldVO vo = new OrderFieldVO();
        vo.setId(field.getId());
        vo.setFieldCode(field.getFieldCode());
        vo.setFieldName(field.getFieldName());
        vo.setFieldType(field.getFieldType());
        OrderFieldType type = OrderFieldType.fromCode(field.getFieldType());
        vo.setFieldTypeLabel(type == null ? field.getFieldType() : type.getDescription());
        vo.setOptions(parseOptions(field.getOptions()));
        vo.setRequired(field.getRequired() == null ? 0 : field.getRequired());
        vo.setDefaultValue(field.getDefaultValue());
        vo.setPlaceholder(field.getPlaceholder());
        vo.setDescription(field.getDescription());
        vo.setSort(field.getSort() == null ? 0 : field.getSort());
        vo.setStatus(field.getStatus());
        vo.setCreateTime(field.getCreateTime());
        vo.setUpdateTime(field.getUpdateTime());
        return vo;
    }

    private List<OrderTemplateVO> toTemplateVOs(List<OrderTemplate> templates, boolean withFields) {
        if (templates == null || templates.isEmpty()) {
            return new ArrayList<>();
        }
        Set<Long> fieldIds = new LinkedHashSet<>();
        for (OrderTemplate template : templates) {
            fieldIds.addAll(parseFieldIds(template.getFieldIds()));
        }
        Map<Long, OrderField> fields = fieldMapOf(fieldIds);
        Map<Long, String> groupNames = skillGroupService.nameMap(skillGroupIds(templates));
        Map<Long, OrderFieldVO> fieldMap = new HashMap<>(fields.size());
        for (Map.Entry<Long, OrderField> entry : fields.entrySet()) {
            fieldMap.put(entry.getKey(), toFieldVO(entry.getValue()));
        }
        List<OrderTemplateVO> list = new ArrayList<>(templates.size());
        for (OrderTemplate template : templates) {
            list.add(toTemplateVO(template, fieldMap, groupNames, withFields));
        }
        return list;
    }

    private OrderTemplateVO toTemplateVO(OrderTemplate template, Map<Long, OrderFieldVO> fieldMap,
                                         Map<Long, String> groupNames, boolean withFields) {
        List<Long> ids = parseFieldIds(template.getFieldIds());
        OrderTemplateVO vo = new OrderTemplateVO();
        vo.setId(template.getId());
        vo.setTemplateName(template.getTemplateName());
        vo.setCategory(template.getCategory());
        vo.setDefaultPriority(template.getDefaultPriority());
        vo.setSkillGroupId(template.getSkillGroupId());
        vo.setSkillGroupName(groupNames.get(template.getSkillGroupId()));
        vo.setFieldIds(ids);
        vo.setDescription(template.getDescription());
        vo.setSort(template.getSort() == null ? 0 : template.getSort());
        vo.setStatus(template.getStatus());
        vo.setCreateTime(template.getCreateTime());
        vo.setUpdateTime(template.getUpdateTime());
        if (withFields) {
            List<OrderFieldVO> fields = new ArrayList<>(ids.size());
            for (Long id : ids) {
                OrderFieldVO field = fieldMap.get(id);
                if (field != null) {
                    fields.add(field);
                }
            }
            vo.setFields(fields);
        }
        return vo;
    }

    private Map<Long, OrderField> fieldMapOf(Collection<Long> fieldIds) {
        if (fieldIds == null || fieldIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<OrderField> fields = orderFieldMapper.selectBatchIds(fieldIds);
        Map<Long, OrderField> map = new HashMap<>(fields.size());
        for (OrderField field : fields) {
            map.put(field.getId(), field);
        }
        return map;
    }

    private Set<Long> skillGroupIds(List<OrderTemplate> templates) {
        Set<Long> ids = new LinkedHashSet<>();
        for (OrderTemplate template : templates) {
            if (template.getSkillGroupId() != null) {
                ids.add(template.getSkillGroupId());
            }
        }
        return ids;
    }

    /**
     * 拆分逗号串字段 ID，非法片段直接忽略。
     */
    private List<Long> parseFieldIds(String fieldIds) {
        if (!StringUtils.hasText(fieldIds)) {
            return new ArrayList<>();
        }
        List<Long> ids = new ArrayList<>();
        for (String part : fieldIds.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                ids.add(Long.parseLong(trimmed));
            } catch (NumberFormatException e) {
                log.warn("忽略非法模板字段 ID：{}", trimmed);
            }
        }
        return ids;
    }

    private List<String> parseOptions(String options) {
        if (!StringUtils.hasText(options)) {
            return new ArrayList<>();
        }
        List<String> list = new ArrayList<>();
        for (String part : options.split(",")) {
            if (StringUtils.hasText(part)) {
                list.add(part.trim());
            }
        }
        return list;
    }

    private OrderField requireField(Long id) {
        OrderField field = id == null ? null : orderFieldMapper.selectById(id);
        if (field == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "工单字段不存在");
        }
        return field;
    }

    private OrderTemplate requireTemplate(Long id) {
        OrderTemplate template = id == null ? null : orderTemplateMapper.selectById(id);
        if (template == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "工单模板不存在");
        }
        return template;
    }

    private OrderFieldType requireFieldType(String fieldType) {
        OrderFieldType type = OrderFieldType.fromCode(fieldType);
        if (type == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "字段类型只能为 " + OrderFieldType.valuesDescription());
        }
        return type;
    }

    private String requireFieldName(String fieldName) {
        if (!StringUtils.hasText(fieldName)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "字段名称不能为空");
        }
        String trimmed = fieldName.trim();
        if (trimmed.length() < 2 || trimmed.length() > 64) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "字段名称长度须为 2-64 个字符");
        }
        return trimmed;
    }

    private String requireTemplateName(String templateName) {
        if (!StringUtils.hasText(templateName)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "模板名称不能为空");
        }
        String trimmed = templateName.trim();
        if (trimmed.length() < 2 || trimmed.length() > 64) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "模板名称长度须为 2-64 个字符");
        }
        return trimmed;
    }

    /**
     * 模板默认分类：留空表示建单时由用户选择，非空必须是通用分类之一。
     */
    private String requireCategory(String category) {
        if (!StringUtils.hasText(category)) {
            return null;
        }
        String trimmed = category.trim();
        if (!Constants.ORDER_CATEGORIES.contains(trimmed)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "问题分类只能为"
                    + String.join("/", Constants.ORDER_CATEGORIES));
        }
        return trimmed;
    }

    private int normalizeStatus(Integer status) {
        if (status == null) {
            return Constants.STATUS_ENABLED;
        }
        if (status != Constants.STATUS_ENABLED && status != Constants.STATUS_DISABLED) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "状态只能为 0 或 1");
        }
        return status;
    }

    private int normalizeFlag(Integer value, String label) {
        if (value == null) {
            return 0;
        }
        if (value != 0 && value != 1) {
            throw new BusinessException(ResultCode.PARAM_ERROR, label + "只能为 0 或 1");
        }
        return value;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private Long userId(LoginUser loginUser) {
        return loginUser == null ? null : loginUser.getUserId();
    }
}
