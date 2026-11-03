package com.zhiketong.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiketong.common.BusinessException;
import com.zhiketong.common.ResultCode;
import com.zhiketong.dto.kb.CategoryCreateRequest;
import com.zhiketong.dto.kb.CategoryUpdateRequest;
import com.zhiketong.dto.kb.CategoryVO;
import com.zhiketong.entity.KbCategory;
import com.zhiketong.entity.KbEntry;
import com.zhiketong.mapper.KbCategoryMapper;
import com.zhiketong.mapper.KbEntryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 知识库分类服务：两级分类树的查询与维护（R-18 ~ R-22）。
 * <p>
 * 分类启停不级联修改子分类或条目，匹配时由 {@link KnowledgeMatcher} 校验父链状态。
 *
 * @author 智客通开发组
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KbCategoryService {

    /** 顶级分类的父 ID */
    private static final long ROOT_PARENT_ID = 0L;

    private static final int DEFAULT_SORT = 99;

    private final KbCategoryMapper kbCategoryMapper;
    private final KbEntryMapper kbEntryMapper;
    private final KnowledgeMatcher knowledgeMatcher;

    /**
     * 分类列表：支持树形/平铺两种形式。
     *
     * @param status 状态筛选，null 返回全部
     * @param tree   true 返回两级树，false 返回平铺列表
     * @return 分类列表
     */
    public List<CategoryVO> list(Integer status, boolean tree) {
        List<KbCategory> categories = kbCategoryMapper.selectList(new LambdaQueryWrapper<KbCategory>()
                .eq(status != null, KbCategory::getStatus, status)
                .orderByAsc(KbCategory::getSort)
                .orderByAsc(KbCategory::getId));
        Map<Long, Long> entryCounts = countEntriesByCategory();
        Map<Long, CategoryVO> voMap = new LinkedHashMap<>();
        for (KbCategory category : categories) {
            voMap.put(category.getId(), toVO(category, entryCounts, tree));
        }
        if (!tree) {
            return new ArrayList<>(voMap.values());
        }
        List<CategoryVO> roots = new ArrayList<>();
        for (CategoryVO vo : voMap.values()) {
            Long parentId = vo.getParentId();
            if (parentId == null || parentId == ROOT_PARENT_ID) {
                roots.add(vo);
                continue;
            }
            CategoryVO parent = voMap.get(parentId);
            if (parent != null) {
                parent.getChildren().add(vo);
            } else if (status == null) {
                // 父分类已被删除时的兜底展示
                roots.add(vo);
            }
        }
        return roots;
    }

    /**
     * 新增分类（R-19）。
     */
    @Transactional(rollbackFor = Exception.class)
    public CategoryVO create(CategoryCreateRequest request) {
        long parentId = request.getParentId() == null ? ROOT_PARENT_ID : request.getParentId();
        validateParent(parentId, null, false);
        String name = request.getName().trim();
        checkNameUnique(parentId, name, null);

        KbCategory category = new KbCategory();
        category.setName(name);
        category.setParentId(parentId);
        category.setSort(request.getSort() == null ? DEFAULT_SORT : request.getSort());
        category.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        category.setDescription(request.getDescription());
        kbCategoryMapper.insert(category);
        knowledgeMatcher.invalidate();
        return toVO(category, Collections.singletonMap(category.getId(), 0L), true);
    }

    /**
     * 编辑分类（R-20）。
     */
    @Transactional(rollbackFor = Exception.class)
    public CategoryVO update(Long id, CategoryUpdateRequest request) {
        KbCategory category = requireCategory(id);
        long targetParentId = category.getParentId() == null ? ROOT_PARENT_ID : category.getParentId();
        if (request.getParentId() != null) {
            targetParentId = request.getParentId();
            validateParent(targetParentId, id, true);
            Long childCount = kbCategoryMapper.selectCount(new LambdaQueryWrapper<KbCategory>()
                    .eq(KbCategory::getParentId, id));
            if (targetParentId != ROOT_PARENT_ID && childCount != null && childCount > 0) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "该分类下存在子分类，不能移动为二级分类");
            }
            category.setParentId(targetParentId);
        }
        if (request.getName() != null) {
            String name = request.getName().trim();
            checkNameUnique(targetParentId, name, id);
            category.setName(name);
        }
        if (request.getSort() != null) {
            category.setSort(request.getSort());
        }
        if (request.getStatus() != null) {
            category.setStatus(request.getStatus());
        }
        if (request.getDescription() != null) {
            category.setDescription(request.getDescription());
        }
        kbCategoryMapper.updateById(category);
        knowledgeMatcher.invalidate();
        return toVO(category, countEntriesByCategory(), true);
    }

    /**
     * 删除分类（R-21）：存在子分类或条目时禁止删除。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireCategory(id);
        Long childCount = kbCategoryMapper.selectCount(new LambdaQueryWrapper<KbCategory>()
                .eq(KbCategory::getParentId, id));
        if (childCount != null && childCount > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "该分类下存在子分类，请先删除子分类");
        }
        Long entryCount = kbEntryMapper.selectCount(new LambdaQueryWrapper<KbEntry>()
                .eq(KbEntry::getCategoryId, id));
        if (entryCount != null && entryCount > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "该分类下存在知识条目，请先迁移或删除条目");
        }
        kbCategoryMapper.deleteById(id);
        knowledgeMatcher.invalidate();
        log.info("删除知识分类：id={}", id);
    }

    /**
     * 启用/禁用分类（R-22）。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> updateStatus(Long id, Integer status) {
        KbCategory category = requireCategory(id);
        category.setStatus(status);
        kbCategoryMapper.updateById(category);
        knowledgeMatcher.invalidate();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("status", status);
        return result;
    }

    /**
     * 查询分类，不存在时抛出 404。
     */
    public KbCategory requireCategory(Long id) {
        KbCategory category = id == null ? null : kbCategoryMapper.selectById(id);
        if (category == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "知识分类不存在");
        }
        return category;
    }

    /**
     * 解析分类筛选范围：分类自身 + 直接子分类。
     *
     * @param categoryId 分类 ID，null 返回 null 表示不限定
     * @return 分类 ID 集合
     */
    public Set<Long> resolveCategoryIds(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        Set<Long> ids = new HashSet<>();
        ids.add(categoryId);
        List<KbCategory> children = kbCategoryMapper.selectList(new LambdaQueryWrapper<KbCategory>()
                .select(KbCategory::getId)
                .eq(KbCategory::getParentId, categoryId));
        for (KbCategory child : children) {
            ids.add(child.getId());
        }
        return ids;
    }

    /**
     * 校验父分类：0 或存在的一级分类。
     *
     * @param parentId 父分类 ID
     * @param selfId   当前分类 ID，新增时为 null
     * @param moving   是否为编辑移动操作
     */
    private void validateParent(Long parentId, Long selfId, boolean moving) {
        if (parentId == null || parentId < 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "父分类 ID 不合法");
        }
        if (parentId == ROOT_PARENT_ID) {
            return;
        }
        if (parentId.equals(selfId)) {
            throw new BusinessException(ResultCode.CONFLICT, "父分类不能为自身");
        }
        KbCategory parent = kbCategoryMapper.selectById(parentId);
        if (parent == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "父分类不存在");
        }
        if (parent.getParentId() != null && parent.getParentId() != ROOT_PARENT_ID) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "仅支持两级分类，父分类必须为一级分类");
        }
        if (moving && selfId != null && selfId.equals(parent.getParentId())) {
            throw new BusinessException(ResultCode.CONFLICT, "不允许将分类移动到自己的子分类下");
        }
    }

    /**
     * 同一父级下分类名唯一。
     */
    private void checkNameUnique(Long parentId, String name, Long excludeId) {
        Long count = kbCategoryMapper.selectCount(new LambdaQueryWrapper<KbCategory>()
                .eq(KbCategory::getParentId, parentId)
                .eq(KbCategory::getName, name)
                .ne(excludeId != null, KbCategory::getId, excludeId));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "同一父级下分类名称已存在");
        }
    }

    /**
     * 统计各分类下的条目数。
     */
    private Map<Long, Long> countEntriesByCategory() {
        List<KbEntry> entries = kbEntryMapper.selectList(new LambdaQueryWrapper<KbEntry>()
                .select(KbEntry::getCategoryId));
        Map<Long, Long> counts = new HashMap<>();
        for (KbEntry entry : entries) {
            if (entry.getCategoryId() != null) {
                counts.merge(entry.getCategoryId(), 1L, Long::sum);
            }
        }
        return counts;
    }

    private CategoryVO toVO(KbCategory category, Map<Long, Long> entryCounts, boolean withChildren) {
        CategoryVO vo = new CategoryVO();
        vo.setId(category.getId());
        vo.setName(category.getName());
        vo.setParentId(category.getParentId());
        vo.setSort(category.getSort());
        vo.setStatus(category.getStatus());
        vo.setDescription(category.getDescription());
        vo.setEntryCount(entryCounts.getOrDefault(category.getId(), 0L));
        if (withChildren) {
            vo.setChildren(new ArrayList<>());
        }
        return vo;
    }
}
