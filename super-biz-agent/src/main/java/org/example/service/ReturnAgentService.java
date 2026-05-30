package org.example.service;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import org.example.agent.tool.audit.DateTimeCalculateTools;
import org.example.agent.tool.audit.ExactMatchTools;
import org.example.agent.tool.audit.HybridSearchTools;
import org.example.agent.tool.audit.ReturnAuditTools;
import org.example.agent.tool.audit.ReturnOrderTools;
import org.example.agent.tool.audit.ReturnRuleKnowledgeTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 退货审核 Agent 服务 — 基于 ReAct 架构
 *
 * 架构思路：
 * - 单 ReactAgent 持有全部工具，自主完成 Observe→Decide→Act→Observe 循环
 * - 通过 prompt 内置短路逻辑和优先级框架，替代外部的 Plan-Execute 编排
 * - Plan-Execute 的设计思路已总结至 rag-test/docs/plan_execute_patterns.md
 */
@Service
public class ReturnAgentService {

    private static final Logger logger = LoggerFactory.getLogger(ReturnAgentService.class);

    @Autowired
    private ReturnOrderTools returnOrderTools;

    @Autowired
    private ReturnAuditTools returnAuditTools;

    @Autowired
    private DateTimeCalculateTools dateTimeCalculateTools;

    @Autowired
    private ReturnRuleKnowledgeTools returnRuleKnowledgeTools;

    @Autowired
    private ExactMatchTools exactMatchTools;

    @Autowired
    private HybridSearchTools hybridSearchTools;

    @Autowired
    private ReturnRuleAgentService returnRuleAgentService;

    /** 用户连续拒绝次数缓存 */
    private final ConcurrentHashMap<String, Integer> rejectionCountCache = new ConcurrentHashMap<>();

    // ==================== 主流程 ====================

    /**
     * 执行退货审核 — 单 ReactAgent ReAct 循环
     */
    public Optional<OverAllState> executeReturnAudit(DashScopeChatModel chatModel,
                                                      ToolCallback[] toolCallbacks,
                                                      Long applyId) throws GraphRunnerException {
        logger.info("[Audit-Start] applyId={}", applyId);
        long startTime = System.currentTimeMillis();

        ReactAgent auditAgent = ReactAgent.builder()
                .name("return_audit_agent")
                .description("退货审核专家：综合商品属性、收货时长、退货原因、用户历史判定退货申请")
                .model(chatModel)
                .systemPrompt(buildReActPrompt())
                .methodTools(new Object[]{returnOrderTools, returnAuditTools,
                        dateTimeCalculateTools, returnRuleKnowledgeTools,
                        exactMatchTools, hybridSearchTools, returnRuleAgentService})
                .outputKey("audit_result")
                .build();

        String taskPrompt = String.format(
                "请对退货申请 #%d 进行审核。先调用 getApplyDetail 了解情况，然后自主决定需要做哪些检查、以什么顺序做。",
                applyId);

        Optional<OverAllState> state = auditAgent.invoke(taskPrompt);
        long elapsed = System.currentTimeMillis() - startTime;

        // 性能指标采集
        logMetrics(applyId, state, elapsed);

        return state;
    }

    /** 采集并记录工具调用效率指标 */
    private void logMetrics(Long applyId, Optional<OverAllState> state, long elapsedMs) {
        if (state.isEmpty()) return;
        String report = extractAuditReport(state.get()).orElse("");

        // 统计报告中提到的维度检查
        int checkedDims = 0;
        int shortCircuited = 0;
        for (String dim : new String[]{"品类限制", "退货条件", "时间窗口", "升级风险", "用户历史"}) {
            if (report.contains(dim)) {
                if (report.contains("未检查") && report.contains(dim)) {
                    shortCircuited++;
                } else {
                    checkedDims++;
                }
            }
        }

        // 检测推断来源
        boolean usedFewShot = report.contains("[推断:语义映射]");
        boolean usedToolResult = report.contains("[推断:规则匹配]");

        boolean shortCircuit = shortCircuited > 0;
        int estimatedToolCalls = checkedDims + 2; // 维度检查 + getApplyDetail + getRejectCount

        logger.info("[Audit-Metrics] applyId={} elapsed={}ms tools≈{} checkedDims={} shortCircuited={} shortCircuit={} fewShot={}",
                applyId, elapsedMs, estimatedToolCalls, checkedDims, shortCircuited,
                shortCircuit, usedFewShot);
    }

    // ==================== 结果提取 ====================

