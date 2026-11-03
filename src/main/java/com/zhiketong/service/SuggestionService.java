package com.zhiketong.service;

import com.zhiketong.dto.assist.SuggestionVO;
import com.zhiketong.security.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 输入联想服务（R-111 访客端 / R-112 坐席端，对应 AS-01 / AS-02）。
 *
 * <p>基于知识库标准问题与相似问法实时召回，不落库：</p>
 * <ul>
 *   <li>只返回问法与匹配可信度，访客端不返回答案内容，避免把联想列表变成答案列表；</li>
 *   <li>坐席端附带答案摘要，便于一键引用到回复框；</li>
 *   <li>低于联想阈值（0.25，低于参考知识阈值 0.35）的候选直接丢弃，避免噪声。</li>
 * </ul>
 *
 * @author 智客通开发组
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SuggestionService {

    /** 联想场景：站内访客 */
    public static final String SCOPE_VISITOR = "VISITOR";

    /** 联想场景：坐席 */
    public static final String SCOPE_AGENT = "AGENT";

    /** 联想候选最低得分 */
    private static final double MIN_SCORE = 0.25D;

    /** 联想默认条数 */
    private static final int DEFAULT_LIMIT = 5;

    /** 联想最大条数 */
    private static final int MAX_LIMIT = 8;

    /** 答案摘要在联想列表中的最大长度 */
    private static final int ANSWER_PREVIEW_LENGTH = 80;

    private final KnowledgeMatcher knowledgeMatcher;
    private final AgentSessionGuard agentSessionGuard;

    /**
     * 站内访客输入联想（R-111）：不返回答案内容。
     *
     * @param query 当前输入
     * @param limit 条数，1-8
     * @return 联想结果
     */
    public SuggestionVO suggestForVisitor(String query, Integer limit) {
        return suggest(query, limit, false, SCOPE_VISITOR);
    }

    /**
     * 坐席输入联想（R-112）：先校验会话数据范围，再返回带答案摘要的候选。
     *
     * @param sessionId 会话 ID
     * @param loginUser 当前登录用户
     * @param query     当前输入
     * @param limit     条数，1-8
     * @return 联想结果
     */
    public SuggestionVO suggestForAgent(Long sessionId, LoginUser loginUser, String query, Integer limit) {
        agentSessionGuard.requireOwned(sessionId, loginUser);
        return suggest(query, limit, true, SCOPE_AGENT);
    }

    /**
     * 召回联想候选。
     *
     * @param query      当前输入内容
     * @param limit      条数，1-8
     * @param withAnswer 是否返回答案摘要（坐席端 true）
     * @param scope      VISITOR / AGENT
     * @return 联想结果
     */
    public SuggestionVO suggest(String query, Integer limit, boolean withAnswer, String scope) {
        int max = limit == null ? DEFAULT_LIMIT : Math.min(Math.max(limit, 1), MAX_LIMIT);
        SuggestionVO vo = new SuggestionVO();
        vo.setScope(scope);
        String keyword = query == null ? "" : query.trim();
        vo.setQuery(keyword);
        if (!StringUtils.hasText(keyword)) {
            vo.setNote("继续输入即可联想知识库问法");
            return vo;
        }
        // 闲聊与无效输入不做联想，避免把「你好」类内容映射到知识条目
        if (knowledgeMatcher.isChitchat(keyword) || knowledgeMatcher.isInvalid(keyword)) {
            vo.setNote("未找到匹配问法，可直接描述问题");
            return vo;
        }

        List<SuggestionVO.Item> items = new ArrayList<>();
        for (KnowledgeMatcher.MatchResult match : knowledgeMatcher.match(keyword, null, max * 2)) {
            if (match.getScore() < MIN_SCORE) {
                continue;
            }
            String question = StringUtils.hasText(match.getQuestion())
                    ? match.getQuestion() : match.getMatchedQuestion();
            if (!StringUtils.hasText(question) || containsQuestion(items, question)) {
                continue;
            }
            SuggestionVO.Item item = new SuggestionVO.Item();
            item.setKbId(match.getId());
            item.setQuestion(question);
            item.setMatchedQuestion(match.getMatchedQuestion());
            item.setMatchedType(match.getMatchedType());
            item.setScore(match.getScore());
            item.setCategoryName(match.getCategoryName());
            if (withAnswer) {
                item.setAnswer(abbreviate(match.getAnswer(), ANSWER_PREVIEW_LENGTH));
            }
            items.add(item);
            if (items.size() >= max) {
                break;
            }
        }
        vo.setItems(items);
        vo.setNote(items.isEmpty() ? "未找到匹配问法，可直接描述问题" : "联想问法来自知识库，点击可引用");
        return vo;
    }

    private boolean containsQuestion(List<SuggestionVO.Item> items, String question) {
        for (SuggestionVO.Item item : items) {
            if (question.equals(item.getQuestion())) {
                return true;
            }
        }
        return false;
    }

    private String abbreviate(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) + "…" : trimmed;
    }
}
