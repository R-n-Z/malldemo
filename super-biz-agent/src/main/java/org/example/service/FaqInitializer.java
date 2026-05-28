package org.example.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.MutationResult;
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
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
public class FaqInitializer {

    private static final Logger logger = LoggerFactory.getLogger(FaqInitializer.class);
    private static final String FAQ_COLLECTION = "faq";

    @Autowired
    private MilvusServiceClient milvusClient;

    @Autowired
    private VectorEmbeddingService embeddingService;

    private final Gson gson = new Gson();

    @PostConstruct
    public void init() {
        try {
            boolean existed = collectionExists();
            if (!existed) {
                createCollection();
                insertFaqData();
                createIndex();
                loadCollection();
                logger.info("FAQ 知识库初始化完成（新建）");
            } else {
                // 集合已存在，检查是否已加载到内存
                try {
                    R<RpcStatus> loadResp = milvusClient.loadCollection(
                        LoadCollectionParam.newBuilder()
                            .withCollectionName(FAQ_COLLECTION).build());
                    if (loadResp.getStatus() == 0) {
                        logger.info("FAQ collection 已存在且加载成功");
                    }
                } catch (Exception e) {
                    logger.warn("FAQ 加载警告: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.error("FAQ 初始化失败（不影响其他功能）", e);
        }
    }

    private boolean collectionExists() {
        try {
            R<ShowCollectionsResponse> resp = milvusClient.showCollections(
                    ShowCollectionsParam.newBuilder().build());
            if (resp.getStatus() == 0 && resp.getData() != null) {
                return resp.getData().getCollectionNamesList().stream()
                        .anyMatch(n -> n.equals(FAQ_COLLECTION));
            }
        } catch (Exception ignored) {}
        return false;
    }

    private void createCollection() {
        FieldType idField = FieldType.newBuilder()
                .withName("id").withDataType(io.milvus.grpc.DataType.VarChar)
                .withMaxLength(256).withPrimaryKey(true).withAutoID(false).build();
        FieldType contentField = FieldType.newBuilder()
                .withName("content").withDataType(io.milvus.grpc.DataType.VarChar)
                .withMaxLength(MilvusConstants.CONTENT_MAX_LENGTH).build();
        FieldType vectorField = FieldType.newBuilder()
                .withName("vector").withDataType(io.milvus.grpc.DataType.FloatVector)
                .withDimension(MilvusConstants.VECTOR_DIM).build();
        FieldType metaField = FieldType.newBuilder()
                .withName("metadata").withDataType(io.milvus.grpc.DataType.JSON).build();

        CreateCollectionParam param = CreateCollectionParam.newBuilder()
                .withCollectionName(FAQ_COLLECTION)
                .withDescription("客服FAQ常见问题知识库")
                .withShardsNum(MilvusConstants.DEFAULT_SHARD_NUMBER)
                .addFieldType(idField).addFieldType(contentField)
                .addFieldType(vectorField).addFieldType(metaField).build();

        R<RpcStatus> resp = milvusClient.createCollection(param);
        if (resp.getStatus() != 0) {
            throw new RuntimeException("创建FAQ collection失败: " + resp.getMessage());
        }
        logger.info("FAQ collection 创建成功");
    }

    private void createIndex() {
        CreateIndexParam param = CreateIndexParam.newBuilder()
                .withCollectionName(FAQ_COLLECTION)
                .withFieldName("vector")
                .withIndexType(io.milvus.param.IndexType.HNSW)
                .withMetricType(io.milvus.param.MetricType.L2)
                .withExtraParam("{\"M\":16,\"efConstruction\":200}")
                .build();
        R<RpcStatus> resp = milvusClient.createIndex(param);
        if (resp.getStatus() != 0) {
            logger.warn("创建FAQ索引警告: {}", resp.getMessage());
        }
    }

    private void loadCollection() {
        milvusClient.loadCollection(LoadCollectionParam.newBuilder()
                .withCollectionName(FAQ_COLLECTION).build());
    }

    private void insertFaqData() {
        List<FaqItem> faqs = buildFaqList();
        logger.info("开始插入 {} 条FAQ数据...", faqs.size());

        for (FaqItem item : faqs) {
            try {
                List<Float> vector = embeddingService.generateEmbedding(item.question);
                List<InsertParam.Field> fields = new ArrayList<>();
                fields.add(new InsertParam.Field("id", Collections.singletonList(
                        UUID.nameUUIDFromBytes(("faq_" + item.id).getBytes()).toString())));
                fields.add(new InsertParam.Field("content", Collections.singletonList(item.question)));
                fields.add(new InsertParam.Field("vector", Collections.singletonList(vector)));

                Map<String, String> meta = new LinkedHashMap<>();
                meta.put("answer", item.answer);
                meta.put("category", item.category);
                JsonObject metaJson = gson.toJsonTree(meta).getAsJsonObject();
                fields.add(new InsertParam.Field("metadata", Collections.singletonList(metaJson)));

                InsertParam insertParam = InsertParam.newBuilder()
                        .withCollectionName(FAQ_COLLECTION).withFields(fields).build();
                milvusClient.insert(insertParam);
            } catch (Exception e) {
                logger.warn("FAQ插入失败: {} - {}", item.question, e.getMessage());
            }
        }
        logger.info("FAQ数据写入完成");
    }

    private List<FaqItem> buildFaqList() {
        List<FaqItem> list = new ArrayList<>();

        // === 从 faq/product_faq.json 加载商品FAQ ===
        int idCounter = 1;
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("faq/product_faq.json")) {
            if (is != null) {
                com.google.gson.reflect.TypeToken<List<JsonObject>> typeToken =
                    new com.google.gson.reflect.TypeToken<List<JsonObject>>() {};
                List<JsonObject> faqJsonList = gson.fromJson(new InputStreamReader(is, StandardCharsets.UTF_8), typeToken);
                if (faqJsonList != null) {
                    for (JsonObject entry : faqJsonList) {
                        String question = entry.get("question").getAsString();
                        String answer = entry.get("answer").getAsString();
                        String category = entry.has("category") ? entry.get("category").getAsString() : "通用";
                        list.add(faq(idCounter++, category, question, answer));
                    }
                    logger.info("从 product_faq.json 加载了 {} 条商品FAQ", faqJsonList.size());
                }
            } else {
                logger.warn("faq/product_faq.json 未找到，使用内置默认FAQ");
            }
        } catch (Exception e) {
            logger.warn("加载 product_faq.json 失败，使用内置默认FAQ: {}", e.getMessage());
        }

        // 如果 JSON 加载失败，回退到内置FAQ
        if (list.isEmpty()) {
            list.add(faq(idCounter++, "物流", "商品什么时候发货？", "付款后48小时内发货，节假日顺延。下单后会短信通知您物流单号。"));
            list.add(faq(idCounter++, "支付", "支持哪些支付方式？", "支持支付宝、微信支付、银行卡支付。"));
            list.add(faq(idCounter++, "退换货", "退换货流程是什么？", "在订单详情页申请退货/换货 → 填写原因 → 等待审核 → 寄回商品 → 退款/换货。"));
            list.add(faq(idCounter++, "售后", "如何联系客服？", "在商品详情页点击「客服」按钮即可在线咨询。"));
        }

        return list;
    }

    private FaqItem faq(int id, String category, String question, String answer) {
        FaqItem item = new FaqItem();
        item.id = id;
        item.category = category;
        item.question = question;
        item.answer = answer;
        return item;
    }

    private static class FaqItem {
        int id;
        String category;
        String question;
        String answer;
    }
}
