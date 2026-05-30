package org.example.dto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 结构化对话状态 — 替代原始对话历史文本注入 Agent prompt。
 *
 * 设计目标：
 * 1. 用 ~100 tokens 替代原始历史的 ~500+ tokens（固定上限，不随轮次增长）
 * 2. 支持指代消解（用户说"这个"时从 entities 推断）
 * 3. 支持澄清追问（pendingQuestion 标记 Agent 上一轮问了什么）
 */
public class ConversationState {

    /** 上一轮意图: PRE_SALES / POST_SALES / CHITCHAT / AMBIGUOUS */
    private String lastIntent = "";

    /** 会话中已提到的关键实体（商品名、品类、订单号等） */
    private final List<String> entities = new ArrayList<>();

    /** Agent 上一轮是否问了澄清问题，如果是，记录问题内容 */
    private String pendingQuestion = "";

    /** 当前会话轮次（一问一答为 1 轮） */
    private int turnCount = 0;

    /** 超过 3 轮的旧对话摘要 */
    private String summary = "";

    /** 最近 3 轮对话的原始文本（用于短期记忆） */
    private final List<Map<String, String>> recentHistory = new ArrayList<>();
    private static final int MAX_RECENT = 6; // 3 轮 × 2 条 = 6 条消息

    // ==================== 状态更新 ====================

    /** 记录一轮对话完成 */
    public void recordTurn(String userMessage, String aiAnswer, String detectedIntent) {
        this.lastIntent = detectedIntent;
        this.turnCount++;

        // 追加到近期记忆
        Map<String, String> userEntry = new LinkedHashMap<>();
        userEntry.put("role", "user");
        userEntry.put("content", userMessage);
        recentHistory.add(userEntry);

        Map<String, String> aiEntry = new LinkedHashMap<>();
        aiEntry.put("role", "assistant");
        aiEntry.put("content", aiAnswer);
        recentHistory.add(aiEntry);

        // 超出窗口 → 压缩最老的一轮到 summary
        while (recentHistory.size() > MAX_RECENT) {
            Map<String, String> oldUser = recentHistory.remove(0);
            Map<String, String> oldAi = recentHistory.remove(0);
            if (summary.isEmpty()) {
                summary = "历史: ";
            } else {
                summary += "; ";
            }
            summary += "用户问'" + truncate((String) oldUser.get("content"), 30)
                    + "'→'" + truncate((String) oldAi.get("content"), 30) + "'";
        }
    }

    /** 添加关键实体 */
    public void addEntity(String entity) {
        if (entity != null && !entity.isEmpty() && !entities.contains(entity)) {
            entities.add(entity);
            // 最多保留 5 个实体
            if (entities.size() > 5) {
                entities.remove(0);
            }
        }
    }

    /** 设置待澄清问题 */
    public void setPendingQuestion(String question) {
        this.pendingQuestion = question;
    }

    /** 清除待澄清问题 */
    public void clearPendingQuestion() {
        this.pendingQuestion = "";
    }

    // ==================== Prompt 上下文生成 ====================

    /**
     * 生成压缩的对话上下文，注入 Agent prompt。
     * 输出 < 200 tokens，替代原有 ~1000+ tokens 的原始历史。
     */
    public String toPromptContext() {
        StringBuilder sb = new StringBuilder();
        sb.append("对话状态: 轮次=").append(turnCount);

        if (!lastIntent.isEmpty()) {
            sb.append(", 上轮意图=").append(lastIntent);
        }
        if (!entities.isEmpty()) {
            sb.append(", 涉及=").append(String.join("/", entities));
        }
        if (!pendingQuestion.isEmpty()) {
            sb.append(", 待澄清=").append(pendingQuestion);
        }
        sb.append("\n");

        // 近期对话
        if (!recentHistory.isEmpty()) {
            sb.append("近期对话:\n");
            for (int i = 0; i < recentHistory.size(); i += 2) {
                String u = (String) recentHistory.get(i).get("content");
                String a = i + 1 < recentHistory.size()
                        ? (String) recentHistory.get(i + 1).get("content") : "";
                sb.append("  用户: ").append(truncate(u, 50)).append("\n");
                sb.append("  助手: ").append(truncate(a, 50)).append("\n");
            }
        }

        // 摘要
        if (!summary.isEmpty()) {
            sb.append("更早对话: ").append(summary).append("\n");
        }

        return sb.toString();
    }

    // ==================== getters ====================

    public String getLastIntent() { return lastIntent; }
    public List<String> getEntities() { return new ArrayList<>(entities); }
    public String getPendingQuestion() { return pendingQuestion; }
    public int getTurnCount() { return turnCount; }
    public List<Map<String, String>> getRecentHistory() { return new ArrayList<>(recentHistory); }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }
}
