package com.zhiketong.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhiketong.common.BusinessException;
import com.zhiketong.common.Constants;
import com.zhiketong.common.ResultCode;
import com.zhiketong.dto.quickreply.QuickReplySaveRequest;
import com.zhiketong.dto.quickreply.QuickReplyUseVO;
import com.zhiketong.dto.quickreply.QuickReplyVO;
import com.zhiketong.entity.QuickReply;
import com.zhiketong.mapper.QuickReplyMapper;
import com.zhiketong.security.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 快捷回复话术服务（R-89 ~ R-93）。
 *
 * <p>话术分两种范围：{@code PUBLIC} 公共话术由管理员维护、全体坐席可见可引用；
 * {@code PERSONAL} 个人话术仅创建者本人可见可维护。坐席新建话术时服务端强制归为个人话术，
 * 避免越权产生公共话术。</p>
 *
 * @author 智客通开发组
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuickReplyService {

    /** 公共话术范围 */
    public static final String SCOPE_PUBLIC = "PUBLIC";

    /** 个人话术范围 */
    public static final String SCOPE_PERSONAL = "PERSONAL";

    /** 缺省分类 */
    private static final String DEFAULT_CATEGORY = "通用";

    /** 单次查询返回上限，话术为轻量配置数据，一次返回即可满足坐席端面板 */
    private static final int MAX_LIST_SIZE = 200;

    private final QuickReplyMapper quickReplyMapper;

    /**
     * 查询可见话术列表：管理员可见全部，坐席可见公共话术与本人个人话术。
     */
    public List<QuickReplyVO> list(String scope, String category, String keyword,
                                   Integer status, LoginUser loginUser) {
        LoginUser user = requireLogin(loginUser);
        String scopeFilter = normalizeScope(scope);
        Integer statusFilter = normalizeStatus(status);

        LambdaQueryWrapper<QuickReply> wrapper = new LambdaQueryWrapper<>();
        if (!Constants.ROLE_ADMIN.equals(user.getRoleCode())) {
            Long userId = user.getUserId();
            wrapper.and(w -> w.eq(QuickReply::getScope, SCOPE_PUBLIC)
                    .or(n -> n.eq(QuickReply::getScope, SCOPE_PERSONAL)
                            .eq(QuickReply::getOwnerId, userId)));
        }
        if (scopeFilter != null) {
            wrapper.eq(QuickReply::getScope, scopeFilter);
        }
        if (StringUtils.hasText(category)) {
            wrapper.eq(QuickReply::getCategory, category.trim());
        }
        if (statusFilter != null) {
            wrapper.eq(QuickReply::getStatus, statusFilter);
        }
        if (StringUtils.hasText(keyword)) {
            String trimmed = keyword.trim();
            wrapper.and(w -> w.like(QuickReply::getTitle, trimmed)
                    .or().like(QuickReply::getContent, trimmed));
        }
        wrapper.orderByAsc(QuickReply::getSort)
                .orderByDesc(QuickReply::getUseCount)
                .orderByAsc(QuickReply::getId)
                .last("LIMIT " + MAX_LIST_SIZE);

        List<QuickReply> replies = quickReplyMapper.selectList(wrapper);
        List<QuickReplyVO> list = new ArrayList<>(replies.size());
        for (QuickReply reply : replies) {
            list.add(toVO(reply, user));
        }
        return list;
    }

    /**
     * 分类候选值，供坐席端与管理端筛选，按出现顺序去重。
     */
    public List<String> categories(LoginUser loginUser) {
        LoginUser user = requireLogin(loginUser);
        LambdaQueryWrapper<QuickReply> wrapper = new LambdaQueryWrapper<QuickReply>()
                .select(QuickReply::getCategory)
                .eq(QuickReply::getStatus, Constants.STATUS_ENABLED)
                .groupBy(QuickReply::getCategory)
                .orderByAsc(QuickReply::getCategory)
                .last("LIMIT " + MAX_LIST_SIZE);
        if (!Constants.ROLE_ADMIN.equals(user.getRoleCode())) {
            Long userId = user.getUserId();
            wrapper.and(w -> w.eq(QuickReply::getScope, SCOPE_PUBLIC)
                    .or(n -> n.eq(QuickReply::getScope, SCOPE_PERSONAL)
                            .eq(QuickReply::getOwnerId, userId)));
        }
        List<QuickReply> replies = quickReplyMapper.selectList(wrapper);
        Set<String> categories = new LinkedHashSet<>();
        for (QuickReply reply : replies) {
            if (StringUtils.hasText(reply.getCategory())) {
                categories.add(reply.getCategory());
            }
        }
        return new ArrayList<>(categories);
    }

    /**
     * 新增话术：管理员可按 scope 创建公共话术，坐席一律创建个人话术。
     */
    @Transactional(rollbackFor = Exception.class)
    public QuickReplyVO create(QuickReplySaveRequest request, LoginUser loginUser) {
        LoginUser user = requireLogin(loginUser);
        if (request == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "话术内容不能为空");
        }
        boolean admin = Constants.ROLE_ADMIN.equals(user.getRoleCode());
        String requestedScope = normalizeScope(request.getScope());
        String scope = admin && SCOPE_PERSONAL.equals(requestedScope) ? SCOPE_PERSONAL : (admin ? SCOPE_PUBLIC : SCOPE_PERSONAL);
        Long ownerId = SCOPE_PERSONAL.equals(scope) ? user.getUserId() : null;

        String title = requireText(request.getTitle(), "话术标题不能为空");
        String content = requireText(request.getContent(), "话术内容不能为空");
        String category = StringUtils.hasText(request.getCategory()) ? request.getCategory().trim() : DEFAULT_CATEGORY;
        checkDuplicate(scope, ownerId, title, null);

        QuickReply reply = new QuickReply();
        reply.setScope(scope);
        reply.setOwnerId(ownerId);
        reply.setCategory(category);
        reply.setTitle(title);
        reply.setContent(content);
        reply.setSort(request.getSort() == null ? 0 : request.getSort());
        reply.setUseCount(0);
        reply.setStatus(request.getStatus() == null ? Constants.STATUS_ENABLED : normalizeStatus(request.getStatus()));
        reply.setCreateBy(user.getUserId());
        reply.setUpdateBy(user.getUserId());
        reply.setDeleted(0);
        quickReplyMapper.insert(reply);
        log.info("新增快捷回复：id={}, scope={}, operatorId={}", reply.getId(), scope, user.getUserId());
        return toVO(reply, user);
    }

    /**
     * 编辑话术：个人话术仅本人可改，公共话术仅管理员可改；范围不可变更。
     */
    @Transactional(rollbackFor = Exception.class)
    public QuickReplyVO update(Long id, QuickReplySaveRequest request, LoginUser loginUser) {
        LoginUser user = requireLogin(loginUser);
        QuickReply reply = requireReply(id);
        checkEditable(reply, user);
        if (request == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "话术内容不能为空");
        }
        String title = requireText(request.getTitle(), "话术标题不能为空");
        String content = requireText(request.getContent(), "话术内容不能为空");
        checkDuplicate(reply.getScope(), reply.getOwnerId(), title, reply.getId());

        reply.setTitle(title);
        reply.setContent(content);
        reply.setCategory(StringUtils.hasText(request.getCategory()) ? request.getCategory().trim() : DEFAULT_CATEGORY);
        if (request.getSort() != null) {
            reply.setSort(request.getSort());
        }
        if (request.getStatus() != null) {
            reply.setStatus(normalizeStatus(request.getStatus()));
        }
        reply.setUpdateBy(user.getUserId());
        quickReplyMapper.updateById(reply);
        return toVO(reply, user);
    }

    /**
     * 启用/停用话术。
     */
    @Transactional(rollbackFor = Exception.class)
    public QuickReplyVO updateStatus(Long id, Integer status, LoginUser loginUser) {
        LoginUser user = requireLogin(loginUser);
        QuickReply reply = requireReply(id);
        checkEditable(reply, user);
        reply.setStatus(normalizeStatus(status));
        reply.setUpdateBy(user.getUserId());
        quickReplyMapper.updateById(reply);
        return toVO(reply, user);
    }

    /**
     * 删除话术（逻辑删除）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, LoginUser loginUser) {
        LoginUser user = requireLogin(loginUser);
        QuickReply reply = requireReply(id);
        checkEditable(reply, user);
        quickReplyMapper.deleteById(reply.getId());
        log.info("删除快捷回复：id={}, operatorId={}", reply.getId(), user.getUserId());
    }

    /**
     * 引用话术：累加使用次数并返回话术正文，供坐席端填充回复框。
     */
    @Transactional(rollbackFor = Exception.class)
    public QuickReplyUseVO use(Long id, LoginUser loginUser) {
        LoginUser user = requireLogin(loginUser);
        QuickReply reply = requireReply(id);
        if (!visible(reply, user)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权使用该话术");
        }
        if (!Integer.valueOf(Constants.STATUS_ENABLED).equals(reply.getStatus())) {
            throw new BusinessException(ResultCode.CONFLICT, "话术已停用，无法引用");
        }
        quickReplyMapper.update(null, new LambdaUpdateWrapper<QuickReply>()
                .setSql("use_count = use_count + 1")
                .eq(QuickReply::getId, reply.getId()));
        QuickReply latest = quickReplyMapper.selectById(reply.getId());
        QuickReplyUseVO vo = new QuickReplyUseVO();
        vo.setId(latest.getId());
        vo.setContent(latest.getContent());
        vo.setUseCount(latest.getUseCount());
        return vo;
    }

    private QuickReply requireReply(Long id) {
        QuickReply reply = id == null ? null : quickReplyMapper.selectById(id);
        if (reply == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "话术不存在");
        }
        return reply;
    }

    /**
     * 可见性：公共话术对登录用户开放，个人话术仅本人与管理员可见。
     */
    private boolean visible(QuickReply reply, LoginUser user) {
        if (Constants.ROLE_ADMIN.equals(user.getRoleCode())) {
            return true;
        }
        if (SCOPE_PUBLIC.equals(reply.getScope())) {
            return true;
        }
        return user.getUserId().equals(reply.getOwnerId());
    }

    private void checkEditable(QuickReply reply, LoginUser user) {
        if (Constants.ROLE_ADMIN.equals(user.getRoleCode())) {
            return;
        }
        if (SCOPE_PERSONAL.equals(reply.getScope()) && user.getUserId().equals(reply.getOwnerId())) {
            return;
        }
        throw new BusinessException(ResultCode.FORBIDDEN, "仅可维护本人创建的个人话术");
    }

    /**
     * 同范围内标题不可重复，便于坐席按标题快速定位话术。
     */
    private void checkDuplicate(String scope, Long ownerId, String title, Long excludeId) {
        LambdaQueryWrapper<QuickReply> wrapper = new LambdaQueryWrapper<QuickReply>()
                .eq(QuickReply::getScope, scope)
                .eq(QuickReply::getTitle, title);
        if (SCOPE_PERSONAL.equals(scope)) {
            wrapper.eq(QuickReply::getOwnerId, ownerId);
        }
        if (excludeId != null) {
            wrapper.ne(QuickReply::getId, excludeId);
        }
        Long count = quickReplyMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "同一范围内已存在同名话术");
        }
    }

    private QuickReplyVO toVO(QuickReply reply, LoginUser user) {
        QuickReplyVO vo = new QuickReplyVO();
        vo.setId(reply.getId());
        vo.setScope(reply.getScope());
        vo.setOwnerId(reply.getOwnerId());
        vo.setCategory(reply.getCategory());
        vo.setTitle(reply.getTitle());
        vo.setContent(reply.getContent());
        vo.setSort(reply.getSort());
        vo.setUseCount(reply.getUseCount() == null ? 0 : reply.getUseCount());
        vo.setStatus(reply.getStatus());
        vo.setEditable(Constants.ROLE_ADMIN.equals(user.getRoleCode())
                || (SCOPE_PERSONAL.equals(reply.getScope()) && user.getUserId().equals(reply.getOwnerId())));
        vo.setCreateTime(reply.getCreateTime());
        return vo;
    }

    private String normalizeScope(String scope) {
        if (!StringUtils.hasText(scope)) {
            return null;
        }
        String value = scope.trim().toUpperCase();
        if (!SCOPE_PUBLIC.equals(value) && !SCOPE_PERSONAL.equals(value)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "scope 仅支持 PUBLIC 或 PERSONAL");
        }
        return value;
    }

    private Integer normalizeStatus(Integer status) {
        if (status == null) {
            return null;
        }
        if (status != Constants.STATUS_ENABLED && status != Constants.STATUS_DISABLED) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "状态只能为 0 或 1");
        }
        return status;
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, message);
        }
        return value.trim();
    }

    private LoginUser requireLogin(LoginUser loginUser) {
        if (loginUser == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return loginUser;
    }
}
