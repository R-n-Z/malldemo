package org.example.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.DataType;
import io.milvus.grpc.ShowCollectionsResponse;
import io.milvus.param.R;
import io.milvus.param.RpcStatus;
import io.milvus.param.collection.*;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.index.CreateIndexParam;
import jakarta.annotation.PostConstruct;
import org.example.constant.MilvusConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 规则关键词向量库初始化器
 * 从 audit/rules.json 提取关键词+同义词→向量化→写入 Milvus rule_keywords collection
 */
@Component
public class RuleKeywordInitializer {

    private static final Logger logger = LoggerFactory.getLogger(RuleKeywordInitializer.class);
    private static final String COLLECTION = MilvusConstants.RULE_KEYWORDS_COLLECTION;

    @Autowired
    private MilvusServiceClient milvusClient;

    @Autowired
    private VectorEmbeddingService embeddingService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        try {
            if (collectionExists()) {
                logger.info("rule_keywords collection 已存在，跳过初始化");
                return;
            }
            createCollection();
            insertKeywords();
            createIndex();
            loadCollection();
            logger.info("rule_keywords 向量库初始化完成");
        } catch (Exception e) {
            logger.error("rule_keywords 初始化失败（不影响精确匹配降级）", e);
        }
    }

    private boolean collectionExists() {
        try {
            R<ShowCollectionsResponse> resp = milvusClient.showCollections(
                    ShowCollectionsParam.newBuilder().build());
            if (resp.getStatus() == 0 && resp.getData() != null) {
                return resp.getData().getCollectionNamesList().stream()
                        .anyMatch(n -> n.equals(COLLECTION));
            }
        } catch (Exception ignored) {}
        return false;
    }

    private void createCollection() {
        FieldType idField = FieldType.newBuilder()
                .withName("id").withDataType(DataType.VarChar)
                .withMaxLength(256).withPrimaryKey(true).withAutoID(false).build();
        FieldType contentField = FieldType.newBuilder()
                .withName("content").withDataType(DataType.VarChar)
                .withMaxLength(1024).build();
        FieldType vectorField = FieldType.newBuilder()
                .withName("vector").withDataType(DataType.FloatVector)
                .withDimension(MilvusConstants.VECTOR_DIM).build();
        FieldType metaField = FieldType.newBuilder()
                .withName("metadata").withDataType(DataType.JSON).build();

        CreateCollectionParam param = CreateCollectionParam.newBuilder()
                .withCollectionName(COLLECTION)
                .withDescription("退货审核规则关键词向量库—用于RAG语义召回")
                .withShardsNum(1)
                .addFieldType(idField).addFieldType(contentField)
                .addFieldType(vectorField).addFieldType(metaField).build();
        milvusClient.createCollection(param);
        logger.info("rule_keywords collection 创建成功");
    }

    private void createIndex() {
        CreateIndexParam param = CreateIndexParam.newBuilder()
                .withCollectionName(COLLECTION).withFieldName("vector")
                .withIndexType(io.milvus.param.IndexType.IVF_FLAT)
                .withMetricType(io.milvus.param.MetricType.L2)
                .withExtraParam("{\"nlist\":128}").build();
        milvusClient.createIndex(param);
    }

    private void loadCollection() {
        milvusClient.loadCollection(LoadCollectionParam.newBuilder()
                .withCollectionName(COLLECTION).build());
    }

    private void insertKeywords() throws Exception {
        ClassPathResource resource = new ClassPathResource("audit/rules.json");
        String content = resource.getContentAsString(StandardCharsets.UTF_8);
        JsonNode root = objectMapper.readTree(content);

        List<KeywordEntry> entries = new ArrayList<>();
        // 从 escalationKeywords 提取
        for (JsonNode kw : root.get("escalationKeywords")) {
            entries.add(buildEntry(kw, "escalation"));
        }
        // 从 strictRejectRules 提取 productKeywords
        for (JsonNode rule : root.get("strictRejectRules")) {
            for (JsonNode kw : rule.get("productKeywords")) {
                entries.add(buildEntry(kw, "strict_reject", rule.get("ruleId").asText()));
            }
        }
        // 从 autoApproveRules 提取 allowedReasons
        for (JsonNode rule : root.get("autoApproveRules")) {
            for (JsonNode reason : rule.get("allowedReasons")) {
                entries.add(buildEntry(reason, "auto_approve", rule.get("ruleId").asText()));
            }
        }

        logger.info("从 rules.json 提取 {} 个关键词，开始向量化...", entries.size());
        List<String> texts = entries.stream().map(e -> e.embedText).toList();
        List<List<Float>> vectors = embeddingService.generateEmbeddings(texts);

        List<InsertParam.Field> fields = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        List<String> contents = new ArrayList<>();
        List<List<Float>> vectorList = new ArrayList<>();
        List<Map<String, Object>> metas = new ArrayList<>();

        for (int i = 0; i < entries.size(); i++) {
            KeywordEntry e = entries.get(i);
            ids.add(UUID.nameUUIDFromBytes(("rule_kw_" + i).getBytes()).toString());
            contents.add(e.embedText);
            vectorList.add(vectors.get(i));
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("keyword", e.keyword);
            meta.put("ruleType", e.ruleType);
            meta.put("ruleId", e.ruleId);
            metas.add(meta);
        }

        fields.add(new InsertParam.Field("id", ids));
        fields.add(new InsertParam.Field("content", contents));
        fields.add(new InsertParam.Field("vector", vectorList));
        fields.add(new InsertParam.Field("metadata", metas));

        InsertParam insertParam = InsertParam.newBuilder()
                .withCollectionName(COLLECTION).withFields(fields).build();
        milvusClient.insert(insertParam);
        logger.info("成功写入 {} 个关键词向量", entries.size());
    }

    private KeywordEntry buildEntry(JsonNode node, String ruleType) {
        return buildEntry(node, ruleType, "");
    }

    private KeywordEntry buildEntry(JsonNode node, String ruleType, String ruleId) {
        KeywordEntry e = new KeywordEntry();
        e.keyword = node.isObject() ? node.get("keyword").asText() : node.asText();
        e.ruleType = ruleType;
        e.ruleId = ruleId.isEmpty() && node.isObject() && node.has("ruleId")
                ? node.get("ruleId").asText() : ruleId;

        // 构建嵌入文本：关键词 + 同义词 + 典型表达
        StringBuilder embedBuilder = new StringBuilder(e.keyword);
        if (node.isObject()) {
            appendArray(node, "synonyms", embedBuilder);
            appendArray(node, "examples", embedBuilder);
        }
        e.embedText = embedBuilder.toString();
        return e;
    }

    private void appendArray(JsonNode node, String field, StringBuilder sb) {
        if (node.has(field)) {
            for (JsonNode item : node.get(field)) {
                sb.append(" ").append(item.asText());
            }
        }
    }

    private static class KeywordEntry {
        String keyword;
        String ruleType;
        String ruleId;
        String embedText;
    }
}
