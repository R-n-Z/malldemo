package org.example.agent.tool.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.SearchResults;
import io.milvus.param.R;
import io.milvus.param.dml.SearchParam;
import io.milvus.response.SearchResultsWrapper;
import org.example.config.HybridSearchProperties;
import org.example.constant.MilvusConstants;
import org.example.service.VectorEmbeddingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 混合检索工具 — 向量语义路 + 精确路双路并行，合并排序
 */
@Component
public class HybridSearchTools {

    private static final Logger logger = LoggerFactory.getLogger(HybridSearchTools.class);

    public static final String TOOL_HYBRID_KEYWORD_SEARCH = "hybridKeywordSearch";

    @Autowired
    private MilvusServiceClient milvusClient;

    @Autowired
    private VectorEmbeddingService embeddingService;

    @Autowired
    private ExactMatchTools exactMatchTools;

    @Autowired
    private HybridSearchProperties hybridProps;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Tool(name = TOOL_HYBRID_KEYWORD_SEARCH,
          description = "混合检索规则关键词。同时执行向量语义搜索和精确关键词匹配，合并去重后按得分排序返回。输入一段文本，返回命中的规则关键词")
    public String hybridKeywordSearch(
            @ToolParam(description = "需要检查的文本") String text) {
        return hybridKeywordSearch(text, hybridProps.getVectorWeight(),
                hybridProps.getKeywordWeight(), hybridProps.getVectorThreshold(),
                hybridProps.getVectorTopK());
    }

    /**
     * 混合检索（运行时权重覆盖）— 供 /rag-test/hybrid/search 权重扫描使用
     */
    public String hybridKeywordSearch(String text, double vw, double kw, double vt, int vtk) {
        List<Map<String, Object>> allMatches = new ArrayList<>();

        // 路径1：向量语义检索
        try {
            allMatches.addAll(vectorSearch(text, vw, vt, vtk));
        } catch (Exception e) {
            logger.warn("向量检索失败，降级为仅精确匹配: {}", e.getMessage());
        }

        // 路径2：精确关键词匹配（含同义词+模糊）
        try {
            String exactResult = exactMatchTools.exactMatchKeywords(text);
            JsonNode root = objectMapper.readTree(exactResult);
            if (root.has("matches")) {
                for (JsonNode m : root.get("matches")) {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("keyword", m.has("keyword") ? m.get("keyword").asText() : "");
                    entry.put("source", m.has("source") ? m.get("source").asText() : "exact");
                    entry.put("score", m.has("score") ? m.get("score").asDouble() * kw : 0.0);
                    if (m.has("category")) entry.put("category", m.get("category").asText());
                    if (m.has("priority")) entry.put("priority", m.get("priority").asText());
                    if (m.has("ruleId")) entry.put("ruleId", m.get("ruleId").asText());
                    allMatches.add(entry);
                }
            }
        } catch (Exception e) {
            logger.warn("精确匹配失败: {}", e.getMessage());
        }

        return mergeAndSort(allMatches);
    }

    private String mergeAndSort(List<Map<String, Object>> allMatches) {
        Map<String, Map<String, Object>> merged = new LinkedHashMap<>();
        for (Map<String, Object> m : allMatches) {
            String kw = (String) m.get("keyword");
            double score = ((Number) m.get("score")).doubleValue();
            if (!merged.containsKey(kw) || ((Number) merged.get(kw).get("score")).doubleValue() < score) {
                merged.put(kw, m);
            }
        }

        List<Map<String, Object>> sorted = new ArrayList<>(merged.values());
        sorted.sort((a, b) -> Double.compare(
                ((Number) b.get("score")).doubleValue(),
                ((Number) a.get("score")).doubleValue()));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalMatches", sorted.size());
        result.put("matches", sorted);
        try { return objectMapper.writeValueAsString(result); }
        catch (Exception e) { return "{\"matches\": []}"; }
    }

    private List<Map<String, Object>> vectorSearch(String text) throws Exception {
        return vectorSearch(text, hybridProps.getVectorWeight(),
                hybridProps.getVectorThreshold(), hybridProps.getVectorTopK());
    }

    private List<Map<String, Object>> vectorSearch(String text, double vw, double vt, int vtk) throws Exception {
        List<Float> queryVector = embeddingService.generateQueryVector(text);

        SearchParam param = SearchParam.newBuilder()
                .withCollectionName(MilvusConstants.RULE_KEYWORDS_COLLECTION)
                .withVectorFieldName("vector")
                .withVectors(Collections.singletonList(queryVector))
                .withOutFields(Arrays.asList("content", "metadata"))
                .withTopK(vtk)
                .withMetricType(io.milvus.param.MetricType.L2)
                .withParams(String.format("{\"ef\":%d}", MilvusConstants.HNSW_EF)).build();

        R<SearchResults> resp = milvusClient.search(param);
        if (resp.getStatus() != 0) return Collections.emptyList();

        SearchResultsWrapper wrapper = new SearchResultsWrapper(resp.getData().getResults());
        List<Map<String, Object>> results = new ArrayList<>();

        for (int i = 0; i < wrapper.getRowRecords(0).size(); i++) {
            float l2Score = wrapper.getIDScore(0).get(i).getScore();
            double similarity = 1.0 - Math.min(l2Score, 1.0);
            if (similarity < vt) continue;

            String content = (String) wrapper.getFieldData("content", 0).get(i);
            Object metaRaw = wrapper.getFieldData("metadata", 0).get(i);

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("keyword", content);
            entry.put("source", "vector");
            entry.put("score", similarity * vw);
            entry.put("l2Distance", (double) l2Score);

            if (metaRaw instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> meta = (Map<String, Object>) metaRaw;
                entry.put("ruleType", meta.getOrDefault("ruleType", ""));
                entry.put("ruleId", meta.getOrDefault("ruleId", ""));
            }
            results.add(entry);
        }
        return results;
    }
}
