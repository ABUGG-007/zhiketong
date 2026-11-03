package com.zhiketong.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiketong.dto.kb.KbDuplicateVO;
import com.zhiketong.dto.kb.KbHealthVO;
import com.zhiketong.entity.KbCategory;
import com.zhiketong.entity.KbEntry;
import com.zhiketong.mapper.KbCategoryMapper;
import com.zhiketong.mapper.KbEntryMapper;
import com.zhiketong.mapper.KbSimilarQuestionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库诊断服务：健康度看板（R-85）与查重冲突检测（R-86）。
 * <p>
 * 查重基于问法文本的结构相似度两两比较，为避免全量 O(n²) 拖慢接口，
 * 只取最近更新的前 {@value #DUPLICATE_SCAN_LIMIT} 条启用词条参与比较。
 *
 * @author 智客通开发组
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KbDiagnosisService {

    /** 参与查重比较的词条上限 */
    private static final int DUPLICATE_SCAN_LIMIT = 300;

    /** 判定为疑似重复的相似度下限 */
    private static final double DUPLICATE_THRESHOLD = 0.75;

    /** 判定为答案冲突的相似度下限 */
    private static final double CONFLICT_THRESHOLD = 0.85;

    /** 查重结果默认返回条数 */
    private static final int DEFAULT_DUPLICATE_LIMIT = 100;

    /** 健康度统计疑似重复对数时最多采集的条数 */
    private static final int HEALTH_DUPLICATE_LIMIT = 500;

    /** 健康度命中排行条数 */
    private static final int TOP_HIT_LIMIT = 10;

    /** 健康度零命中文词条展示条数 */
    private static final int ZERO_HIT_LIMIT = 20;

    /** 未命中趋势天数 */
    private static final int TREND_DAYS = 14;

    private final KbEntryMapper entryMapper;
    private final KbCategoryMapper categoryMapper;
    private final KbSimilarQuestionMapper similarQuestionMapper;
    private final KbUnmatchedService unmatchedService;
    private final KnowledgeMatcher knowledgeMatcher;

    /**
     * 知识健康度看板（R-85）。
     */
    public KbHealthVO health() {
        KbHealthVO vo = new KbHealthVO();
        vo.setEntryTotal(entryMapper.selectCount(null));
        vo.setEntryEnabled(entryMapper.selectCount(new LambdaQueryWrapper<KbEntry>()
                .eq(KbEntry::getStatus, 1)));
        vo.setSimilarTotal(similarQuestionMapper.selectCount(null));
        vo.setUnmatchedTotal(unmatchedService.countTotal());
        vo.setUnmatchedPending(unmatchedService.countPending());

        Long zeroHitTotal = entryMapper.selectCount(new LambdaQueryWrapper<KbEntry>()
                .eq(KbEntry::getStatus, 1)
                .and(w -> w.eq(KbEntry::getHitCount, 0).or().isNull(KbEntry::getHitCount)));
        List<KbEntry> zeroHitEntries = entryMapper.selectList(new LambdaQueryWrapper<KbEntry>()
                .eq(KbEntry::getStatus, 1)
                .and(w -> w.eq(KbEntry::getHitCount, 0).or().isNull(KbEntry::getHitCount))
                .orderByDesc(KbEntry::getUpdateTime)
                .last("LIMIT " + ZERO_HIT_LIMIT));
        vo.setZeroHitEntries(zeroHitTotal == null ? 0L : zeroHitTotal);

        List<KbDuplicateVO> duplicates = duplicates(HEALTH_DUPLICATE_LIMIT);
        vo.setDuplicatePairs(duplicates.size());

        Map<Long, String> categoryNames = categoryNames();
        vo.setTopHits(topHits(categoryNames));
        vo.setZeroHitList(zeroHitItems(zeroHitEntries, categoryNames));
        vo.setTopUnmatched(unmatchedService.topUnmatched(TOP_HIT_LIMIT));
        vo.setCategories(categoryCoverage(categoryNames));
        vo.setTrend(unmatchedService.trend(TREND_DAYS));
        return vo;
    }

    /**
     * 疑似重复与答案冲突检测（R-86）。
     *
     * @param limit 返回条数上限，null 或非正数时取默认值
     */
    public List<KbDuplicateVO> duplicates(Integer limit) {
        int max = limit == null || limit <= 0 ? DEFAULT_DUPLICATE_LIMIT : limit;
        List<KbEntry> entries = entryMapper.selectList(new LambdaQueryWrapper<KbEntry>()
                .eq(KbEntry::getStatus, 1)
                .orderByDesc(KbEntry::getUpdateTime)
                .last("LIMIT " + DUPLICATE_SCAN_LIMIT));
        if (entries.size() < 2) {
            return Collections.emptyList();
        }
        Map<Long, String> categoryNames = categoryNames();
        List<KbDuplicateVO> results = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            KbEntry left = entries.get(i);
            for (int j = i + 1; j < entries.size(); j++) {
                KbEntry right = entries.get(j);
                double similarity = knowledgeMatcher.similarityOf(left.getQuestion(), right.getQuestion());
                if (similarity < DUPLICATE_THRESHOLD) {
                    continue;
                }
                boolean conflict = similarity >= CONFLICT_THRESHOLD
                        && answersDiffer(left.getAnswer(), right.getAnswer());
                KbDuplicateVO vo = new KbDuplicateVO();
                vo.setEntryAId(left.getId());
                vo.setEntryAQuestion(left.getQuestion());
                vo.setEntryBId(right.getId());
                vo.setEntryBQuestion(right.getQuestion());
                vo.setCategoryName(categoryNames.get(left.getCategoryId()));
                vo.setSimilarity(similarity);
                vo.setConflict(conflict);
                vo.setReason(conflict
                        ? "问法高度相似但答案不一致，请确认是否存在口径冲突"
                        : "问法高度相似，建议合并到同一条词条并互为相似问法");
                results.add(vo);
            }
        }
        results.sort(Comparator.comparingDouble(KbDuplicateVO::getSimilarity).reversed());
        if (results.size() > max) {
            return new ArrayList<>(results.subList(0, max));
        }
        return results;
    }

    private List<KbHealthVO.HitItem> topHits(Map<Long, String> categoryNames) {
        List<KbEntry> entries = entryMapper.selectList(new LambdaQueryWrapper<KbEntry>()
                .eq(KbEntry::getStatus, 1)
                .orderByDesc(KbEntry::getHitCount)
                .orderByAsc(KbEntry::getId)
                .last("LIMIT " + TOP_HIT_LIMIT));
        List<KbHealthVO.HitItem> items = new ArrayList<>(entries.size());
        for (KbEntry entry : entries) {
            items.add(toHitItem(entry, categoryNames));
        }
        return items;
    }

    private List<KbHealthVO.HitItem> zeroHitItems(List<KbEntry> entries, Map<Long, String> categoryNames) {
        List<KbHealthVO.HitItem> items = new ArrayList<>(entries.size());
        for (KbEntry entry : entries) {
            items.add(toHitItem(entry, categoryNames));
        }
        return items;
    }

    private KbHealthVO.HitItem toHitItem(KbEntry entry, Map<Long, String> categoryNames) {
        KbHealthVO.HitItem item = new KbHealthVO.HitItem();
        item.setId(entry.getId());
        item.setQuestion(entry.getQuestion());
        item.setCategoryName(categoryNames.get(entry.getCategoryId()));
        item.setHitCount(entry.getHitCount() == null ? 0 : entry.getHitCount());
        return item;
    }

    private List<KbHealthVO.CategoryCoverage> categoryCoverage(Map<Long, String> categoryNames) {
        List<KbEntry> entries = entryMapper.selectList(new LambdaQueryWrapper<KbEntry>()
                .select(KbEntry::getCategoryId, KbEntry::getHitCount, KbEntry::getStatus)
                .eq(KbEntry::getStatus, 1));
        Map<Long, long[]> stats = new HashMap<>();
        for (KbEntry entry : entries) {
            if (entry.getCategoryId() == null) {
                continue;
            }
            long[] counter = stats.computeIfAbsent(entry.getCategoryId(), key -> new long[2]);
            counter[0]++;
            if (entry.getHitCount() == null || entry.getHitCount() == 0) {
                counter[1]++;
            }
        }
        List<KbHealthVO.CategoryCoverage> list = new ArrayList<>();
        for (Map.Entry<Long, long[]> item : stats.entrySet()) {
            KbHealthVO.CategoryCoverage coverage = new KbHealthVO.CategoryCoverage();
            coverage.setCategoryId(item.getKey());
            coverage.setCategoryName(categoryNames.get(item.getKey()));
            coverage.setEntryCount(item.getValue()[0]);
            coverage.setZeroHitCount(item.getValue()[1]);
            list.add(coverage);
        }
        list.sort(Comparator.comparingLong(KbHealthVO.CategoryCoverage::getEntryCount).reversed());
        return list;
    }

    private Map<Long, String> categoryNames() {
        List<KbCategory> categories = categoryMapper.selectList(null);
        Map<Long, String> map = new HashMap<>();
        for (KbCategory category : categories) {
            map.put(category.getId(), category.getName());
        }
        return map;
    }

    private boolean answersDiffer(String left, String right) {
        String leftNormalized = StringUtils.hasText(left) ? knowledgeMatcher.normalize(left) : "";
        String rightNormalized = StringUtils.hasText(right) ? knowledgeMatcher.normalize(right) : "";
        return !leftNormalized.equals(rightNormalized);
    }
}