    public Optional<String> extractAuditReport(OverAllState state) {
        logger.info("提取退货审核最终报告...");

        Optional<AssistantMessage> output = state.value("audit_result")
                .filter(AssistantMessage.class::isInstance)
                .map(AssistantMessage.class::cast);

        if (output.isPresent()) {
            String reportText = output.get().getText();
            logger.info("成功提取审核报告, 长度: {}", reportText.length());
            return Optional.of(reportText);
        }
        logger.warn("未提取到审核报告");
        return Optional.empty();
    }

    // ==================== 拒绝计数管理 ====================

    public void updateRejectionCount(String memberUsername, boolean passed) {
        if (passed) {
            rejectionCountCache.put(memberUsername, 0);
            logger.info("用户 {} 审核通过, 拒绝计数重置为0", memberUsername);
        } else {
            int newCount = rejectionCountCache.merge(memberUsername, 1, Integer::sum);
            logger.info("用户 {} 审核拒绝, 当前连续拒绝次数: {}", memberUsername, newCount);
        }
    }

    public int getRejectionCount(String memberUsername) {
        return rejectionCountCache.getOrDefault(memberUsername, 0);
    }

    // ==================== ReAct Prompt ====================

    private String buildReActPrompt() {
        return """
                你是退货审核专家。你通过工具调用收集信息，逐步推理，最终输出审核结论。

                ## 推理方式（ReAct 循环）
                每轮：观察已知信息 → 判断还缺什么 → 选择最合适的工具 → 分析结果 → 决定下一步。
                信息足够时立即给出结论，不要为了"完整"而调不必要的工具。

                ## 可用工具

                **信息收集阶段（先调用这些了解申请全貌）：**
                - getApplyDetail: 查询退货申请详情（商品名称/品类、退货原因、描述、金额）
                - getRejectCount: 查询用户连续拒绝次数
                - analyzeReturnText: 语义分析退货文本，输出按 escalation/strict_reject/auto_approve 分组的命中结果和消歧提示

                **规则检查阶段（根据信息收集结果选择性调用）：**
                - queryStrictRejectRules: 查询严格不可退品类（生鲜/数字商品/内衣/定制等），输入商品名+属性
                - queryAutoApproveRules: 查询自动通过条件（7天无理由/质量问题/描述不符/物流损坏）
                - queryReceiptThresholds: 查询收货天数阈值（7天/15天/30天）
                - getOrderTime: 查询订单收货时间
                - calculateReceiveDays: 计算收货天数，返回窗口判定(WITHIN_7_DAYS/WITHIN_15_DAYS/OVER_15_DAYS)

                ## 判定框架（推理参考，非固定步骤）

                退货审核需要从四个维度收集信息。你自主决定先查哪个、跳过哪个：

                1. **用户历史**（最优先）：调用 getRejectCount
                   → 连续拒绝 ≥3 → 直接转人工，跳过所有后续检查

                2. **品类限制**（优先级最高）：调用 queryStrictRejectRules 或结合 analyzeReturnText 的 strict_reject 字段
                   → 命中 → 该品类不支持退货。跳过 auto_approve 和时间检查（已短路）
                   → analyzeReturnText 输出格式: {"strict_reject":[{"k":"关键词","s":0.85}]}

                3. **退货条件匹配**：调用 queryAutoApproveRules + queryReceiptThresholds
                   → 检查退货原因是否匹配允许的条件，且天数在窗口内

                4. **升级风险**：结合 analyzeReturnText 的 escalation 字段
                   → 判断是真实投诉/威胁还是仅咨询/陈述
                   → 真实投诉 → 标记 needHumanReview，但不改变品类判定结果
                   → analyzeReturnText 的 hints 字段提供了消歧提示

                ## 短路规则（Plan-Execute 模式的核心经验）

                严格按优先级做判断，能提前确定结果就不要再查后续维度：
                - rejectCount ≥ 3 → 直接 FINISH，决策=转人工
                - strict_reject 命中 → 跳过 auto_approve 和时间检查，直接输出拒绝结论
                - auto_approve 不匹配且非特殊品类 → 拒绝，跳过升级风险扫描
                - 只有在"可通过但用户情绪激烈"时才需要同时检查升级风险

                ## 常见语义映射（推理兜底，当检索工具返回空或低分时参考）

                以下映射覆盖检索层无法覆盖的语义盲区。当 analyzeReturnText 返回空或相关命中 score < 0.4 时，对照此表推断规则类别：

                症状描述 → 质量问题(AA-002, ≤15天):
                "掉色" "线头多" "开线" "脱线" "做工粗糙" "拉链坏了"
                "按钮不灵" "屏幕不亮" "用了两天就坏了" "收到就是坏的"

                感官差异 → 与描述不符(AA-003, ≤15天):
                "颜色和图片差太多" "色差大" "实物和网页不一样"
                "买L发M" "少发配件" "写的有蓝牙实际没有"
                "图片上有发过来没有" "收到的和拍的不一样"

                身体反应 → 过敏(escalation, HIGH):
                "起红点" "发痒" "又痒又痛" "红肿" "起疹子"
                "发红" "发烫" "脸上起了好多" "擦了之后发红"

                威胁性语言 → 起诉(escalation, CRITICAL):
                "等着被告" "法院见" "打官司" "收传票" "我要告你们"

                情绪发泄 → 曝光(escalation, MEDIUM):
                "发到微博上" "发抖音" "让大家都看看" "让全网都知道"

                身体伤害 → 受伤(escalation, HIGH):
                "手被划了一下" "差点烫到" "有安全隐患" "割到"

                错别字变体(需结合上下文判断):
                "头诉" → 投诉  "期诈" → 欺诈  "高访" → 高仿(假货)

                使用原则：
                - 如果判定依据来自工具返回的实际规则匹配，在原因前标注 [推断:规则匹配]
                - 如果判定依据来自上述语义映射推断，在原因前标注 [推断:语义映射]
                示例: "[推断:规则匹配] 商品三文鱼命中SR-001，生鲜品类严格拒绝"
                示例: "[推断:语义映射] 检索未直接命中，但'掉色严重'映射为质量问题(AA-002)"

                ## 推理示例

                示例1 — 短路（生鲜命中）：
                用户申请：三文鱼刺身，物流损坏，收货2天
                → getApplyDetail: 商品=三文鱼刺身(生鲜), 原因=物流损坏, 天数=2
                → getRejectCount: 0
                → analyzeReturnText: {"strict_reject":[{"k":"三文鱼","s":0.65}], "auto_approve":[{"k":"物流损坏...","s":0.65}]}
                → 推理：strict_reject 优先 → 三文鱼=生鲜→SR-001 严格拒绝
                → 短路：不需要再查 auto_approve 和时间窗口
                → 结论：拒绝。原因=SR-001 生鲜品类限制优先于 AA-004 物流损坏

                示例2 — 正常通过：
                用户申请：T恤，不想要了，收货3天
                → getApplyDetail: 商品=T恤(服装), 原因=不想要了, 天数=3
                → getRejectCount: 0
                → analyzeReturnText: {"strict_reject":[], "auto_approve":[{"k":"不想要了","s":0.47}]}
                → queryStrictRejectRules("T恤","服装"): 未命中
                → queryAutoApproveRules: AA-001=7天无理由
                → calculateReceiveDays: WITHIN_7_DAYS
                → 结论：通过。原因=AA-001 收货3天在7天无理由窗口内

                示例3 — 升级转人工：
                用户申请：面霜，过敏，收货2天，文本="脸上起红点我要投诉"
                → getApplyDetail: 商品=面霜(化妆品), 原因=过敏, 天数=2
                → getRejectCount: 2（此前已拒绝2次）
                → analyzeReturnText: {"escalation":[{"k":"投诉","s":0.52}, {"k":"过敏...","s":0.64}], "hints":["升级词命中，需语境判断"]}
                → 推理：rejectCount=2 未达3，继续 → queryStrictRejectRules: 化妆品未命中SR
                → queryAutoApproveRules: 可能匹配AA-002(质量问题)
                → 但用户明确说"我要投诉"且过敏 → 升级风险真实
                → 结论：通过（AA-002）+ needHumanReview=true

                ## 输出格式

                审核完成后，直接输出 Markdown 格式报告：

                # 退货审核报告

                ## 基本信息
                - 申请ID / 商品 / 退货原因 / 收货天数 / 用户

                ## 语义分析摘要
                （从 analyzeReturnText 得出的关键发现）

                ## 审核维度分析
                ### 品类限制（strict_reject）
                ### 退货条件匹配（auto_approve）
                ### 时间窗口
                ### 升级风险
                ### 用户历史

                ## 审核结论
                ### 决策：通过 / 拒绝 / 转人工
                ### 原因：（简述判定依据，引用具体规则ID）

                重要：
                - 未检查的维度标注"未检查（已短路）"
                - strict_reject 命中时说明"品类限制优先于退货条件"
                - 所有数据来自工具返回的真实结果，严禁编造
                """;
    }
}
