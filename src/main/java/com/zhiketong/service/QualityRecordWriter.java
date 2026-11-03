package com.zhiketong.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiketong.entity.QualityItem;
import com.zhiketong.entity.QualityRecord;
import com.zhiketong.mapper.QualityItemMapper;
import com.zhiketong.mapper.QualityRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 质检结果落库组件（S5-A）。
 *
 * <p>单独成类的原因：质检评估包含大模型调用（秒级），若与落库共用一个事务，
 * 会在整段 AI 调用期间持有数据库连接，批量质检时容易打满连接池。
 * 因此评估在事务外完成，仅把「覆盖记录 + 重建明细」这一段放进短事务。</p>
 *
 * <p>一个会话只保留一条记录（{@code uk_session}），重复质检复用同一行，
 * 旧明细逻辑删除后按本次评估结果重建，保证明细与得分严格对应。</p>
 *
 * @author 智客通开发组
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QualityRecordWriter {

    private final QualityRecordMapper qualityRecordMapper;
    private final QualityItemMapper qualityItemMapper;

    /**
     * 保存质检结果：记录新增或覆盖，明细先逻辑删除再重建。
     *
     * @param record 质检记录（id 为空表示首次质检）
     * @param items  本次评估生成的明细
     */
    @Transactional(rollbackFor = Exception.class)
    public void save(QualityRecord record, List<QualityItem> items) {
        if (record.getId() == null) {
            qualityRecordMapper.insert(record);
        } else {
            qualityRecordMapper.updateById(record);
        }
        // 逻辑删除历史明细，避免重评后新旧证据混在一起
        qualityItemMapper.delete(new LambdaQueryWrapper<QualityItem>()
                .eq(QualityItem::getRecordId, record.getId()));
        for (QualityItem item : items) {
            item.setRecordId(record.getId());
            qualityItemMapper.insert(item);
        }
        log.info("质检结果落库：recordId={}, sessionId={}, score={}, hit={}/{}, engine={}",
                record.getId(), record.getSessionId(), record.getScore(),
                record.getHitCount(), record.getRuleCount(), record.getEngine());
    }
}
