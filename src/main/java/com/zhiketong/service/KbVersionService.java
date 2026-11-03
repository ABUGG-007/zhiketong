package com.zhiketong.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiketong.common.BusinessException;
import com.zhiketong.common.ResultCode;
import com.zhiketong.dto.kb.EntryVersionVO;
import com.zhiketong.entity.KbEntry;
import com.zhiketong.entity.KbEntryVersion;
import com.zhiketong.entity.KbSimilarQuestion;
import com.zhiketong.entity.SysUser;
import com.zhiketong.enums.VersionChangeType;
import com.zhiketong.mapper.KbEntryMapper;
import com.zhiketong.mapper.KbEntryVersionMapper;
import com.zhiketong.mapper.KbSimilarQuestionMapper;
import com.zhiketong.mapper.SysUserMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 知识词条版本服务（R-78 / R-79）。
 * <p>
 * 每次词条变更都追加一条版本记录（含变更前后完整快照），
 * 支持按版本回滚；回滚本身也作为一次变更留痕，避免审计断链。
 *
 * @author 智客通开发组
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KbVersionService {

    /** 版本列表默认上限 */
    private static final int DEFAULT_LIST_LIMIT = 50;

    private final KbEntryVersionMapper versionMapper;
    private final KbEntryMapper entryMapper;
    private final KbSimilarQuestionMapper similarQuestionMapper;
    private final SysUserMapper sysUserMapper;
    private final KnowledgeMatcher knowledgeMatcher;
    private final ObjectMapper objectMapper;

    /**
     * 记录一次变更。
     *
     * @param entryId    词条 ID
     * @param type       变更类型
     * @param before     变更前快照，新建时为 null
     * @param after      变更后快照，删除时为 null
     * @param operatorId 操作人
     * @param remark     变更说明
     */
    public void record(Long entryId, VersionChangeType type, EntrySnapshot before, EntrySnapshot after,
                       Long operatorId, String remark) {
        if (entryId == null || type == null) {
            return;
        }
        KbEntryVersion version = new KbEntryVersion();
        version.setEntryId(entryId);
        version.setVersionNo(nextVersionNo(entryId));
        version.setChangeType(type.getCode());
        version.setBeforeContent(toJson(before));
        version.setAfterContent(toJson(after));
        version.setOperatorId(operatorId);
        version.setOperatorName(operatorName(operatorId));
        version.setRemark(truncate(remark, 255));
        versionMapper.insert(version);
    }

    /**
     * 为词条生成当前快照（含相似问法），供变更留痕与回滚使用。
     */
    public EntrySnapshot snapshot(KbEntry entry) {
        EntrySnapshot snapshot = new EntrySnapshot();
        snapshot.setCategoryId(entry.getCategoryId());
        snapshot.setQuestion(entry.getQuestion());
        snapshot.setAnswer(entry.getAnswer());
        snapshot.setKeywords(entry.getKeywords());
        snapshot.setTags(entry.getTags());
        snapshot.setStatus(entry.getStatus());
        List<KbSimilarQuestion> similars = similarQuestionMapper.selectList(
                new LambdaQueryWrapper<KbSimilarQuestion>()
                        .select(KbSimilarQuestion::getQuestion)
                        .eq(KbSimilarQuestion::getEntryId, entry.getId())
                        .orderByAsc(KbSimilarQuestion::getId));
        List<String> questions = new ArrayList<>(similars.size());
        for (KbSimilarQuestion similar : similars) {
            questions.add(similar.getQuestion());
        }
        snapshot.setSimilarQuestions(questions);
        return snapshot;
    }

    /**
     * 版本列表（R-78）：按版本号倒序。
     */
    public List<EntryVersionVO> list(Long entryId) {
        if (entryId == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "词条 ID 不能为空");
        }
        List<KbEntryVersion> versions = versionMapper.selectList(new LambdaQueryWrapper<KbEntryVersion>()
                .eq(KbEntryVersion::getEntryId, entryId)
                .orderByDesc(KbEntryVersion::getVersionNo)
                .last("LIMIT " + DEFAULT_LIST_LIMIT));
        List<EntryVersionVO> list = new ArrayList<>(versions.size());
        for (KbEntryVersion version : versions) {
            EntryVersionVO vo = new EntryVersionVO();
            vo.setId(version.getId());
            vo.setEntryId(version.getEntryId());
            vo.setVersionNo(version.getVersionNo());
            vo.setChangeType(version.getChangeType());
            vo.setChangeTypeDesc(changeTypeDesc(version.getChangeType()));
            vo.setBeforeContent(version.getBeforeContent());
            vo.setAfterContent(version.getAfterContent());
            vo.setOperatorName(version.getOperatorName());
            vo.setRemark(version.getRemark());
            vo.setCreateTime(version.getCreateTime());
            list.add(vo);
        }
        return list;
    }

    /**
     * 回滚到指定版本（R-79）：以该版本变更前的内容覆盖当前词条，并记录一次 ROLLBACK。
     */
    @Transactional(rollbackFor = Exception.class)
    public void rollback(Long versionId, Long operatorId) {
        KbEntryVersion version = versionId == null ? null : versionMapper.selectById(versionId);
        if (version == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "版本记录不存在");
        }
        if (!StringUtils.hasText(version.getBeforeContent())) {
            throw new BusinessException(ResultCode.CONFLICT, "该版本是首次创建，没有可回滚的历史内容");
        }
        EntrySnapshot target = fromJson(version.getBeforeContent());
        if (target == null) {
            throw new BusinessException(ResultCode.ERROR, "历史版本内容解析失败，无法回滚");
        }
        KbEntry entry = entryMapper.selectById(version.getEntryId());
        if (entry == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "词条已删除，无法回滚");
        }
        EntrySnapshot current = snapshot(entry);

        entry.setCategoryId(target.getCategoryId());
        entry.setQuestion(target.getQuestion());
        entry.setAnswer(target.getAnswer());
        entry.setKeywords(target.getKeywords() == null ? "" : target.getKeywords());
        entry.setTags(target.getTags() == null ? "" : target.getTags());
        entry.setStatus(target.getStatus() == null ? 1 : target.getStatus());
        entry.setUpdateBy(operatorId);
        entryMapper.updateById(entry);

        // 相似问法整体还原：先逻辑删除现有记录，再按快照重建
        similarQuestionMapper.delete(new LambdaQueryWrapper<KbSimilarQuestion>()
                .eq(KbSimilarQuestion::getEntryId, entry.getId()));
        restoreSimilarQuestions(entry.getId(), target.getSimilarQuestions(), operatorId);

        EntrySnapshot restored = snapshot(entryMapper.selectById(entry.getId()));
        record(entry.getId(), VersionChangeType.ROLLBACK, current, restored, operatorId,
                "回滚到版本 V" + version.getVersionNo());
        knowledgeMatcher.invalidate();
        log.info("知识词条回滚：entryId={}, versionNo={}, operatorId={}",
                entry.getId(), version.getVersionNo(), operatorId);
    }

    private void restoreSimilarQuestions(Long entryId, List<String> questions, Long operatorId) {
        if (questions == null || questions.isEmpty()) {
            return;
        }
        for (String question : questions) {
            if (!StringUtils.hasText(question)) {
                continue;
            }
            KbSimilarQuestion similar = new KbSimilarQuestion();
            similar.setEntryId(entryId);
            similar.setQuestion(question.trim());
            similar.setSource("MANUAL");
            similar.setStatus(1);
            similar.setCreateBy(operatorId);
            similar.setDeleted(0);
            similarQuestionMapper.insert(similar);
        }
    }

    private int nextVersionNo(Long entryId) {
        KbEntryVersion latest = versionMapper.selectOne(new LambdaQueryWrapper<KbEntryVersion>()
                .eq(KbEntryVersion::getEntryId, entryId)
                .orderByDesc(KbEntryVersion::getVersionNo)
                .last("LIMIT 1"));
        return latest == null || latest.getVersionNo() == null ? 1 : latest.getVersionNo() + 1;
    }

    private String operatorName(Long operatorId) {
        if (operatorId == null) {
            return null;
        }
        SysUser user = sysUserMapper.selectById(operatorId);
        if (user == null) {
            return null;
        }
        return StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername();
    }

    private String changeTypeDesc(String code) {
        if (!StringUtils.hasText(code)) {
            return null;
        }
        try {
            return VersionChangeType.valueOf(code).getDescription();
        } catch (IllegalArgumentException e) {
            return code;
        }
    }

    private String toJson(EntrySnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            log.warn("词条快照序列化失败：{}", e.getMessage());
            return null;
        }
    }

    private EntrySnapshot fromJson(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, EntrySnapshot.class);
        } catch (JsonProcessingException e) {
            log.warn("词条快照解析失败：{}", e.getMessage());
            return null;
        }
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() > max ? value.substring(0, max) : value;
    }

    /**
     * 词条内容快照：版本留痕与回滚的最小数据集。
     */
    @Data
    public static class EntrySnapshot implements Serializable {

        private static final long serialVersionUID = 1L;

        private Long categoryId;

        private String question;

        private String answer;

        private String keywords;

        private String tags;

        private Integer status;

        private List<String> similarQuestions = Collections.emptyList();
    }
}
