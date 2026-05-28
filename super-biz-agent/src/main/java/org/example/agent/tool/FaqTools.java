package org.example.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.SearchResults;
import io.milvus.param.R;
import io.milvus.param.dml.SearchParam;
import io.milvus.response.SearchResultsWrapper;
import org.example.service.VectorEmbeddingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 客服 FAQ 知识库工具
 * 使用 Milvus 向量搜索从常见问题库中检索匹配的答案
 */
@Component
public class FaqTools {

    private static final Logger logger = LoggerFactory.getLogger(FaqTools.class);

    public static final String FAQ_COLLECTION = "faq";

    @Autowired
    private MilvusServiceClient milvusClient;

    @Autowired
    private VectorEmbeddingService embeddingService;

    @Value("${rag.top-k:3}")
    private int topK;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 搜索客服常见问题知识库
     * @param query 用户的问题
     * @return JSON 格式的匹配结果，包含 question、answer、score
     */
    @Tool(name = "searchFaq", description = "搜索客服常见问题知识库(FAQ)。当用户询问商品、物流、退换货、支付、售后等常见问题时使用此工具。" +
            "返回最匹配的问题和答案。如果匹配分数低于阈值，表示知识库中没有相关答案，需要人工客服处理。")
    public String searchFaq(
            @ToolParam(description = "用户提出的问题") String query) {

        try {
            logger.info("[FaqTools] 搜索FAQ: {}", query);

            // 1. 生成查询向量
            List<Float> queryVector = embeddingService.generateQueryVector(query);

            // 2. 在 faq collection 中搜索
            SearchParam searchParam = SearchParam.newBuilder()
                    .withCollectionName(FAQ_COLLECTION)
                    .withVectorFieldName("vector")
                    .withVectors(Collections.singletonList(queryVector))
                    .withTopK(topK)
                    .withMetricType(io.milvus.param.MetricType.L2)
                    .withOutFields(List.of("id", "content", "metadata"))
                    .withParams("{\"ef\":64}")
                    .build();

            R<SearchResults> searchResponse = milvusClient.search(searchParam);

            if (searchResponse.getStatus() != 0) {
                return "{\"status\":\"error\",\"message\":\"FAQ搜索失败\"}";
            }

            // 3. 解析结果
            SearchResultsWrapper wrapper = new SearchResultsWrapper(searchResponse.getData().getResults());
            List<Map<String, Object>> results = new ArrayList<>();

            for (int i = 0; i < wrapper.getRowRecords(0).size(); i++) {
                float score = wrapper.getIDScore(0).get(i).getScore();
                String content = (String) wrapper.getFieldData("content", 0).get(i);
                Object metaObj = wrapper.getFieldData("metadata", 0).get(i);

                Map<String, Object> item = new LinkedHashMap<>();
                item.put("question", content);
                item.put("score", score);

                // 从 metadata 中提取 answer 和 category
                if (metaObj != null) {
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> meta = objectMapper.readValue(metaObj.toString(), Map.class);
                        item.put("answer", meta.getOrDefault("answer", "暂无答案"));
                        item.put("category", meta.getOrDefault("category", ""));
                    } catch (Exception ignored) {}
                }
                results.add(item);
            }

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", "success");
            response.put("query", query);
            response.put("results", results);

            logger.info("[FaqTools] 找到 {} 个匹配", results.size());
            return objectMapper.writeValueAsString(response);

        } catch (Exception e) {
            logger.error("[FaqTools] 搜索失败", e);
            return "{\"status\":\"error\",\"message\":\"" + e.getMessage() + "\"}";
        }
    }
}
