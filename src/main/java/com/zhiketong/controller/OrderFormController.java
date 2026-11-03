package com.zhiketong.controller;

import com.zhiketong.common.Result;
import com.zhiketong.dto.orderform.OrderFieldRequest;
import com.zhiketong.dto.orderform.OrderFieldVO;
import com.zhiketong.dto.orderform.OrderFormStatusRequest;
import com.zhiketong.dto.orderform.OrderTemplateRequest;
import com.zhiketong.dto.orderform.OrderTemplateVO;
import com.zhiketong.security.SecurityUtils;
import com.zhiketong.service.OrderFormService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

/**
 * 工单表单引擎管理接口（R-115 ~ R-125）：自定义字段与工单模板，仅管理员可访问。
 *
 * <p>控制层只做参数接收与登录人透传，唯一性、引用关系与字段值校验均由
 * {@link OrderFormService} 负责。</p>
 *
 * @author 智客通开发组
 */
@Tag(name = "09. 工单 - 表单配置")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class OrderFormController {

    private final OrderFormService orderFormService;

    @Operation(summary = "工单字段列表（R-115）")
    @GetMapping("/order-fields")
    public Result<List<OrderFieldVO>> listFields(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        return Result.success(orderFormService.listFields(keyword, status));
    }

    @Operation(summary = "工单字段详情（R-115）")
    @GetMapping("/order-fields/{id}")
    public Result<OrderFieldVO> fieldDetail(@PathVariable Long id) {
        return Result.success(orderFormService.fieldDetail(id));
    }

    @Operation(summary = "新增工单字段（R-116）")
    @PostMapping("/order-fields")
    public Result<OrderFieldVO> createField(@Valid @RequestBody OrderFieldRequest request) {
        return Result.success(orderFormService.createField(request, SecurityUtils.requireLogin()));
    }

    @Operation(summary = "修改工单字段（R-117）")
    @PutMapping("/order-fields/{id}")
    public Result<OrderFieldVO> updateField(@PathVariable Long id,
                                            @Valid @RequestBody OrderFieldRequest request) {
        return Result.success(orderFormService.updateField(id, request, SecurityUtils.requireLogin()));
    }

    @Operation(summary = "启用/停用工单字段（R-118）")
    @PutMapping("/order-fields/{id}/status")
    public Result<OrderFieldVO> updateFieldStatus(@PathVariable Long id,
                                                  @Valid @RequestBody OrderFormStatusRequest request) {
        return Result.success(orderFormService.updateFieldStatus(id, request.getStatus(), SecurityUtils.requireLogin()));
    }

    @Operation(summary = "删除工单字段（R-119）")
    @DeleteMapping("/order-fields/{id}")
    public Result<Void> deleteField(@PathVariable Long id) {
        orderFormService.deleteField(id, SecurityUtils.requireLogin());
        return Result.success();
    }

    @Operation(summary = "工单模板列表（R-120）")
    @GetMapping("/order-templates")
    public Result<List<OrderTemplateVO>> listTemplates(
            @RequestParam(required = false) Integer status) {
        return Result.success(orderFormService.listTemplates(status));
    }

    @Operation(summary = "工单模板详情（R-121）")
    @GetMapping("/order-templates/{id}")
    public Result<OrderTemplateVO> templateDetail(@PathVariable Long id) {
        return Result.success(orderFormService.templateDetail(id));
    }

    @Operation(summary = "新增工单模板（R-122）")
    @PostMapping("/order-templates")
    public Result<OrderTemplateVO> createTemplate(@Valid @RequestBody OrderTemplateRequest request) {
        return Result.success(orderFormService.createTemplate(request, SecurityUtils.requireLogin()));
    }

    @Operation(summary = "修改工单模板（R-123）")
    @PutMapping("/order-templates/{id}")
    public Result<OrderTemplateVO> updateTemplate(@PathVariable Long id,
                                                  @Valid @RequestBody OrderTemplateRequest request) {
        return Result.success(orderFormService.updateTemplate(id, request, SecurityUtils.requireLogin()));
    }

    @Operation(summary = "启用/停用工单模板（R-124）")
    @PutMapping("/order-templates/{id}/status")
    public Result<OrderTemplateVO> updateTemplateStatus(@PathVariable Long id,
                                                        @Valid @RequestBody OrderFormStatusRequest request) {
        return Result.success(orderFormService.updateTemplateStatus(id, request.getStatus(),
                SecurityUtils.requireLogin()));
    }

    @Operation(summary = "删除工单模板（R-125）")
    @DeleteMapping("/order-templates/{id}")
    public Result<Void> deleteTemplate(@PathVariable Long id) {
        orderFormService.deleteTemplate(id, SecurityUtils.requireLogin());
        return Result.success();
    }
}
