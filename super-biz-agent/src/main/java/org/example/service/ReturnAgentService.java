package org.example.service;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.SupervisorAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import org.example.agent.tool.audit.DateTimeCalculateTools;
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
    private ReturnRuleAgentService returnRuleAgentService;

    /** 用户连续拒绝次数缓存 */
    private final ConcurrentHashMap<String, Integer> rejectionCountCache = new ConcurrentHashMap<>();

    /**
     * 执行退货审核多 Agent 协作流程
     */
    public Optional<OverAllState> executeReturnAudit(DashScopeChatModel chatModel,
                                                      ToolCallback[] toolCallbacks,
                                                      Long applyId) throws GraphRunnerException {
        logger.info("开始执行退货审核多 Agent 协作流程, applyId={}", applyId);

        ReactAgent plannerAgent = buildAuditPlannerAgent(chatModel, toolCallbacks);
        ReactAgent executorAgent = buildAuditExecutorAgent(chatModel, toolCallbacks);
        ReactAgent ruleJudgeAgent = returnRuleAgentService.buildReturnRuleAgent(chatModel, toolCallbacks);

        SupervisorAgent supervisorAgent = SupervisorAgent.builder()
                .name("return_audit_supervisor")
                .description("负责调度退货审核 Planner、Executor 与 RuleJudge 的多 Agent 控制器")
                .model(chatModel)
                .systemPrompt(buildSupervisorSystemPrompt())
                .subAgents(List.of(plannerAgent, executorAgent, ruleJudgeAgent))
                .build();

        String taskPrompt = String.format(
                "请对退货申请 #%d 进行审核。严格按以下顺序执行："
                + "1. 首先调用 getReturnApplyDetail 获取申请详情。"
                + "2. 将 reason+description 传给 rule_judge_agent 执行混合检索规则判定。"
                + "3. 调用 getOrderReceiveTime + calculateReceiveDays 计算收货时长。"
                + "4. 调用 queryAutoApproveRules + queryReceiptThresholds 匹配自动通过条件。"
                + "5. 调用 getUserRejectionCount 获取用户连续拒绝次数。"
                + "6. 汇总 rule_judge_agent 判定结果 + 时间维度 + 历史维度，按模板输出《退货审核报告》。"
                + "禁止编造数据，所有结论必须基于工具返回的真实数据。",
                applyId);

        logger.info("调用退货审核 Supervisor Agent 开始编排, applyId={}", applyId);
        return supervisorAgent.invoke(taskPrompt);
    }

    /**
     * 从执行结果中提取最终审核报告
     */
    public Optional<String> extractAuditReport(OverAllState state) {
        logger.info("提取退货审核最终报告...");

        Optional<AssistantMessage> plannerOutput = state.value("audit_plan")
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

    private ReactAgent buildAuditPlannerAgent(DashScopeChatModel chatModel, ToolCallback[] toolCallbacks) {
        return ReactAgent.builder()
                .name("audit_planner")
                .description("负责分析退货申请并制定审核步骤，汇总证据后做出审核决策")
                .model(chatModel)
                .systemPrompt(buildPlannerPrompt())
                .methodTools(buildMethodToolsArray())
                .tools(toolCallbacks)
                .outputKey("audit_plan")
                .build();
    }

    private ReactAgent buildAuditExecutorAgent(DashScopeChatModel chatModel, ToolCallback[] toolCallbacks) {
        return ReactAgent.builder()
                .name("audit_executor")
                .description("负责执行 Planner 指定的查询步骤，收集证据并反馈给 Planner")
                .model(chatModel)
                .systemPrompt(buildExecutorPrompt())
                .methodTools(buildMethodToolsArray())
                .tools(toolCallbacks)
                .outputKey("audit_executor_result")
                .build();
    }

    private Object[] buildMethodToolsArray() {
        return new Object[]{returnOrderTools, returnAuditTools, dateTimeCalculateTools, returnRuleKnowledgeTools};
    }

    // ==================== System Prompts ====================

    private String buildPlannerPrompt() {
        return """
                你是退货审核 Planner Agent，负责：
                1. 读取当前退货申请信息 {input} 以及 Executor 的反馈 {audit_executor_result}。
                2. 制定审核步骤并输出 JSON decision：
                   - PLAN: 分析任务，暂不执行
                   - EXECUTE: 给出具体执行步骤（需指定 toolToCall 和 toolParams）
                   - FINISH: 所有证据收集完毕，输出最终审核报告
                3. 审核维度（缺一不可，按顺序执行）：
                   a) **关键词扫描（第一步，最优先）**：
                      用 checkEscalationKeywords 检查 reason 和 description 是否包含转人工关键词
                      （投诉/12315/消协/律师/起诉/假货/欺诈/过敏/受伤/工商/媒体/曝光）。
                      命中 CRITICAL 或 HIGH 优先级关键词 → 立即 FINISH，标记转人工。
                   b) **严格拒绝规则检查（第二步）**：
                      用 queryStrictRejectRules 根据 productName 和 productAttr 检查是否命中
                      严格不允许退货的商品类别（生鲜/数字化商品/内衣/定制/消耗品等）。
                      命中了 autoReject=true 的规则 → 直接拒绝，除非有有效例外凭证。
                   c) **收货时长校验**：
                      用 getOrderReceiveTime 获取收货时间，用 calculateReceiveDays 计算天数，
                      用 queryReceiptThresholds 获取时间阈值规则。
                      - ≤7天：支持七天无理由退货
                      - 7~15天：仅质量问题/描述不符可通过
                      - 15~30天：严格审查
                      - >30天：拒绝
                   d) **自动通过规则匹配**：
                      用 queryAutoApproveRules 获取自动通过条件，核对当前申请是否满足。
                   e) **退货原因校验**：reason 字段是否合理，description 和 proofPics 是否有凭证。
                   f) **用户历史校验**：通过 getUserRejectionCount 获取连续拒绝次数。
                      ≥3次连续拒绝 → 自动转人工。
                4. 禁止编造数据，只能引用工具返回的真实内容。同一工具连续失败3次需停止并说明。

                当 decision=FINISH 时，直接输出 Markdown 格式报告，不要用 JSON：

                # 退货审核报告

                ## 基本信息
                ## 关键词扫描结果
                ## 审核维度分析
                ### 1. 严格拒绝规则检查
                ### 2. 收货时长校验
                ### 3. 自动通过规则匹配
                ### 4. 退货原因校验
                ### 5. 用户历史校验
                ## 审核结论
                ### 决策：通过 / 拒绝 / 转人工
                ### 原因
                ### 是否需要人工介入
                """;
    }

    private String buildExecutorPrompt() {
        return """
                你是退货审核 Executor Agent，负责读取 Planner 最新输出 {audit_plan}，只执行其中的第一步。
                - 确认步骤所需的工具与参数，调用对应工具收集数据。
                - 工具返回错误或空数据时，记录失败原因和请求参数，同一工具连续3次失败返回 FAILED。
                - 将收集到的证据整理为结构化摘要。
                - 以 JSON 形式返回执行结果：

                {"status": "SUCCESS", "evidence": "订单收货时间为2026-05-20，距申请已5天", "nextHint": "建议核查退货原因有效性"}
                {"status": "FAILED", "error": "查询失败原因", "nextHint": "建议跳过该维度继续审核"}
                """;
    }

    private String buildSupervisorSystemPrompt() {
        return """
                你是退货审核 Supervisor，负责调度 audit_planner、audit_executor 与 rule_judge_agent：
                1. **第一步**：调用 rule_judge_agent 做规则判定（语义检索 + 精确匹配 → 语境推理）。
                2. 当需要拆解任务或重新规划时，调用 audit_planner。
                3. 当 audit_planner 输出 decision=EXECUTE 时，调用 audit_executor 执行第一步。
                4. 根据 audit_executor 的反馈，决定是否再次调用 audit_planner，直到 decision=FINISH。
                5. FINISH 后，输出完整的《退货审核报告》。

                审核铁律（必须遵守，按优先级排序）：
                1. rule_judge_agent 判定 escalation 触发 → 立即转人工
                2. rule_judge_agent 判定 strict_reject 触发且无有效例外凭证 → 拒绝
                3. 商品属性与订单记录不一致 → 拒绝
                4. 七天无理由退货超过7天 → 拒绝
                5. 收货超过30天一律拒绝（法律另有规定除外）
                6. 质量问题/描述不符无凭证 → 拒绝
                7. 用户连续3次被拒绝 → 自动转人工，标记 needHumanSupport=true
                8. 只有同时满足：rule_judge_agent 判定无问题 + 收货≤7天 + 原因匹配自动通过规则 → 自动通过

                只允许在 rule_judge_agent、audit_planner、audit_executor 与 FINISH 之间做出选择。
                """;
    }
}
