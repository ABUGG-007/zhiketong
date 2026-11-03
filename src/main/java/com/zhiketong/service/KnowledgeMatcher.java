package com.zhiketong.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiketong.config.properties.ChatProperties;
import com.zhiketong.entity.KbCategory;
import com.zhiketong.entity.KbEntry;
import com.zhiketong.entity.KbSimilarQuestion;
import com.zhiketong.enums.AnswerCategory;
import com.zhiketong.mapper.KbCategoryMapper;
import com.zhiketong.mapper.KbEntryMapper;
import com.zhiketong.mapper.KbSimilarQuestionMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 知识库匹配器：实现协议文档 5.2 的关键词命中 + 2-gram Dice 相似度评分。
 * <p>
 * 启用且分类（含父分类）启用的条目会缓存在内存中，条目/分类变更后由业务层调用
 * {@link #invalidate()} 失效缓存；匹配全程在内存完成，避免每轮对话查询数据库。
 *
 * @author 智客通开发组
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeMatcher {

    /** 匹配类型：标准问题 */
    private static final String MATCH_TYPE_STANDARD = "STANDARD";

    /** 匹配类型：相似问法 */
    private static final String MATCH_TYPE_SIMILAR = "SIMILAR";

    /** 关键词命中 2 个及以上即视为满分 */
    private static final double KEYWORD_FULL_HIT = 2.0;

    /** 关键词得分与相似度得分权重各占一半 */
    private static final double WEIGHT = 0.5;

    /** 逗号分隔的关键词中，单个关键词达到该长度即视为完整命中 */
    private static final double KEYWORD_FULL_LENGTH = 4.0;

    /**
     * 允许按单字替换的停用词：仅限语气助词。
     * “请”这类单字会误伤“申请/请求”等词，必须跳过。
     */
    private static final Set<String> SAFE_SINGLE_CHAR_STOPWORDS = new HashSet<>(Arrays.asList(
            "的", "了", "吗", "呢", "啊", "呀", "吧", "哦", "嗯"));

    /** 停用词文件路径 */
    private static final String STOPWORDS_FILE = "stopwords.txt";

    /** 参考知识下限：0.35 ~ 直答阈值之间的候选注入大模型提示词（与问答分类口径一致） */
    private static final double REFERENCE_THRESHOLD = 0.35;

    /** 闲聊识别：命中任一表达且整句较短时归类为 CHITCHAT */
    private static final List<String> CHITCHAT_TOKENS = Arrays.asList(
            "你好", "您好", "在吗", "在么", "在不在", "有人吗", "谢谢", "感谢", "多谢",
            "再见", "拜拜", "好的", "收到", "你是谁", "机器人", "hello", "hi", "thanks", "thankyou", "ok");

    /** 闲聊句最大长度（归一化后字符数） */
    private static final int CHITCHAT_MAX_LENGTH = 12;

    private final KbEntryMapper kbEntryMapper;
    private final KbCategoryMapper kbCategoryMapper;
    private final KbSimilarQuestionMapper kbSimilarQuestionMapper;
    private final ChatProperties chatProperties;

    /** 条目快照缓存，null 表示需要重新加载 */
    private volatile List<EntrySnapshot> snapshot;

    /** 停用词集合 */
    private Set<String> stopwords = Collections.emptySet();

    /**
     * 启动时加载停用词表，文件缺失时使用内置默认词表。
     */
    @PostConstruct
    public void initStopwords() {
        Set<String> words = new HashSet<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource(STOPWORDS_FILE).getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String word = line.trim();
                if (!word.isEmpty()) {
                    words.add(word);
                }
            }
        } catch (IOException e) {
            log.warn("加载停用词表失败，使用内置词表：{}", e.getMessage());
            words.addAll(Arrays.asList("的", "了", "吗", "呢", "啊", "请问", "我想", "怎么", "什么", "如何"));
        }
        this.stopwords = words;
    }

    /**
     * 使条目缓存失效，下次匹配时重新加载。
     */
    public void invalidate() {
        snapshot = null;
        log.debug("知识库匹配缓存已失效");
    }

    /**
     * 直接回答阈值（zhiketong.chat.ai-confidence-threshold）。
     */
    public double getDirectAnswerThreshold() {
        return chatProperties.getAiConfidenceThreshold();
    }

    /**
     * 参考知识下限：低于该得分不注入提示词，也不作为推荐候选。
     */
    public double getReferenceThreshold() {
        return REFERENCE_THRESHOLD;
    }

    /**
     * 问答分类（对标七鱼五分类兜底策略）：直接命中 / 推荐 / 无效 / 闲聊 / 兜底。
     * <p>大模型实际应答成功时由调用方另行标记为 REFERENCE 或 FREE，本方法只处理入参分类判断。</p>
     *
     * @param query     用户问句
     * @param bestScore 知识库最高匹配得分
     * @return 应答分类
     */
    public AnswerCategory classify(String query, double bestScore) {
        if (bestScore >= getDirectAnswerThreshold()) {
            return AnswerCategory.DIRECT;
        }
        if (isInvalid(query)) {
            return AnswerCategory.INVALID;
        }
        if (bestScore >= REFERENCE_THRESHOLD) {
            return AnswerCategory.SUGGEST;
        }
        if (isChitchat(query)) {
            return AnswerCategory.CHITCHAT;
        }
        return AnswerCategory.FALLBACK;
    }

    /**
     * 无效输入：归一化后过短、纯数字或单字重复。
     */
    public boolean isInvalid(String query) {
        String compact = normalize(query).replace(" ", "");
        if (compact.length() < 2) {
            return true;
        }
        if (compact.matches("[0-9]+")) {
            return true;
        }
        return compact.matches("(.)\\1+");
    }

    /**
     * 闲聊识别：短句且命中问候/致谢/告别等表达。
     */
    public boolean isChitchat(String query) {
        String compact = normalize(query).replace(" ", "");
        if (compact.isEmpty() || compact.length() > CHITCHAT_MAX_LENGTH) {
            return false;
        }
        for (String token : CHITCHAT_TOKENS) {
            if (compact.contains(token)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 计算两句的文本结构相似度，供知识查重与冲突检测复用。
     */
    public double similarityOf(String left, String right) {
        return round(similarity(toGrams(normalize(left)), toGrams(normalize(right))));
    }

    /**
     * 匹配候选条目。
     *
     * @param query       用户问句
     * @param categoryIds 限定分类 ID 集合，null 表示不限定
     * @param limit       返回条数上限
     * @return 按得分降序的候选列表
     */
    public List<MatchResult> match(String query, Collection<Long> categoryIds, int limit) {
        if (query == null || query.trim().isEmpty() || limit <= 0) {
            return Collections.emptyList();
        }
        String normalized = normalize(query);
        if (normalized.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> queryGrams = toGrams(normalized);
        List<MatchResult> results = new ArrayList<>();
        for (EntrySnapshot entry : getSnapshot()) {
            if (categoryIds != null && !categoryIds.contains(entry.getCategoryId())) {
                continue;
            }
            List<String> hitKeywords = new ArrayList<>();
            double keywordWeight = 0;
            for (int i = 0; i < entry.getNormalizedKeywords().size(); i++) {
                String keyword = entry.getNormalizedKeywords().get(i);
                if (!keyword.isEmpty() && normalized.contains(keyword)) {
                    hitKeywords.add(entry.getKeywords().get(i));
                    // 关键词越长信息量越大：4 字及以上计满分，短词按长度折算，避免“退款”这类泛词拉高得分
                    keywordWeight += Math.min(1.0, keyword.length() / KEYWORD_FULL_LENGTH);
                }
            }
            double keywordScore = Math.min(1.0, keywordWeight / KEYWORD_FULL_HIT);
            // 相似度取「标准问题 + 全部启用相似问法」的最高值，同义改写与口语化问法靠相似问法召回
            double similarityScore = 0;
            QuestionVariant bestVariant = null;
            for (QuestionVariant variant : entry.getVariants()) {
                double variantScore = similarity(queryGrams, variant.getGrams());
                if (variantScore > similarityScore) {
                    similarityScore = variantScore;
                    bestVariant = variant;
                }
            }
            double score = round(WEIGHT * keywordScore + WEIGHT * similarityScore);
            if (score <= 0) {
                continue;
            }
            MatchResult result = new MatchResult();
            result.setId(entry.getId());
            result.setQuestion(entry.getQuestion());
            result.setMatchedQuestion(bestVariant == null ? entry.getQuestion() : bestVariant.getQuestion());
            result.setMatchedType(bestVariant == null ? MATCH_TYPE_STANDARD : bestVariant.getType());
            result.setAnswer(entry.getAnswer());
            result.setCategoryId(entry.getCategoryId());
            result.setCategoryName(entry.getCategoryName());
            result.setHitCount(entry.getHitCount());
            result.setScore(score);
            result.setHitKeywords(hitKeywords);
            results.add(result);
        }
        results.sort(Comparator.comparingDouble(MatchResult::getScore).reversed()
                .thenComparing(Comparator.comparingInt(MatchResult::getHitCount).reversed())
                .thenComparing(MatchResult::getId));
        if (results.size() > limit) {
            return new ArrayList<>(results.subList(0, limit));
        }
        return results;
    }

    /**
     * 加载启用条目快照：条目启用、所属分类启用，二级分类还要求父分类启用。
     */
    private List<EntrySnapshot> getSnapshot() {
        List<EntrySnapshot> local = snapshot;
        if (local != null) {
            return local;
        }
        synchronized (this) {
            if (snapshot == null) {
                snapshot = loadSnapshot();
            }
            return snapshot;
        }
    }

    private List<EntrySnapshot> loadSnapshot() {
        List<KbCategory> categories = kbCategoryMapper.selectList(null);
        Map<Long, KbCategory> categoryMap = new HashMap<>();
        for (KbCategory category : categories) {
            categoryMap.put(category.getId(), category);
        }
        List<KbEntry> entries = kbEntryMapper.selectList(new LambdaQueryWrapper<KbEntry>()
                .eq(KbEntry::getStatus, 1));
        Map<Long, List<String>> similarMap = loadSimilarQuestions(entries);
        List<EntrySnapshot> list = new ArrayList<>();
        for (KbEntry entry : entries) {
            KbCategory category = categoryMap.get(entry.getCategoryId());
            if (!isCategoryEnabled(category, categoryMap)) {
                continue;
            }
            EntrySnapshot item = new EntrySnapshot();
            item.setId(entry.getId());
            item.setQuestion(entry.getQuestion());
            item.setAnswer(entry.getAnswer());
            item.setCategoryId(entry.getCategoryId());
            item.setCategoryName(category.getName());
            item.setHitCount(entry.getHitCount() == null ? 0 : entry.getHitCount());
            // 标准问题作为第 0 个变体，相似问法紧随其后，打分时取最高值
            List<QuestionVariant> variants = new ArrayList<>();
            variants.add(buildVariant(entry.getQuestion(), MATCH_TYPE_STANDARD));
            for (String similar : similarMap.getOrDefault(entry.getId(), Collections.emptyList())) {
                variants.add(buildVariant(similar, MATCH_TYPE_SIMILAR));
            }
            item.setVariants(variants);
            List<String> keywords = splitKeywords(entry.getKeywords());
            item.setKeywords(keywords);
            List<String> normalizedKeywords = new ArrayList<>(keywords.size());
            for (String keyword : keywords) {
                normalizedKeywords.add(normalize(keyword));
            }
            item.setNormalizedKeywords(normalizedKeywords);
            list.add(item);
        }
        log.info("知识库匹配缓存加载完成，可用条目数={}", list.size());
        return Collections.unmodifiableList(list);
    }

    /**
     * 加载启用条目的相似问法，按条目分组。
     */
    private Map<Long, List<String>> loadSimilarQuestions(List<KbEntry> entries) {
        if (entries.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> entryIds = new ArrayList<>(entries.size());
        for (KbEntry entry : entries) {
            entryIds.add(entry.getId());
        }
        List<KbSimilarQuestion> similars = kbSimilarQuestionMapper.selectList(
                new LambdaQueryWrapper<KbSimilarQuestion>()
                        .select(KbSimilarQuestion::getEntryId, KbSimilarQuestion::getQuestion)
                        .eq(KbSimilarQuestion::getStatus, 1)
                        .in(KbSimilarQuestion::getEntryId, entryIds));
        Map<Long, List<String>> map = new HashMap<>();
        for (KbSimilarQuestion similar : similars) {
            if (similar.getQuestion() == null || similar.getQuestion().trim().isEmpty()) {
                continue;
            }
            map.computeIfAbsent(similar.getEntryId(), key -> new ArrayList<>())
                    .add(similar.getQuestion().trim());
        }
        return map;
    }

    private QuestionVariant buildVariant(String question, String type) {
        QuestionVariant variant = new QuestionVariant();
        variant.setQuestion(question);
        variant.setType(type);
        variant.setGrams(toGrams(normalize(question)));
        return variant;
    }

    /**
     * 分类是否启用：自身启用，二级分类还要求父分类存在且启用。
     */
    private boolean isCategoryEnabled(KbCategory category, Map<Long, KbCategory> categoryMap) {
        if (category == null || !Integer.valueOf(1).equals(category.getStatus())) {
            return false;
        }
        Long parentId = category.getParentId();
        if (parentId == null || parentId == 0L) {
            return true;
        }
        KbCategory parent = categoryMap.get(parentId);
        return parent != null && Integer.valueOf(1).equals(parent.getStatus());
    }

    /**
     * 文本预处理：全角转半角、英文小写、去标点、去停用词、压缩空白。
     *
     * @param text 原始文本
     * @return 预处理后的文本
     */
    public String normalize(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder(text.length());
        for (char ch : text.toCharArray()) {
            char current = ch;
            if (current >= '\uFF01' && current <= '\uFF5E') {
                // 全角 ASCII 转半角
                current = (char) (current - 0xFEE0);
            } else if (current == '\u3000') {
                current = ' ';
            }
            if (Character.isLetterOrDigit(current) || Character.isWhitespace(current)) {
                builder.append(Character.toLowerCase(current));
            } else {
                builder.append(' ');
            }
        }
        String result = builder.toString().replaceAll("\\s+", " ").trim();
        for (String stopword : stopwords) {
            if (stopword.isEmpty()) {
                continue;
            }
            // 单字停用词只处理语气助词，否则“请”会把“申请退款”拆成“申 退款”，破坏词结构
            if (stopword.length() == 1 && !SAFE_SINGLE_CHAR_STOPWORDS.contains(stopword)) {
                continue;
            }
            result = result.replace(stopword, " ");
        }
        return result.replaceAll("\\s+", " ").trim();
    }

    /**
     * 切词：中文按 2-gram 切分，英文/数字按空格切分保留整词。
     */
    private Set<String> toGrams(String text) {
        if (text == null || text.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> grams = new HashSet<>();
        StringBuilder cjk = new StringBuilder();
        StringBuilder ascii = new StringBuilder();
        for (char ch : text.toCharArray()) {
            if (isCjk(ch)) {
                flushAscii(ascii, grams);
                cjk.append(ch);
            } else if (Character.isLetterOrDigit(ch)) {
                flushCjk(cjk, grams);
                ascii.append(ch);
            } else {
                flushCjk(cjk, grams);
                flushAscii(ascii, grams);
            }
        }
        flushCjk(cjk, grams);
        flushAscii(ascii, grams);
        return grams;
    }

    private void flushCjk(StringBuilder buffer, Set<String> grams) {
        String text = buffer.toString();
        for (int i = 0; i + 1 < text.length(); i++) {
            grams.add(text.substring(i, i + 2));
        }
        buffer.setLength(0);
    }

    private void flushAscii(StringBuilder buffer, Set<String> grams) {
        if (buffer.length() > 0) {
            grams.add(buffer.toString());
            buffer.setLength(0);
        }
    }

    private boolean isCjk(char ch) {
        return (ch >= '\u4E00' && ch <= '\u9FFF')
                || (ch >= '\u3400' && ch <= '\u4DBF')
                || (ch >= '\uF900' && ch <= '\uFAFF');
    }

    /**
     * 结构相似度：Dice 系数占 60%，包含度占 40%。
     * <p>包含度（交集 / 较短一侧）用于缓解“用户问句较长、问法被完整包含”时 Dice 被稀释的问题。</p>
     */
    private double similarity(Set<String> left, Set<String> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return 0.0;
        }
        int intersection = 0;
        for (String gram : left) {
            if (right.contains(gram)) {
                intersection++;
            }
        }
        double dice = 2.0 * intersection / (left.size() + right.size());
        double containment = (double) intersection / Math.min(left.size(), right.size());
        return 0.6 * dice + 0.4 * containment;
    }

    private double round(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    /**
     * 拆分英文逗号分隔的关键词，兼容中文逗号并去除空白项。
     */
    private List<String> splitKeywords(String keywords) {
        if (keywords == null || keywords.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<String> list = new ArrayList<>();
        for (String item : keywords.replace('，', ',').split(",")) {
            String keyword = item.trim();
            if (!keyword.isEmpty()) {
                list.add(keyword);
            }
        }
        return list;
    }

    /**
     * 条目快照：匹配所需的只读数据。
     */
    @Data
    private static class EntrySnapshot {

        private Long id;

        private String question;

        private String answer;

        private Long categoryId;

        private String categoryName;

        private Integer hitCount;

        private List<String> keywords = Collections.emptyList();

        private List<String> normalizedKeywords = Collections.emptyList();

        /** 全部可匹配问法：标准问题 + 相似问法 */
        private List<QuestionVariant> variants = Collections.emptyList();
    }

    /**
     * 问法变体：标准问题或条相似问法。
     */
    @Data
    private static class QuestionVariant {

        private String question;

        /** STANDARD 标准问题 / SIMILAR 相似问法 */
        private String type;

        private Set<String> grams = Collections.emptySet();
    }

    /**
     * 匹配结果。
     */
    @Data
    public static class MatchResult {

        /** 条目 ID */
        private Long id;

        /** 标准问题 */
        private String question;

        /** 实际命中的问法（标准问题或相似问法） */
        private String matchedQuestion;

        /** 命中类型：STANDARD / SIMILAR */
        private String matchedType;

        /** 标准答案 */
        private String answer;

        /** 所属分类 ID */
        private Long categoryId;

        /** 所属分类名称 */
        private String categoryName;

        /** 累计命中次数 */
        private Integer hitCount;

        /** 匹配得分，0-1 */
        private double score;

        /** 命中的关键词 */
        private List<String> hitKeywords = Collections.emptyList();
    }
}
