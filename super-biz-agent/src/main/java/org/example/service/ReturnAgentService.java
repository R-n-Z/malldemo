package org.example.service;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.SupervisorAgent;
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

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 退货审核 Agent 服务
 * 使用 Planner-Executor-Supervisor 三 Agent 编排模式自动审核退货申请
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

    /** 用户连续拒绝次数缓存 */
    private final ConcurrentHashMap<String, Integer> rejectionCountCache = new ConcurrentHashMap<>();

    /**
     * 执行退货审核多 Agent 协作流程
     */
    public Optional<OverAllState> executeReturnAudit(DashScopeChatModel chatModel,
                                                      ToolCallback[] toolCallbacks,
                                                      Long applyId) throws GraphRunnerException {
        logger.info("开始执行退货审核流程, applyId={}", applyId);

        ReactAgent auditAgent = ReactAgent.builder()
                .name("return_audit_agent")
                .description("退货审核专家：综合商品属性、收货时长、退货原因、用户历史判定退货申请")
                .model(chatModel)
                .systemPrompt(buildAuditPrompt())
                .methodTools(buildMethodToolsArray())
                .outputKey("audit_result")
                .build();

        String taskPrompt = String.format(
                "请对退货申请 #%d 进行审核。按以下顺序执行："
                + "1. 调用 getApplyDetail 获取申请详情。"
                + "2. 调用 queryAutoApproveRules + queryReceiptThresholds，将 reason 与 allowedReasons 匹配。"
                + "3. 调用 getOrderTime + calculateReceiveDays 计算收货时长，判断是否在规则允许天数内。"
                + "4. 调用 hybridKeywordSearch 扫描 reason+description 中的敏感词。"
                + "5. 调用 getRejectCount 获取用户连续拒绝次数。"
                + "6. 若原因匹配自动通过规则且天数满足 → 标记通过。否则标记拒绝或转人工。禁止编造数据。",
                applyId);

        logger.info("调用退货审核 Agent, applyId={}", applyId);
        return auditAgent.invoke(taskPrompt);
    }

    /**
     * 从执行结果中提取最终审核报告
     */
    public Optional<String> extractAuditReport(OverAllState state) {
        logger.info("提取退货审核最终报告...");

        Optional<AssistantMessage> plannerOutput = state.value("audit_result")
                .filter(AssistantMessage.class::isInstance)
                .map(AssistantMessage.class::cast);

        if (plannerOutput.isPresent()) {
            String reportText = plannerOutput.get().getText();
            logger.info("成功提取审核报告, 长度: {}", reportText.length());
            return Optional.of(reportText);
        }
        logger.warn("未提取到审核报告");
        return Optional.empty();
    }

    /** 更新拒绝计数 */
    public void updateRejectionCount(String memberUsername, boolean passed) {
        if (passed) {
            rejectionCountCache.put(memberUsername, 0);
            logger.info("用户 {} 审核通过, 拒绝计数重置为0", memberUsername);
        } else {
            int newCount = rejectionCountCache.merge(memberUsername, 1, Integer::sum);
            logger.info("用户 {} 审核拒绝, 当前连续拒绝次数: {}", memberUsername, newCount);
        }
    }

    /** 获取拒绝计数 */
    public int getRejectionCount(String memberUsername) {
        return rejectionCountCache.getOrDefault(memberUsername, 0);
    }

    // ==================== Agent 构建 ====================

    private Object[] buildMethodToolsArray() {
        return new Object[]{returnOrderTools, returnAuditTools, dateTimeCalculateTools,
                returnRuleKnowledgeTools, exactMatchTools, hybridSearchTools};
    }

    private String buildAuditPrompt() {
        return """
                你是退货审核专家。你有以下工具可用：
                - getApplyDetail: 查询退货申请详情
                - getOrderTime: 查询订单收货时间
                - calculateReceiveDays: 计算收货天数
                - getRejectCount: 查询用户连续拒绝次数
                - queryAutoApproveRules: 查询自动通过条件
                - queryStrictRejectRules: 查询严格拒绝商品类目
                - queryReceiptThresholds: 查询收货天数阈值
                - hybridKeywordSearch: 混合检索敏感词（投诉/假货等）

                审核规则（必须严格遵守）：
                1. 先调用 queryAutoApproveRules 获取规则，核对退货原因是否匹配 allowedReasons
                   - 匹配 AA-001（七天无理由）且收货≤7天 → 自动通过
                   - 匹配 AA-002（质量问题）且收货≤15天且有凭证 → 自动通过
                   - 匹配 AA-003（描述不符/发错货/少件）且收货≤15天 → 自动通过
                   - 匹配 AA-004（物流损坏）且收货≤3天且有凭证 → 自动通过
                2. 再调用 queryStrictRejectRules 检查是否命中严格拒绝类目 → 命中则拒绝
                3. 连续拒绝≥3次 → 自动转人工
                4. 收货>30天 → 一律拒绝

                最终输出 Markdown 格式的《退货审核报告》，包含：
                # 退货审核报告
                ## 基本信息
                ## 审核维度分析（商品属性/收货时长/退货原因/用户历史）
                ## 审核结论
                ### 决策：通过
                （或者：拒绝、转人工，三选一，只填一个词）
                ### 原因
                （简述判定依据）
                """;
    }
}
