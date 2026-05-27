package org.example.agent.tool.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 退货审核规则知识库工具
 * 加载 audit/rules.json 提供三类规则查询：自动通过、转人工关键词、严格拒绝
 */
@Component
public class ReturnRuleKnowledgeTools {

    private static final Logger logger = LoggerFactory.getLogger(ReturnRuleKnowledgeTools.class);

    public static final String TOOL_QUERY_AUTO_APPROVE_RULES = "queryAutoApproveRules";
    public static final String TOOL_CHECK_ESCALATION_KEYWORDS = "checkEscalationKeywords";
    public static final String TOOL_QUERY_STRICT_REJECT_RULES = "queryStrictRejectRules";
    public static final String TOOL_QUERY_RECEIPT_THRESHOLDS = "queryReceiptThresholds";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private JsonNode rulesRoot;

    @PostConstruct
    public void init() {
        try {
            ClassPathResource resource = new ClassPathResource("audit/rules.json");
            String content = resource.getContentAsString(StandardCharsets.UTF_8);
            rulesRoot = objectMapper.readTree(content);
            logger.info("退货审核规则知识库加载成功, 版本: {}",
                    rulesRoot.has("version") ? rulesRoot.get("version").asText() : "unknown");
        } catch (Exception e) {
            logger.error("退货审核规则知识库加载失败", e);
            rulesRoot = null;
        }
    }

    @Tool(name = TOOL_QUERY_AUTO_APPROVE_RULES,
          description = "查询自动通过退货的规则条件。返回允许自动通过的退货原因列表、收货天数上限、必要条件")
    public String queryAutoApproveRules() {
        if (rulesRoot == null) return "{\"error\": \"规则知识库未加载\"}";
        try {
            JsonNode rules = rulesRoot.get("autoApproveRules");
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(rules);
        } catch (Exception e) {
            return "{\"error\": \"查询自动通过规则失败: " + e.getMessage() + "\"}";
        }
    }

    @Tool(name = TOOL_CHECK_ESCALATION_KEYWORDS,
          description = "检查退货申请中是否包含需要转人工处理的关键词。输入退货原因和描述，返回命中的关键词及处理建议")
    public String checkEscalationKeywords(
            @ToolParam(description = "退货原因") String reason,
            @ToolParam(description = "退货描述") String description) {
        if (rulesRoot == null) return "{\"error\": \"规则知识库未加载\"}";
        try {
            String combined = (reason + " " + (description != null ? description : "")).toLowerCase();
            JsonNode keywords = rulesRoot.get("escalationKeywords");
            List<JsonNode> matches = new ArrayList<>();

            for (JsonNode kw : keywords) {
                String word = kw.get("keyword").asText().toLowerCase();
                if (combined.contains(word)) {
                    matches.add(kw);
                }
            }

            if (matches.isEmpty()) {
                return "{\"escalationNeeded\": false, \"message\": \"未命中转人工关键词\", \"matches\": []}";
            }

            return String.format(
                    "{\"escalationNeeded\": true, \"message\": \"命中%d个转人工关键词\", \"matches\": %s}",
                    matches.size(), objectMapper.writeValueAsString(matches));
        } catch (Exception e) {
            return "{\"error\": \"关键词检查失败: " + e.getMessage() + "\"}";
        }
    }

    @Tool(name = TOOL_QUERY_STRICT_REJECT_RULES,
          description = "查询严格不允许退货的商品规则。输入商品名称和属性，返回匹配的拒绝规则")
    public String queryStrictRejectRules(
            @ToolParam(description = "商品名称") String productName,
            @ToolParam(description = "商品属性") String productAttr) {
        if (rulesRoot == null) return "{\"error\": \"规则知识库未加载\"}";
        try {
            String combined = ((productName != null ? productName : "") + " "
                    + (productAttr != null ? productAttr : "")).toLowerCase();
            JsonNode rules = rulesRoot.get("strictRejectRules");
            List<JsonNode> matches = new ArrayList<>();

            for (JsonNode rule : rules) {
                JsonNode keywords = rule.get("productKeywords");
                if (keywords != null) {
                    for (JsonNode kw : keywords) {
                        if (combined.contains(kw.asText().toLowerCase())) {
                            matches.add(rule);
                            break;
                        }
                    }
                }
            }

            if (matches.isEmpty()) {
                return "{\"strictRejectMatched\": false, \"message\": \"未命中严格拒绝规则\", \"rules\": []}";
            }

            return String.format(
                    "{\"strictRejectMatched\": true, \"message\": \"命中%d条严格拒绝规则\", \"rules\": %s}",
                    matches.size(), objectMapper.writeValueAsString(matches));
        } catch (Exception e) {
            return "{\"error\": \"查询严格拒绝规则失败: " + e.getMessage() + "\"}";
        }
    }

    @Tool(name = TOOL_QUERY_RECEIPT_THRESHOLDS,
          description = "查询收货天数阈值规则。返回允许无理由退货的天数、有条件退货的天数范围、严格审查和直接拒绝的天数阈值")
    public String queryReceiptThresholds() {
        if (rulesRoot == null) return "{\"error\": \"规则知识库未加载\"}";
        try {
            JsonNode thresholds = rulesRoot.get("receiptDayThresholds");
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(thresholds);
        } catch (Exception e) {
            return "{\"error\": \"查询时间阈值失败: " + e.getMessage() + "\"}";
        }
    }
}
