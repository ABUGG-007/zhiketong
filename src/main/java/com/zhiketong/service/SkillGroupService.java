package com.zhiketong.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiketong.common.BusinessException;
import com.zhiketong.common.Constants;
import com.zhiketong.common.ResultCode;
import com.zhiketong.dto.skill.SkillGroupBriefVO;
import com.zhiketong.dto.skill.SkillGroupMemberVO;
import com.zhiketong.dto.skill.SkillGroupSaveRequest;
import com.zhiketong.dto.skill.SkillGroupVO;
import com.zhiketong.entity.ChatSession;
import com.zhiketong.entity.SkillGroup;
import com.zhiketong.entity.SysUser;
import com.zhiketong.entity.SysUserSkill;
import com.zhiketong.enums.SessionStatus;
import com.zhiketong.mapper.ChatSessionMapper;
import com.zhiketong.mapper.SkillGroupMapper;
import com.zhiketong.mapper.SysUserMapper;
import com.zhiketong.mapper.SysUserSkillMapper;
import com.zhiketong.security.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 技能组服务（R-94 ~ R-100）。
 *
 * <p>技能组是会话分配的最小单元：访客转人工时选择咨询类型落到
 * {@code chat_session.skill_group_id}，自动分配优先在该组在岗坐席中挑选，
 * 组内无可用坐席时降级为全体在岗坐席，保证会话不被单一技能组堵死。</p>
 *
 * @author 智客通开发组
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkillGroupService {

    /** 成员能力值默认 3（中位） */
    private static final int DEFAULT_LEVEL = 3;

    /** 能力值下限 */
    private static final int MIN_LEVEL = 1;

    /** 能力值上限 */
    private static final int MAX_LEVEL = 5;

    private final SkillGroupMapper skillGroupMapper;
    private final SysUserSkillMapper sysUserSkillMapper;
    private final SysUserMapper sysUserMapper;
    private final ChatSessionMapper chatSessionMapper;

    /**
     * 技能组列表（R-96），含成员数量；withMembers 为 true 时附带成员明细。
     */
    public List<SkillGroupVO> list(boolean withMembers) {
        List<SkillGroup> groups = skillGroupMapper.selectList(new LambdaQueryWrapper<SkillGroup>()
                .orderByAsc(SkillGroup::getSort)
                .orderByAsc(SkillGroup::getId));
        return toVOs(groups, withMembers);
    }

    /**
     * 技能组详情（R-97）。
     */
    public SkillGroupVO detail(Long id) {
        return toVOs(Collections.singletonList(requireGroup(id)), true).get(0);
    }

    /**
     * 新增技能组（R-98）：成员随请求全量覆盖。
     */
    @Transactional(rollbackFor = Exception.class)
    public SkillGroupVO create(SkillGroupSaveRequest request, LoginUser loginUser) {
        if (request == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "技能组信息不能为空");
        }
        String name = requireName(request.getName());
        checkNameUnique(name, null);
        SkillGroup group = new SkillGroup();
        group.setName(name);
        group.setDescription(trimToNull(request.getDescription()));
        group.setSort(request.getSort() == null ? 0 : request.getSort());
        group.setStatus(request.getStatus() == null ? Constants.STATUS_ENABLED : normalizeStatus(request.getStatus()));
        group.setDeleted(0);
        skillGroupMapper.insert(group);
        replaceMembers(group.getId(), request);
        log.info("新增技能组：id={}, name={}, operatorId={}", group.getId(), name, userId(loginUser));
        return detail(group.getId());
    }

    /**
     * 修改技能组（R-99）：名称唯一，成员以本次提交为准覆盖。
     */
    @Transactional(rollbackFor = Exception.class)
    public SkillGroupVO update(Long id, SkillGroupSaveRequest request, LoginUser loginUser) {
        SkillGroup group = requireGroup(id);
        if (request == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "技能组信息不能为空");
        }
        String name = requireName(request.getName());
        checkNameUnique(name, group.getId());
        if (request.getStatus() != null && normalizeStatus(request.getStatus()) == Constants.STATUS_DISABLED
                && Integer.valueOf(Constants.STATUS_ENABLED).equals(group.getStatus())) {
            checkNoWaitingSession(group.getId(), "仍有排队会话属于该技能组，请先处理后再停用");
        }
        group.setName(name);
        group.setDescription(trimToNull(request.getDescription()));
        if (request.getSort() != null) {
            group.setSort(request.getSort());
        }
        if (request.getStatus() != null) {
            group.setStatus(normalizeStatus(request.getStatus()));
        }
        skillGroupMapper.updateById(group);
        replaceMembers(group.getId(), request);
        log.info("修改技能组：id={}, name={}, operatorId={}", group.getId(), name, userId(loginUser));
        return detail(group.getId());
    }

    /**
     * 启用/停用技能组（R-100）：停用后不再参与分配与访客选择。
     */
    @Transactional(rollbackFor = Exception.class)
    public SkillGroupVO updateStatus(Long id, Integer status, LoginUser loginUser) {
        SkillGroup group = requireGroup(id);
        int target = normalizeStatus(status);
        if (target == Constants.STATUS_DISABLED && Integer.valueOf(Constants.STATUS_ENABLED).equals(group.getStatus())) {
            checkNoWaitingSession(group.getId(), "仍有排队会话属于该技能组，请先处理后再停用");
        }
        group.setStatus(target);
        skillGroupMapper.updateById(group);
        log.info("技能组状态变更：id={}, status={}, operatorId={}", group.getId(), target, userId(loginUser));
        return detail(group.getId());
    }

    /**
     * 删除技能组（R-101）：成员关系一并清理，存在排队会话时拒绝删除。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, LoginUser loginUser) {
        SkillGroup group = requireGroup(id);
        checkNoWaitingSession(group.getId(), "仍有排队会话属于该技能组，请先处理后再删除");
        sysUserSkillMapper.delete(new LambdaQueryWrapper<SysUserSkill>()
                .eq(SysUserSkill::getSkillGroupId, group.getId()));
        skillGroupMapper.deleteById(group.getId());
        log.info("删除技能组：id={}, name={}, operatorId={}", group.getId(), group.getName(), userId(loginUser));
    }

    /**
     * 访客端可选咨询类型（R-103）：仅启用状态的技能组。
     */
    public List<SkillGroupBriefVO> enabledList() {
        List<SkillGroup> groups = skillGroupMapper.selectList(new LambdaQueryWrapper<SkillGroup>()
                .eq(SkillGroup::getStatus, Constants.STATUS_ENABLED)
                .orderByAsc(SkillGroup::getSort)
                .orderByAsc(SkillGroup::getId));
        List<SkillGroupBriefVO> list = new ArrayList<>(groups.size());
        for (SkillGroup group : groups) {
            list.add(toBriefVO(group));
        }
        return list;
    }

    /**
     * 坐席「我的技能组」（R-102）：仅返回本人所属且启用的技能组。
     */
    public List<SkillGroupBriefVO> mine(Long agentId) {
        if (agentId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        Set<Long> groupIds = groupIdsOf(agentId);
        if (groupIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<SkillGroup> groups = skillGroupMapper.selectList(new LambdaQueryWrapper<SkillGroup>()
                .in(SkillGroup::getId, groupIds)
                .eq(SkillGroup::getStatus, Constants.STATUS_ENABLED)
                .orderByAsc(SkillGroup::getSort)
                .orderByAsc(SkillGroup::getId));
        List<SkillGroupBriefVO> list = new ArrayList<>(groups.size());
        for (SkillGroup group : groups) {
            list.add(toBriefVO(group));
        }
        return list;
    }

    /**
     * 组内坐席 ID 集合，用于分配时按技能组过滤候选。
     */
    public Set<Long> memberIdsOf(Long groupId) {
        if (groupId == null) {
            return Collections.emptySet();
        }
        List<SysUserSkill> relations = sysUserSkillMapper.selectList(new LambdaQueryWrapper<SysUserSkill>()
                .eq(SysUserSkill::getSkillGroupId, groupId));
        Set<Long> ids = new HashSet<>(relations.size());
        for (SysUserSkill relation : relations) {
            ids.add(relation.getUserId());
        }
        return ids;
    }

    /**
     * 组内坐席能力值映射，供分配时在同负载候选之间优先高能力值坐席。
     */
    public Map<Long, Integer> levelMapOf(Long groupId) {
        if (groupId == null) {
            return Collections.emptyMap();
        }
        List<SysUserSkill> relations = sysUserSkillMapper.selectList(new LambdaQueryWrapper<SysUserSkill>()
                .eq(SysUserSkill::getSkillGroupId, groupId));
        Map<Long, Integer> map = new HashMap<>(relations.size());
        for (SysUserSkill relation : relations) {
            map.put(relation.getUserId(), relation.getLevel());
        }
        return map;
    }

    /**
     * 组内是否已有成员；无成员的技能组视为不限，避免分配被空组堵死。
     */
    public boolean hasMembers(Long groupId) {
        if (groupId == null) {
            return false;
        }
        Long count = sysUserSkillMapper.selectCount(new LambdaQueryWrapper<SysUserSkill>()
                .eq(SysUserSkill::getSkillGroupId, groupId));
        return count != null && count > 0;
    }

    /**
     * 坐席是否属于指定技能组。
     */
    public boolean isMember(Long groupId, Long agentId) {
        if (groupId == null || agentId == null) {
            return false;
        }
        Long count = sysUserSkillMapper.selectCount(new LambdaQueryWrapper<SysUserSkill>()
                .eq(SysUserSkill::getSkillGroupId, groupId)
                .eq(SysUserSkill::getUserId, agentId));
        return count != null && count > 0;
    }

    /**
     * 取启用状态的技能组，不存在或已停用时返回 null（转人工选择咨询类型时校验）。
     */
    public SkillGroup activeGroup(Long groupId) {
        if (groupId == null) {
            return null;
        }
        SkillGroup group = skillGroupMapper.selectById(groupId);
        if (group == null || !Integer.valueOf(Constants.STATUS_ENABLED).equals(group.getStatus())) {
            return null;
        }
        return group;
    }

    /**
     * 批量取技能组名称，供会话列表展示咨询类型。
     */
    public Map<Long, String> nameMap(Collection<Long> groupIds) {
        if (groupIds == null || groupIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<SkillGroup> groups = skillGroupMapper.selectBatchIds(groupIds);
        Map<Long, String> map = new HashMap<>(groups.size());
        for (SkillGroup group : groups) {
            map.put(group.getId(), group.getName());
        }
        return map;
    }

    private Set<Long> groupIdsOf(Long userId) {
        List<SysUserSkill> relations = sysUserSkillMapper.selectList(new LambdaQueryWrapper<SysUserSkill>()
                .eq(SysUserSkill::getUserId, userId));
        Set<Long> ids = new HashSet<>(relations.size());
        for (SysUserSkill relation : relations) {
            ids.add(relation.getSkillGroupId());
        }
        return ids;
    }

    /**
     * 成员全量覆盖：先清空原关系，再按提交顺序重建，能力值缺省为 3。
     */
    private void replaceMembers(Long groupId, SkillGroupSaveRequest request) {
        sysUserSkillMapper.delete(new LambdaQueryWrapper<SysUserSkill>()
                .eq(SysUserSkill::getSkillGroupId, groupId));
        List<Long> memberIds = request.getMemberIds();
        if (memberIds == null || memberIds.isEmpty()) {
            return;
        }
        Map<Long, Integer> levelMap = new HashMap<>(memberIds.size());
        if (request.getLevels() != null) {
            for (SkillGroupSaveRequest.MemberLevel item : request.getLevels()) {
                if (item != null && item.getUserId() != null && item.getLevel() != null) {
                    levelMap.put(item.getUserId(), item.getLevel());
                }
            }
        }
        Set<Long> distinctIds = new LinkedHashSet<>(memberIds);
        Map<Long, SysUser> users = userMap(distinctIds);
        for (Long userId : distinctIds) {
            SysUser user = users.get(userId);
            if (user == null || !Constants.ROLE_AGENT.equals(user.getRoleCode())
                    || !Integer.valueOf(Constants.STATUS_ENABLED).equals(user.getStatus())) {
                throw new BusinessException(ResultCode.NOT_FOUND, "坐席不存在或不可用：userId=" + userId);
            }
            SysUserSkill relation = new SysUserSkill();
            relation.setUserId(userId);
            relation.setSkillGroupId(groupId);
            relation.setLevel(clampLevel(levelMap.get(userId)));
            sysUserSkillMapper.insert(relation);
        }
    }

    private List<SkillGroupVO> toVOs(List<SkillGroup> groups, boolean withMembers) {
        if (groups == null || groups.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> groupIds = new ArrayList<>(groups.size());
        for (SkillGroup group : groups) {
            groupIds.add(group.getId());
        }
        List<SysUserSkill> relations = sysUserSkillMapper.selectList(new LambdaQueryWrapper<SysUserSkill>()
                .in(SysUserSkill::getSkillGroupId, groupIds)
                .orderByAsc(SysUserSkill::getSkillGroupId)
                .orderByAsc(SysUserSkill::getUserId));
        Map<Long, List<SysUserSkill>> relationMap = new HashMap<>(groups.size());
        Set<Long> userIds = new HashSet<>(relations.size());
        for (SysUserSkill relation : relations) {
            relationMap.computeIfAbsent(relation.getSkillGroupId(), k -> new ArrayList<>()).add(relation);
            userIds.add(relation.getUserId());
        }
        Map<Long, SysUser> users = userMap(userIds);

        List<SkillGroupVO> list = new ArrayList<>(groups.size());
        for (SkillGroup group : groups) {
            SkillGroupVO vo = new SkillGroupVO();
            vo.setId(group.getId());
            vo.setName(group.getName());
            vo.setDescription(group.getDescription());
            vo.setSort(group.getSort());
            vo.setStatus(group.getStatus());
            vo.setCreateTime(group.getCreateTime());
            List<SysUserSkill> groupRelations = relationMap.getOrDefault(group.getId(), Collections.emptyList());
            vo.setMemberCount(groupRelations.size());
            if (withMembers) {
                List<SkillGroupMemberVO> members = new ArrayList<>(groupRelations.size());
                for (SysUserSkill relation : groupRelations) {
                    SysUser user = users.get(relation.getUserId());
                    if (user == null) {
                        continue;
                    }
                    SkillGroupMemberVO member = new SkillGroupMemberVO();
                    member.setUserId(user.getId());
                    member.setUsername(user.getUsername());
                    member.setNickname(StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername());
                    member.setLevel(relation.getLevel());
                    members.add(member);
                }
                vo.setMembers(members);
            }
            list.add(vo);
        }
        return list;
    }

    private SkillGroupBriefVO toBriefVO(SkillGroup group) {
        SkillGroupBriefVO vo = new SkillGroupBriefVO();
        vo.setId(group.getId());
        vo.setName(group.getName());
        vo.setDescription(group.getDescription());
        return vo;
    }

    private Map<Long, SysUser> userMap(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<SysUser> users = sysUserMapper.selectBatchIds(userIds);
        Map<Long, SysUser> map = new HashMap<>(users.size());
        for (SysUser user : users) {
            map.put(user.getId(), user);
        }
        return map;
    }

    private SkillGroup requireGroup(Long id) {
        SkillGroup group = id == null ? null : skillGroupMapper.selectById(id);
        if (group == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "技能组不存在");
        }
        return group;
    }

    private void checkNameUnique(String name, Long excludeId) {
        LambdaQueryWrapper<SkillGroup> wrapper = new LambdaQueryWrapper<SkillGroup>()
                .eq(SkillGroup::getName, name);
        if (excludeId != null) {
            wrapper.ne(SkillGroup::getId, excludeId);
        }
        Long count = skillGroupMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "技能组名称已存在");
        }
    }

    /**
     * 停用/删除前校验：仍在排队的会话会引用该技能组，需先处理完。
     */
    private void checkNoWaitingSession(Long groupId, String message) {
        Long count = chatSessionMapper.selectCount(new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getSkillGroupId, groupId)
                .eq(ChatSession::getStatus, SessionStatus.WAITING.getCode()));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.CONFLICT, message);
        }
    }

    private String requireName(String name) {
        if (!StringUtils.hasText(name)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "技能组名称不能为空");
        }
        String trimmed = name.trim();
        if (trimmed.length() > 64) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "技能组名称不能超过 64 个字符");
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

    private int clampLevel(Integer level) {
        if (level == null) {
            return DEFAULT_LEVEL;
        }
        return Math.min(MAX_LEVEL, Math.max(MIN_LEVEL, level));
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
