package org.example.ragtest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.agent.tool.FaqTools;
import org.example.agent.tool.audit.ExactMatchTools;
import org.example.agent.tool.audit.HybridSearchTools;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * RAG 检索性能集成测试
 *
 * 前置条件: Milvus 已启动, DashScope API Key 已配置, Spring 上下文可加载
 * 运行方式: mvn test -Dtest=RagPerformanceTest -pl super-biz-agent
 *
 * 测试覆盖:
 *   1. FAQ 向量检索 (FaqTools.searchFaq)
 *   2. 精确关键词匹配 (ExactMatchTools.exactMatchKeywords)
 *   3. 混合检索 (HybridSearchTools.hybridKeywordSearch)
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class RagPerformanceTest {

    private static final Logger log = LoggerFactory.getLogger(RagPerformanceTest.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String RESULTS_DIR = "rag-test/results/";
    private static final String DATASETS_DIR = "rag-test/datasets/";

    // 测试结果收集
    private static final Map<String, List<Map<String, Object>>> faqResults = new LinkedHashMap<>();
    private static final Map<String, List<Map<String, Object>>> hybridResults = new LinkedHashMap<>();
    private static final Map<String, List<Map<String, Object>>> exactResults = new LinkedHashMap<>();

    @Autowired
    private FaqTools faqTools;

    @Autowired
    private ExactMatchTools exactMatchTools;

    @Autowired
    private HybridSearchTools hybridSearchTools;

    private List<JsonNode> faqTestCases;
    private List<JsonNode> ruleTestCases;

    @BeforeEach
    void loadDatasets() throws Exception {
        if (faqTestCases != null) return; // 只加载一次

        Path faqPath = Paths.get(DATASETS_DIR, "faq_test_cases.json");
        Path rulePath = Paths.get(DATASETS_DIR, "rule_test_cases.json");

        if (Files.exists(faqPath)) {
            JsonNode faq = mapper.readTree(faqPath.toFile());
            faqTestCases = new ArrayList<>();
            faq.get("testCases").forEach(faqTestCases::add);
            log.info("加载FAQ测试用例: {} 条", faqTestCases.size());
        }

        if (Files.exists(rulePath)) {
            JsonNode rule = mapper.readTree(rulePath.toFile());
            ruleTestCases = new ArrayList<>();
            rule.get("testCases").forEach(ruleTestCases::add);
            log.info("加载规则测试用例: {} 条", ruleTestCases.size());
        }
    }

    // ======================== FAQ 向量检索测试 ========================

    @Test
    @Order(1)
    @DisplayName("FAQ向量检索 - 全量测试")
    void testFaqVectorSearch() throws Exception {
        assumeTrue(faqTestCases != null && !faqTestCases.isEmpty(), "FAQ测试数据集未找到");

        log.info("========== FAQ 向量检索测试开始 ==========");
        int total = faqTestCases.size();
        int success = 0;

        for (int i = 0; i < total; i++) {
            JsonNode tc = faqTestCases.get(i);
            String caseId = tc.get("id").asText();
            String query = tc.get("query").asText();

            try {
                String result = faqTools.searchFaq(query);
                JsonNode root = mapper.readTree(result);
                List<Map<String, Object>> items = new ArrayList<>();

                if (root.has("results")) {
                    for (JsonNode item : root.get("results")) {
                        Map<String, Object> entry = new LinkedHashMap<>();
                        entry.put("question", item.get("question").asText());
                        entry.put("score", item.get("score").asDouble());
                        if (item.has("category")) entry.put("category", item.get("category").asText());
                        items.add(entry);
                    }
                }

                faqResults.put(caseId, items);

                String expected = tc.has("expectedMatch") && !tc.get("expectedMatch").isNull()
                        ? tc.get("expectedMatch").asText() : null;
                boolean found = expected != null && items.stream().anyMatch(
                        it -> it.get("question").toString().contains(expected));

                log.info("[{}] '{}' -> {}条结果, 命中期望: {}",
                        caseId, query.substring(0, Math.min(40, query.length())),
                        items.size(), found);
                if (found) success++;
            } catch (Exception e) {
                log.error("[{}] 查询失败: {}", caseId, e.getMessage());
                faqResults.put(caseId, Collections.emptyList());
            }

            Thread.sleep(200); // 避免API限流
        }

        log.info("========== FAQ测试完成: {}/{} 命中期望 ==========", success, total);
    }

    // ======================== 精确关键词匹配测试 ========================

    @Test
    @Order(2)
    @DisplayName("精确关键词匹配 - 全量测试")
    void testExactKeywordMatch() throws Exception {
        assumeTrue(ruleTestCases != null && !ruleTestCases.isEmpty(), "规则测试数据集未找到");

        log.info("========== 精确关键词匹配测试开始 ==========");
        int total = ruleTestCases.size();

        for (int i = 0; i < total; i++) {
            JsonNode tc = ruleTestCases.get(i);
            String caseId = tc.get("id").asText();
            String text = tc.get("text").asText();

            try {
                String result = exactMatchTools.exactMatchKeywords(text);
                JsonNode root = mapper.readTree(result);
                List<Map<String, Object>> items = new ArrayList<>();

                if (root.has("matches")) {
                    for (JsonNode item : root.get("matches")) {
                        Map<String, Object> entry = new LinkedHashMap<>();
                        entry.put("keyword", item.get("keyword").asText());
                        entry.put("source", item.get("source").asText());
                        entry.put("score", item.get("score").asDouble());
                        if (item.has("category")) entry.put("category", item.get("category").asText());
                        if (item.has("priority")) entry.put("priority", item.get("priority").asText());
                        items.add(entry);
                    }
                }
                exactResults.put(caseId, items);
                log.info("[{}] 精确匹配 '{}' -> {}条: {}",
                        caseId, text.substring(0, Math.min(30, text.length())),
                        items.size(),
                        items.stream().map(m -> m.get("keyword")).toList());
            } catch (Exception e) {
                log.error("[{}] 精确匹配失败: {}", caseId, e.getMessage());
                exactResults.put(caseId, Collections.emptyList());
            }
        }

        log.info("========== 精确匹配测试完成 ==========");
    }

    // ======================== 混合检索测试 ========================

    @Test
    @Order(3)
    @DisplayName("混合检索(向量+精确) - 全量测试")
    void testHybridSearch() throws Exception {
        assumeTrue(ruleTestCases != null && !ruleTestCases.isEmpty(), "规则测试数据集未找到");

        log.info("========== 混合检索测试开始 ==========");
        int total = ruleTestCases.size();

        for (int i = 0; i < total; i++) {
            JsonNode tc = ruleTestCases.get(i);
            String caseId = tc.get("id").asText();
            String text = tc.get("text").asText();

            try {
                String result = hybridSearchTools.hybridKeywordSearch(text);
                JsonNode root = mapper.readTree(result);
                List<Map<String, Object>> items = new ArrayList<>();

                if (root.has("matches")) {
                    for (JsonNode item : root.get("matches")) {
                        Map<String, Object> entry = new LinkedHashMap<>();
                        entry.put("keyword", item.get("keyword").asText());
                        entry.put("source", item.get("source").asText());
                        entry.put("score", item.get("score").asDouble());
                        if (item.has("ruleType")) entry.put("ruleType", item.get("ruleType").asText());
                        if (item.has("ruleId")) entry.put("ruleId", item.get("ruleId").asText());
                        if (item.has("l2Distance")) entry.put("l2Distance", item.get("l2Distance").asDouble());
                        items.add(entry);
                    }
                }
                hybridResults.put(caseId, items);
                log.info("[{}] 混合检索 '{}' -> {}条: {}",
                        caseId, text.substring(0, Math.min(30, text.length())),
                        items.size(),
                        items.stream().map(m ->
                                m.get("keyword") + "(" + m.get("source") + ":" +
                                        String.format("%.2f", m.get("score")) + ")").toList());
            } catch (Exception e) {
                log.error("[{}] 混合检索失败: {}", caseId, e.getMessage());
                hybridResults.put(caseId, Collections.emptyList());
            }

            Thread.sleep(300); // 避免 Embedding API 限流
        }

        log.info("========== 混合检索测试完成 ==========");
    }

    // ======================== 结果保存 ========================

    @AfterAll
    static void saveResults() throws IOException {
        Files.createDirectories(Paths.get(RESULTS_DIR));
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

        // 保存 FAQ 结果
        if (!faqResults.isEmpty()) {
            Map<String, Object> faqOutput = new LinkedHashMap<>();
            faqOutput.put("testTime", ts);
            faqOutput.put("testType", "faq_vector_search");
            faqOutput.put("toolName", "FaqTools.searchFaq");
            faqOutput.put("totalQueries", faqResults.size());
            faqOutput.put("results", faqResults);
            writeJson(RESULTS_DIR + "faq_results_" + ts + ".json", faqOutput);
            writeJson(RESULTS_DIR + "faq_results_latest.json", faqOutput);
        }

        // 保存精确匹配结果
        if (!exactResults.isEmpty()) {
            Map<String, Object> exactOutput = new LinkedHashMap<>();
            exactOutput.put("testTime", ts);
            exactOutput.put("testType", "exact_keyword_match");
            exactOutput.put("toolName", "ExactMatchTools.exactMatchKeywords");
            exactOutput.put("totalQueries", exactResults.size());
            exactOutput.put("results", exactResults);
            writeJson(RESULTS_DIR + "exact_results_" + ts + ".json", exactOutput);
            writeJson(RESULTS_DIR + "exact_results_latest.json", exactOutput);
        }

        // 保存混合检索结果
        if (!hybridResults.isEmpty()) {
            Map<String, Object> hybridOutput = new LinkedHashMap<>();
            hybridOutput.put("testTime", ts);
            hybridOutput.put("testType", "hybrid_search");
            hybridOutput.put("toolName", "HybridSearchTools.hybridKeywordSearch");
            hybridOutput.put("config", Map.of(
                    "vectorTopK", 5, "vectorThreshold", 0.4,
                    "keywordWeight", 1.0, "vectorWeight", 0.8
            ));
            hybridOutput.put("totalQueries", hybridResults.size());
            hybridOutput.put("results", hybridResults);
            writeJson(RESULTS_DIR + "hybrid_results_" + ts + ".json", hybridOutput);
            writeJson(RESULTS_DIR + "hybrid_results_latest.json", hybridOutput);
        }

        // 打印摘要
        System.out.println("\n" + "=".repeat(60));
        System.out.println("  RAG 性能测试完成");
        System.out.println("=".repeat(60));
        System.out.println("FAQ向量检索:     " + faqResults.size() + " 条查询");
        System.out.println("精确关键词匹配:  " + exactResults.size() + " 条查询");
        System.out.println("混合检索:        " + hybridResults.size() + " 条查询");
        System.out.println("结果目录:        " + RESULTS_DIR);
        System.out.println("=".repeat(60));
    }

    private static void writeJson(String path, Object data) throws IOException {
        String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(data);
        try (FileWriter fw = new FileWriter(path)) {
            fw.write(json);
        }
        log.info("结果已保存: {}", path);
    }

    private static void assumeTrue(boolean condition, String message) {
        if (!condition) {
            log.warn("跳过测试: {}", message);
            throw new TestAbortedException(message);
        }
    }

    static class TestAbortedException extends RuntimeException {
        TestAbortedException(String msg) { super(msg); }
    }
}
