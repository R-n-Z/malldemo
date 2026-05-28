# RAG 检索性能测试

验证项目中RAG系统的检索性能：**召回率(Recall)**、**准确率(Precision)**、**MRR**、**Hit Rate**。

## 目录结构

```
rag-test/
├── README.md                          # 本文件
├── datasets/                          # 测试数据集（标注真值）
│   ├── faq_test_cases.json           # FAQ检索测试用例(20条)
│   └── rule_test_cases.json          # 规则关键词测试用例(25条)
├── scripts/                           # 测试脚本
│   ├── RagPerformanceTest.java       # Java集成测试（Spring Boot Test）
│   ├── run_faq_test.py               # Python FAQ测试运行器（HTTP调用）
│   ├── evaluate.py                   # Python评估脚本（指标计算+报告生成）
│   └── run_all.sh                    # 一键运行脚本
└── results/                           # 测试结果（自动生成）
    └── .gitkeep
```

## 测试范围

### 1. FAQ 向量检索 (FaqTools.searchFaq)
- **检索方式**: 纯向量语义搜索 (Milvus HNSW, L2度量)
- **Collection**: `faq` (1024维, text-embedding-v4)
- **测试用例**: 20条 (精确匹配/语义相似/商品查询/无关查询)
- **指标**: Recall@K, Precision@K, MRR@K, Hit@K, F1@K

### 2. 精确关键词匹配 (ExactMatchTools.exactMatchKeywords)
- **检索方式**: 三层漏斗 (精确子串→同义词词典→编辑距离)
- **数据源**: `audit/rules.json` (内存索引)
- **测试用例**: 25条 (精确/同义词/模糊/混合场景)
- **指标**: Recall@K, Precision@K, MRR@K, Hit@K

### 3. 混合检索 (HybridSearchTools.hybridKeywordSearch)
- **检索方式**: 向量语义 + 精确匹配双路并行→合并去重→按得分排序
- **Collection**: `rule_keywords` (1024维, IVF_FLAT)
- **配置**: vectorTopK=5, vectorThreshold=0.4, keywordWeight=1.0, vectorWeight=0.8
- **测试用例**: 25条（与精确匹配相同数据集，便于对比）
- **指标**: Recall@K, Precision@K, MRR@K, Hit@K, F1@K

## 运行方式

### 方式一: Java 集成测试（推荐，最准确）

```bash
# 1. 确保服务已启动
#    - Milvus (端口 19530)
#    - DashScope API Key 已配置

# 2. 运行测试
cd super-biz-agent
mvn test -Dtest=RagPerformanceTest -DfailIfNoTests=false

# 3. 评估结果
cd rag-test/scripts
python evaluate.py \
  --dataset ../datasets/faq_test_cases.json \
  --results ../results/faq_results_latest.json

python evaluate.py \
  --dataset ../datasets/rule_test_cases.json \
  --results ../results/hybrid_results_latest.json
```

### 方式二: Python HTTP 测试（不需要Maven）

```bash
# 1. 确保 super-biz-agent 已启动 (端口 9900)

# 2. 运行FAQ检索测试
cd rag-test/scripts
python run_faq_test.py --base-url http://localhost:9900

# 3. 评估结果
python evaluate.py \
  --dataset ../datasets/faq_test_cases.json \
  --results ../results/faq_results.json
```

### 方式三: 一键运行

```bash
cd rag-test/scripts
bash run_all.sh
```

## 评估指标说明

| 指标 | 公式 | 含义 |
|------|------|------|
| **Recall@K** | 前K个结果中相关文档数 / 总相关文档数 | 检索系统找到相关文档的能力 |
| **Precision@K** | 前K个结果中相关文档数 / K | 检索结果中相关文档的占比 |
| **Hit@K** | 前K个结果中至少命中1条相关文档的查询比例 | 系统"命中"的概率 |
| **MRR@K** | 第一个相关文档排名倒数的平均值 | 找到的第一个相关文档的排名质量 |
| **F1@K** | 2*R*P/(R+P) | 召回和精确的调和平均 |

## 相关性判断规则

- **FAQ检索**: 返回结果的问题文本包含期望的问题关键词 → 相关
- **规则关键词**: 返回结果中 keyword 或 ruleType 与期望匹配 → 相关
- **得分阈值**: score >= 0.4 (混合检索) / L2 score < 0.5 (FAQ检索)

## 测试用例设计

测试用例覆盖以下维度:
- **精确匹配**: 查询与知识库内容完全一致
- **语义相似**: 语义等价但措辞不同（口语化/同义词改写）
- **同义词匹配**: 使用同义词词典中的变体（精确路第二层）
- **模糊匹配**: 包含错别字或拼写变形（精确路第三层）
- **混合场景**: 同一文本命中多个规则类型
- **无关查询**: 与电商/退货完全无关的文本
- **边界测试**: 低优先级但仍应被检索到的场景

## 结果解读

```bash
# 查看最新结果
ls -la rag-test/results/

# 对比精确匹配 vs 混合检索
python evaluate.py --dataset ../datasets/rule_test_cases.json \
  --results ../results/exact_results_latest.json

python evaluate.py --dataset ../datasets/rule_test_cases.json \
  --results ../results/hybrid_results_latest.json
```

两者的对比能揭示: 哪些场景向量语义能补充精确匹配的盲区（如同义表达但不同词汇），哪些场景精确匹配不可替代（如专有名词"12315"）。
