package org.example.service;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import org.example.agent.tool.audit.ExactMatchTools;
import org.example.agent.tool.audit.HybridSearchTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 退货规则判定 Agent
 * 基于混合检索（向量语义 + 精确关键词）做语境推理，区分"真投诉"与"咨询"、商品名与比喻
 *
 * 提供两个能力：
 * 1. buildReturnRuleAgent() — 构建可独立运行的 rule_judge_agent（供外部编排调用）
 * 2. analyzeReturnText()   — @Tool 方法，供 return_audit_agent 在信息收集阶段调用
 *
 * 优化：
 * - LRU 缓存：相同退货文本避免重复调 embedding API（100 条上限）
 * - 精简输出：每类只保留 top-3，字段缩写，去除冗余，减少 Agent 上下文 token 消耗
 */
@Service
public class ReturnRuleAgentService {

    private static final Logger logger = LoggerFactory.getLogger(ReturnRuleAgentService.class);

    @Autowired
    private HybridSearchTools hybridSearchTools;

    @Autowired
    private ExactMatchTools exactMatchTools;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * LRU 缓存 — 相同退货文本在审核流程中可能被多次查询，
     * 缓存避免重复调用 embedding API。100 条上限，最近最少使用淘汰。
     */
    private final Map<String, String> analysisCache =
            Collections.synchronizedMap(new LinkedHashMap<>(100, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > 100;
                }
            });

    // ==================== @Tool: 语义分析（精简输出 + 缓存） ====================

    /**
     * 语义分析工具（精简输出 + LRU 缓存）
     *
     * 设计思路：
     * 1. 缓存：相同退货文本避免重复 embedding API 调用，100 条 LRU 覆盖常见退货表述。
     * 2. 精简：Agent 只需要 top 信号（哪些规则类型被触发、最高分、需消歧），
     *    不需要完整的 keyword/embedding text/source 详情。
     *    输出从 ~600 chars 压缩到 ~200 chars，减少 Agent 上下文的 token 消耗。
     */
    @Tool(name = "analyzeReturnText",
          description = "对退货文本做语义分析。同时执行混合检索和精确匹配，按规则类型分组输出 top 命中。" +
                  "用于了解退货文本中隐含的规则触发信号。")
    public String analyzeReturnText(
            @ToolParam(description = "退货原因和描述的合并文本") String text) {

        // LRU 缓存
        String cacheKey = text.trim().toLowerCase();
        String cached = analysisCache.get(cacheKey);
        if (cached != null) {
            logger.debug("analyzeReturnText 缓存命中: {}", cacheKey.substring(0, Math.min(30, cacheKey.length())));
            return cached;
        }

        String result = doAnalyze(text);
        analysisCache.put(cacheKey, result);
        return result;
    }

    private String doAnalyze(String text) {
        List<Map<String, Object>> allHits = new ArrayList<>();

        // 路径1: 混合检索
        try {
            String hybridResult = hybridSearchTools.hybridKeywordSearch(text);
            JsonNode hybridRoot = objectMapper.readTree(hybridResult);
            if (hybridRoot.has("matches")) {
                for (JsonNode m : hybridRoot.get("matches")) {
                    Map<String, Object> hit = new LinkedHashMap<>();
                    hit.put("keyword", m.get("keyword").asText());
                    hit.put("score", m.get("score").asDouble());
                    // 提取规则类型: ruleType(向量路) 或 category(精确路)
                    String type = extractType(m);
                    if (!type.isEmpty()) hit.put("ruleType", type);
                    allHits.add(hit);
                }
            }
        } catch (Exception e) {
            logger.warn("analyzeReturnText 混合检索失败: {}", e.getMessage());
        }

        // 路径2: 精确匹配（确认实体词）
        try {
            String exactResult = exactMatchTools.exactMatchKeywords(text);
            JsonNode exactRoot = objectMapper.readTree(exactResult);
            if (exactRoot.has("matches")) {
                for (JsonNode m : exactRoot.get("matches")) {
                    String kw = m.get("keyword").asText();
                    boolean alreadyExists = allHits.stream()
                            .anyMatch(h -> kw.equals(h.get("keyword")));
                    if (!alreadyExists) {
                        Map<String, Object> hit = new LinkedHashMap<>();
                        hit.put("keyword", kw);
                        hit.put("score", m.get("score").asDouble());
                        if (m.has("category")) hit.put("ruleType", m.get("category").asText());
                        allHits.add(hit);
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("analyzeReturnText 精确匹配失败: {}", e.getMessage());
        }

        // 按 ruleType 分组、去重、只保留每类 top-3
        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        for (String type : new String[]{"escalation", "strict_reject", "auto_approve"}) {
            grouped.put(type, new ArrayList<>());
        }

        for (Map<String, Object> hit : allHits) {
            String mappedType = mapRuleType((String) hit.getOrDefault("ruleType", "unknown"));
            grouped.computeIfAbsent(mappedType, k -> new ArrayList<>()).add(hit);
        }

        // 构建精简输出
        Map<String, Object> analysis = new LinkedHashMap<>();

        for (String type : new String[]{"escalation", "strict_reject", "auto_approve"}) {
            List<Map<String, Object>> list = grouped.getOrDefault(type, Collections.emptyList());
            // 去重
            Set<String> seen = new HashSet<>();
            List<Map<String, Object>> deduped = new ArrayList<>();
            for (Map<String, Object> h : list) {
                String kw = ((String) h.get("keyword")).toLowerCase();
                if (seen.add(kw)) deduped.add(h);
            }
            // 按 score 降序
            deduped.sort((a, b) -> Double.compare(
                    ((Number) b.get("score")).doubleValue(),
                    ((Number) a.get("score")).doubleValue()));

            // 只保留 top-3，精简字段: k=截断关键词, s=分数
            List<Map<String, Object>> slim = new ArrayList<>();
            int limit = Math.min(3, deduped.size());
            for (int i = 0; i < limit; i++) {
                Map<String, Object> h = deduped.get(i);
                Map<String, Object> item = new LinkedHashMap<>();
                String kw = (String) h.get("keyword");
                item.put("k", kw.length() > 40 ? kw.substring(0, 37) + "..." : kw);
                item.put("s", Math.round(((Number) h.get("score")).doubleValue() * 1000) / 1000.0);
                slim.add(item);
            }
            if (!slim.isEmpty()) {
                analysis.put(type, slim);
            }
        }

        // 消歧提示（仅当有命中时）
        List<String> hints = new ArrayList<>();
        if (!grouped.getOrDefault("escalation", Collections.emptyList()).isEmpty()) {
            hints.add("升级词命中，需语境判断");
        }
        if (!grouped.getOrDefault("strict_reject", Collections.emptyList()).isEmpty()) {
            hints.add("严格拒绝品类命中，需确认是否真实商品名");
        }
        if (!grouped.getOrDefault("auto_approve", Collections.emptyList()).isEmpty()) {
            hints.add("自动通过条件可能匹配");
        }
        if (!hints.isEmpty()) {
            analysis.put("hints", hints);
        }

        try {
            return objectMapper.writeValueAsString(analysis);
        } catch (Exception e) {
            return "{}";
        }
    }

    /** 从 JSON 节点提取规则类型（优先 ruleType > category > keyword推断） */
    private String extractType(JsonNode m) {
        if (m.has("ruleType") && !m.get("ruleType").asText().isEmpty())
            return m.get("ruleType").asText();
        if (m.has("category") && !m.get("category").asText().isEmpty())
            return m.get("category").asText();
        // 从 keyword 内容推断
        String kw = m.has("keyword") ? m.get("keyword").asText() : "";
        if (kw.contains("投诉") || kw.contains("举报") || kw.contains("12315")
                || kw.contains("律师") || kw.contains("假货") || kw.contains("欺诈")
                || kw.contains("过敏") || kw.contains("受伤") || kw.contains("工商")
                || kw.contains("媒体") || kw.contains("曝光") || kw.contains("退款"))
            return "官方投诉"; // maps to escalation
        if (kw.contains("水果") || kw.contains("海鲜") || kw.contains("三文鱼")
                || kw.contains("蛋糕") || kw.contains("内裤") || kw.contains("文胸")
                || kw.contains("内衣") || kw.contains("定制") || kw.contains("化妆品"))
            return "生鲜易腐商品"; // maps to strict_reject
        if (kw.contains("不想要") || kw.contains("质量") || kw.contains("描述")
                || kw.contains("物流") || kw.contains("损坏") || kw.contains("7天"))
            return "AA-001"; // maps to auto_approve
        return "";
    }

    /** 将 rules.json 中的 category/ruleType 映射到标准三大类 */
    private String mapRuleType(String raw) {
        if (raw == null || raw.isEmpty()) return "unknown";
        // escalationKeywords 的 category 值
        if (raw.contains("投诉") || raw.contains("法律") || raw.contains("安全")
                || raw.contains("行政") || raw.contains("舆情") || raw.contains("金额")
                || raw.contains("质量") && raw.contains("严重")) return "escalation";
        // strictRejectRules 的 category 值
        if (raw.contains("生鲜") || raw.contains("数字") || raw.contains("卫生")
                || raw.contains("定制") || raw.contains("消耗") || raw.contains("限时")
                || raw.contains("抢购")) return "strict_reject";
        // autoApproveRules 的 ruleId
        if (raw.startsWith("AA-")) return "auto_approve";
        // 英文值
        String lower = raw.toLowerCase();
        if (lower.contains("escalation")) return "escalation";
        if (lower.contains("strict") || lower.contains("reject")) return "strict_reject";
        if (lower.contains("auto") || lower.contains("approve")) return "auto_approve";
        // 从 keyword 内容推断
        if (raw.contains("投诉") || raw.contains("律师") || raw.contains("12315")
                || raw.contains("假货") || raw.contains("欺诈") || raw.contains("过敏")
                || raw.contains("受伤") || raw.contains("工商") || raw.contains("媒体")
                || raw.contains("曝光") || raw.contains("退款")) return "escalation";
        if (raw.contains("不想要") || raw.contains("质量") || raw.contains("描述")
                || raw.contains("物流")) return "auto_approve";
        return "unknown";
    }

    // ==================== 独立 Agent 构建（保留） ====================

    /**
     * 构建 ReturnRuleAgent — 语义规则判定专家
     * 注册 hybridKeywordSearch + exactMatchKeywords 两个 Tool
     */
    public ReactAgent buildReturnRuleAgent(DashScopeChatModel chatModel, ToolCallback[] toolCallbacks) {
        return ReactAgent.builder()
                .name("rule_judge_agent")
                .description("规则判定专家：综合混合检索结果判断是否触发升级/拒绝/通过规则")
                .model(chatModel)
                .systemPrompt(buildRuleJudgePrompt())
                .methodTools(new Object[]{hybridSearchTools, exactMatchTools})
                .outputKey("rule_judge_result")
                .build();
    }

    private String buildRuleJudgePrompt() {
        return """
                你是退货规则判定专家。你的职责是根据混合检索召回的候选规则，判断当前退货申请是否触发特定规则。

                ## 判定的规则类型
                1. **escalation（升级转人工）**：投诉/12315/律师/假货/欺诈/过敏/受伤/工商/媒体/曝光
                2. **strict_reject（严格拒绝）**：生鲜/数字商品/内衣/定制/消耗品/限时商品
                3. **auto_approve（自动通过）**：七天无理由/质量问题/描述不符/物流损坏

                ## 步骤（严格按顺序）

                ### Step 1: 调用 hybridKeywordSearch(text) 获取混合检索结果
                参数 text = 退货原因 + 退货描述（合并为一段文本）
                你会得到候选规则列表，每个包含：keyword、source(vector/exact/synonym/fuzzy)、score

                ### Step 2: 调用 exactMatchKeywords(text) 确认实体词
                用于确认精确实体（12315/消协/工商/律师）是否命中

                ### Step 3: 综合判定（这是最关键的一步）

                **语境区分规则：**
                - "我要投诉你们" → 真投诉，触发 escalation
                - "投诉流程怎么走？"/"投诉需要什么材料？" → 咨询，不触发
                - "之前投诉过现在好了" → 历史陈述，不触发
                - "这个质量也太差了" → 情绪表达，不等于"质量问题"规则自动触发（需结合商品属性判断）

                **商品名 vs 比喻区分：**
                - "买了三文鱼刺身" → 真实生鲜商品，触发 strict_reject SR-001
                - "这是我的小心肝宝贝" → 比喻，不触发
                - "买的内衣" → 真实个人卫生产品，触发 SR-003
                - "这个像内衣一样贴身" → 比喻，不触发

                **得分阈值：**
                - score > 0.7 且语境确认真实意图 → 触发
                - score 0.4-0.7 → 可疑，标记但不触发，建议人工复核
                - score < 0.4 → 忽略

                ## 输出格式（严格 JSON，不要输出 Markdown）
                {
                  "triggered": true/false,
                  "ruleType": "escalation|strict_reject|auto_approve|none",
                  "matchedRuleId": "SR-001",
                  "severity": "CRITICAL|HIGH|MEDIUM|LOW|NONE",
                  "reasoning": "简要的推理过程，1-2句话",
                  "recommendation": "建议的后续动作",
                  "matchedKeywords": [
                    {"keyword": "投诉", "source": "vector", "score": 0.85, "triggered": true}
                  ],
                  "needHumanReview": false
                }
                """;
    }
}
