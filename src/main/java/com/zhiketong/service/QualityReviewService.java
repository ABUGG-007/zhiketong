package com.zhiketong.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhiketong.common.BusinessException;
import com.zhiketong.common.ResultCode;
import com.zhiketong.dto.quality.QualityRecordDetailVO;
import com.zhiketong.dto.quality.QualityReviewRequest;
import com.zhiketong.entity.QualityItem;
import com.zhiketong.entity.QualityRecord;
import com.zhiketong.enums.AuditAction;
import com.zhiketong.enums.AuditModule;
import com.zhiketong.enums.QualityRecordStatus;
import com.zhiketong.mapper.QualityItemMapper;
import com.zhiketong.mapper.QualityRecordMapper;
import com.zhiketong.security.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 质检人工复核服务（S5-B，QC-06）。
 *
 * <p>状态机严格单向可控，非法流转直接 409：</p>
 * <pre>
 * PENDING --提交复核(R-172)--> REVIEWED --确认归档(R-173)--> CONFIRMED（终态）
 *                                      \--驳回退回(R-174)--> PENDING
 * </pre>
 *
 * <p>三条数据一致性约定：</p>
 * <ol>
 *   <li>自动质检结论（score / grade / pass_flag / hit / deduct_score）永不被复核改写，
 *       复核结果单独存放在 review_* 列，人机对比因此可完整回溯；</li>
 *   <li>只调总分时视为「整体调分」，复核分即生效分，必须填写复核意见；</li>
 *   <li>逐条改判时复核分必须等于「100 - 改判后扣分合计」，由服务端核算并校验，
 *       避免评分卡明细与总分互相矛盾。</li>
 * </ol>
 *
 * @author 智客通开发组
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QualityReviewService {

    /** 单条改判扣分上限，与规则扣分上限保持一致 */
    private static final int MAX_DEDUCT = 30;

    private final QualityRecordMapper qualityRecordMapper;
    private final QualityItemMapper qualityItemMapper;
    private final QualityService qualityService;
    private final AuditLogService auditLogService;

    /**
     * 提交人工复核（R-172）：PENDING → REVIEWED，可整体调分或逐条改判。
     *
     * @param id        质检记录 ID
     * @param request   复核请求
     * @param loginUser 复核人
     * @return 复核后的评分卡详情
     */
    @Transactional(rollbackFor = Exception.class)
    public QualityRecordDetailVO review(Long id, QualityReviewRequest request, LoginUser loginUser) {
        QualityRecord record = requireRecord(id);
        if (!QualityRecordStatus.PENDING.getCode().equals(record.getStatus())) {
            throw new BusinessException(ResultCode.CONFLICT, "仅「待复核」的记录可以提交复核，当前状态："
                    + statusName(record.getStatus()));
        }
        List<QualityItem> items = loadItems(record.getId());
        if (items.isEmpty()) {
            throw new BusinessException(ResultCode.CONFLICT, "质检明细缺失，无法复核");
        }
        Map<Long, QualityReviewRequest.ItemReview> patches = collectPatches(request, items);

        int deductSum = 0;
        int hitCount = 0;
        for (QualityItem item : items) {
            QualityReviewRequest.ItemReview patch = patches.get(item.getId());
            int hit = item.effectiveHit();
            int deduct = item.effectiveDeduct();
            if (patch != null) {
                hit = patch.getHit();
                if (hit == 1) {
                    if (patch.getDeductScore() == null) {
                        throw new BusinessException(ResultCode.PARAM_ERROR,
                                "改判「" + item.getRuleName() + "」为命中时必须填写扣分");
                    }
                    deduct = Math.max(0, Math.min(MAX_DEDUCT, patch.getDeductScore()));
                } else {
                    deduct = 0;
                }
                item.setReviewHit(hit);
                item.setReviewDeduct(deduct);
            }
            if (hit == 1) {
                hitCount++;
                deductSum += deduct;
            }
        }

        int autoScore = record.getScore() == null ? 0 : record.getScore();
        int computed = Math.max(0, 100 - deductSum);
        Integer reviewScore = request.getReviewScore();
        if (!patches.isEmpty()) {
            if (reviewScore != null && reviewScore != computed) {
                throw new BusinessException(ResultCode.PARAM_ERROR,
                        "复核分与逐条改判不一致：按改判结果应为 " + computed + " 分");
            }
            reviewScore = computed;
        } else if (reviewScore == null) {
            // 未做任何调整，等价于「确认自动结论」
            reviewScore = autoScore;
        }

        for (QualityItem item : items) {
            if (item.getReviewHit() != null) {
                qualityItemMapper.updateById(item);
            }
        }

        QualityRecord before = copyOf(record);
        record.setStatus(QualityRecordStatus.REVIEWED.getCode());
        record.setReviewAction(AuditAction.REVIEW.getCode());
        record.setReviewerId(loginUser.getUserId());
        record.setReviewerName(displayName(loginUser));
        record.setReviewComment(trimToNull(request.getReviewComment()));
        record.setReviewScore(reviewScore);
        record.setReviewTime(LocalDateTime.now());
        qualityRecordMapper.updateById(record);

        auditLogService.record(AuditModule.QUALITY, AuditAction.REVIEW)
                .target("QUALITY_RECORD", record.getId(), record.getSessionNo())
                .detail("复核质检记录：" + record.getSessionNo() + "，自动 " + autoScore + " 分 → 复核 "
                        + reviewScore + " 分，命中 " + hitCount + " 条"
                        + (patches.isEmpty() ? "（未改判明细）" : "（改判 " + patches.size() + " 条）"))
                .before(before)
                .after(record)
                .save();
        log.info("质检复核完成：recordId={}, autoScore={}, reviewScore={}, patch={}, operatorId={}",
                record.getId(), autoScore, reviewScore, patches.size(), loginUser.getUserId());
        return qualityService.recordDetail(record.getId());
    }

    /**
     * 确认归档（R-173）：REVIEWED → CONFIRMED，终态不可再变更。
     *
     * @param id        质检记录 ID
     * @param comment   确认备注，可空，只进审计不影响评分卡
     * @param loginUser 确认人
     * @return 归档后的评分卡详情
     */
    @Transactional(rollbackFor = Exception.class)
    public QualityRecordDetailVO confirm(Long id, String comment, LoginUser loginUser) {
        QualityRecord record = requireRecord(id);
        if (!QualityRecordStatus.REVIEWED.getCode().equals(record.getStatus())) {
            throw new BusinessException(ResultCode.CONFLICT, "仅「已复核」的记录可以确认归档，当前状态："
                    + statusName(record.getStatus()));
        }
        QualityRecord before = copyOf(record);
        record.setStatus(QualityRecordStatus.CONFIRMED.getCode());
        record.setReviewAction(AuditAction.CONFIRM.getCode());
        record.setReviewerId(loginUser.getUserId());
        record.setReviewerName(displayName(loginUser));
        record.setReviewTime(LocalDateTime.now());
        // 复核分兜底：未调分的记录在归档时明确落到自动分，便于口径统计
        if (record.getReviewScore() == null) {
            record.setReviewScore(record.getScore());
        }
        qualityRecordMapper.updateById(record);

        auditLogService.record(AuditModule.QUALITY, AuditAction.CONFIRM)
                .target("QUALITY_RECORD", record.getId(), record.getSessionNo())
                .detail("确认质检记录归档：" + record.getSessionNo() + "，生效 "
                        + record.effectiveScore() + " 分"
                        + (StringUtils.hasText(comment) ? "，备注：" + comment.trim() : ""))
                .before(before)
                .after(record)
                .save();
        log.info("质检记录归档：recordId={}, score={}, operatorId={}",
                record.getId(), record.effectiveScore(), loginUser.getUserId());
        return qualityService.recordDetail(record.getId());
    }

    /**
     * 驳回退回（R-174）：REVIEWED → PENDING，复核结论作废，等待重新复核。
     *
     * <p>复核分与逐条改判一并清空，避免作废结论混入人机对比统计；
     * 原结论已完整留存于审计的 before 快照，可追溯。</p>
     *
     * @param id        质检记录 ID
     * @param reason    驳回原因，必填
     * @param loginUser 驳回人
     * @return 退回后的评分卡详情
     */
    @Transactional(rollbackFor = Exception.class)
    public QualityRecordDetailVO reject(Long id, String reason, LoginUser loginUser) {
        QualityRecord record = requireRecord(id);
        if (!QualityRecordStatus.REVIEWED.getCode().equals(record.getStatus())) {
            throw new BusinessException(ResultCode.CONFLICT, "仅「已复核」的记录可以驳回退回，当前状态："
                    + statusName(record.getStatus()));
        }
        if (!StringUtils.hasText(reason)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "驳回必须填写原因");
        }
        QualityRecord before = copyOf(record);
        String operator = displayName(loginUser);
        LocalDateTime now = LocalDateTime.now();

        // updateById 会忽略 null 字段，清空复核分必须用 UpdateWrapper 显式 set null
        qualityRecordMapper.update(null, new LambdaUpdateWrapper<QualityRecord>()
                .eq(QualityRecord::getId, record.getId())
                .set(QualityRecord::getStatus, QualityRecordStatus.PENDING.getCode())
                .set(QualityRecord::getReviewAction, AuditAction.REJECT.getCode())
                .set(QualityRecord::getReviewScore, null)
                .set(QualityRecord::getReviewComment, reason.trim())
                .set(QualityRecord::getReviewerId, loginUser.getUserId())
                .set(QualityRecord::getReviewerName, operator)
                .set(QualityRecord::getReviewTime, now));
        qualityItemMapper.update(null, new LambdaUpdateWrapper<QualityItem>()
                .eq(QualityItem::getRecordId, record.getId())
                .set(QualityItem::getReviewHit, null)
                .set(QualityItem::getReviewDeduct, null));

        auditLogService.record(AuditModule.QUALITY, AuditAction.REJECT)
                .target("QUALITY_RECORD", record.getId(), record.getSessionNo())
                .detail("驳回质检复核结论并退回待复核：" + record.getSessionNo()
                        + "，原复核分 " + before.getReviewScore() + "，原因：" + reason.trim())
                .before(before)
                .save();
        log.info("质检复核被驳回：recordId={}, reason={}, operatorId={}",
                record.getId(), reason.trim(), loginUser.getUserId());
        return qualityService.recordDetail(record.getId());
    }

    // ------------------------------------------------------------------
    // 内部工具
    // ------------------------------------------------------------------

    /**
     * 改判清单归一：校验明细归属与重复提交，避免跨记录改判。
     */
    private Map<Long, QualityReviewRequest.ItemReview> collectPatches(QualityReviewRequest request,
                                                                     List<QualityItem> items) {
        Map<Long, QualityReviewRequest.ItemReview> patches = new HashMap<>();
        if (request.getItems() == null || request.getItems().isEmpty()) {
            return patches;
        }
        Map<Long, QualityItem> owned = new HashMap<>();
        for (QualityItem item : items) {
            owned.put(item.getId(), item);
        }
        for (QualityReviewRequest.ItemReview patch : request.getItems()) {
            if (patch == null || patch.getItemId() == null) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "改判明细缺少 itemId");
            }
            if (!owned.containsKey(patch.getItemId())) {
                throw new BusinessException(ResultCode.PARAM_ERROR,
                        "改判明细不属于该质检记录：itemId=" + patch.getItemId());
            }
            if (patches.put(patch.getItemId(), patch) != null) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "同一明细不能重复提交改判");
            }
        }
        return patches;
    }

    private QualityRecord requireRecord(Long id) {
        // selectById 经多租户拦截器自动附加 tenant_id 条件，跨租户访问直接查不到
        QualityRecord record = id == null ? null : qualityRecordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "质检记录不存在");
        }
        return record;
    }

    private List<QualityItem> loadItems(Long recordId) {
        return qualityItemMapper.selectList(new LambdaQueryWrapper<QualityItem>()
                .eq(QualityItem::getRecordId, recordId)
                .orderByAsc(QualityItem::getId));
    }

    private String statusName(String status) {
        QualityRecordStatus value = QualityRecordStatus.fromCode(status);
        return value == null ? String.valueOf(status) : value.getDescription();
    }

    private String displayName(LoginUser loginUser) {
        return StringUtils.hasText(loginUser.getNickname())
                ? loginUser.getNickname() : loginUser.getUsername();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /**
     * 复核前快照：只保留审计需要的评分与状态字段，避免快照过大。
     */
    private QualityRecord copyOf(QualityRecord source) {
        QualityRecord copy = new QualityRecord();
        copy.setId(source.getId());
        copy.setSessionNo(source.getSessionNo());
        copy.setScore(source.getScore());
        copy.setGrade(source.getGrade());
        copy.setPassFlag(source.getPassFlag());
        copy.setStatus(source.getStatus());
        copy.setReviewAction(source.getReviewAction());
        copy.setReviewScore(source.getReviewScore());
        copy.setReviewComment(source.getReviewComment());
        copy.setReviewerName(source.getReviewerName());
        copy.setReviewTime(source.getReviewTime());
        return copy;
    }
}
