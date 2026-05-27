package org.example.service;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.SupervisorAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import org.example.agent.tool.DateTimeTools;
import org.example.agent.tool.FaqTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import org.springframework.ai.chat.messages.AssistantMessage;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 客服多智能体服务
 * SupervisorAgent + 4 子 Agent：售前 / 售后 / 升级 / 闲聊
 */
@Service
public class ChatService {

    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);

    @Autowired
    private FaqTools faqTools;

    @Autowired
    private DateTimeTools dateTimeTools;

    @Autowired
    private ToolCallbackProvider tools;

    @Value("${spring.ai.dashscope.api-key}")
    private String dashScopeApiKey;

    // ==================== 模型工厂 ====================

    public DashScopeApi createDashScopeApi() {
        return DashScopeApi.builder().apiKey(dashScopeApiKey).build();
    }

    public DashScopeChatModel createChatModel(DashScopeApi api, double temperature, int maxToken, double topP) {
        return DashScopeChatModel.builder()
                .dashScopeApi(api)
                .defaultOptions(DashScopeChatOptions.builder()
                        .withModel(DashScopeChatModel.DEFAULT_MODEL_NAME)
                        .withTemperature(temperature).withMaxToken(maxToken).withTopP(topP).build())
                .build();
    }

    public DashScopeChatModel createStandardChatModel(DashScopeApi api) {
        return createChatModel(api, 0.5, 1500, 0.9);
    }

    public void logAvailableTools() {
        for (ToolCallback t : tools.getToolCallbacks())
            logger.info(">>> MCP tool: {}", t.getToolDefinition().name());
    }

    public ToolCallback[] getToolCallbacks() {
        return tools.getToolCallbacks();
    }

    // ==================== 4 个子 Agent ====================

    /** 售前咨询 Agent — 商品信息、价格、对比、推荐 */
    public ReactAgent buildPreSalesAgent(DashScopeChatModel model, String productName) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是售前咨询专家。你的职责是回答商品相关问题：价格、优惠、库存、型号对比、品牌推荐、适用人群、规格选配、功能特点。\n");
        prompt.append("规则：\n");
        prompt.append("1. 优先使用 searchFaq 查询知识库，返回匹配答案后加 '[自动回复]'。\n");
        prompt.append("2. 如果 searchFaq 返回多个结果，优先回复与用户问的商品最相关的那个。\n");
        prompt.append("3. 知识库无匹配时，回复 'NEED_HUMAN: 您的问题已转接人工客服'。\n");
        prompt.append("4. 对于型号对比问题，从知识库提取信息后以对比表格呈现。\n");
        if (productName != null && !productName.isEmpty()) {
            prompt.append("5. 用户当前在看「").append(productName)
                 .append("」，说「这个」时指的就是它，搜索时替换为商品名。\n");
        }
        return ReactAgent.builder()
                .name("pre_sales_agent")
                .description("售前咨询：商品信息、价格、库存、对比、推荐")
                .model(model).systemPrompt(prompt.toString())
                .methodTools(new Object[]{faqTools})
                .outputKey("pre_sales_answer").build();
    }

    /** 售后支持 Agent — 退换货、物流、支付、保修 */
    public ReactAgent buildPostSalesAgent(DashScopeChatModel model) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是售后支持专家。你的职责是回答售后服务问题：退换货流程、退款时效、物流查询、支付方式、发票、保修、订单修改/取消。\n");
        prompt.append("规则：\n");
        prompt.append("1. 优先使用 searchFaq 查询知识库，返回匹配答案后加 '[自动回复]'。\n");
        prompt.append("2. 知识库无匹配时，回复 'NEED_HUMAN: 您的问题已转接人工客服，我们的客服人员将尽快为您处理'。\n");
        prompt.append("3. 涉及具体订单时，提醒用户提供订单号以便客服查询。\n");
        return ReactAgent.builder()
                .name("post_sales_agent")
                .description("售后支持：退换货、物流、支付、保修、订单")
                .model(model).systemPrompt(prompt.toString())
                .methodTools(new Object[]{faqTools})
                .outputKey("post_sales_answer").build();
    }

    /** 人工升级 Agent — 投诉、复杂问题、用户要求人工 */
    public ReactAgent buildEscalationAgent(DashScopeChatModel model) {
        String prompt = "你是客服升级专员。当用户的问题无法通过自动客服解决时，你将问题升级到人工客服。\n"
            + "规则：\n"
            + "1. 对于投诉、复杂纠纷、用户明确要求人工、法律/合规问题，回复 'NEED_HUMAN: ' 后跟简短的安抚+转接说明。\n"
            + "2. 安抚用户情绪，告知预计响应时间（工作时间9:00-21:00，通常30分钟内响应）。\n"
            + "3. 不要尝试回答你无法确定的问题，直接转人工。\n"
            + "示例回复：'NEED_HUMAN: 非常抱歉给您带来不便，您的问题已转接高级客服专员，工作时间30分钟内为您处理。'\n";
        return ReactAgent.builder()
                .name("escalation_agent")
                .description("人工升级：投诉、复杂问题、用户要求人工")
                .model(model).systemPrompt(prompt)
                .outputKey("escalation_answer").build();
    }

    /** 闲聊处理 Agent — 问候、感谢、范围外话题 */
    public ReactAgent buildChitchatAgent(DashScopeChatModel model) {
        String prompt = "你是电商客服的接待助手。你的职责是处理闲聊和简单问候。\n"
            + "规则：\n"
            + "1. 对「你好」「早上好」「谢谢」「再见」等礼貌用语给予简短友好的回复。\n"
            + "2. 如果用户问与电商完全无关的话题，友好引导回商品咨询。\n"
            + "3. 不要调用任何工具。回复简洁（1-2句话）。\n"
            + "4. 回复后加 '[自动回复]'。\n"
            + "你可以用 getCurrentDateTime 获取当前时间。\n";
        return ReactAgent.builder()
                .name("chitchat_agent")
                .description("闲聊处理：问候、感谢、范围外话题")
                .model(model).systemPrompt(prompt)
                .methodTools(new Object[]{dateTimeTools})
                .outputKey("chitchat_answer").build();
    }

    // ==================== SupervisorAgent ====================

    public SupervisorAgent createSupervisorAgent(DashScopeChatModel model,
            String productName, List<Map<String, String>> history) {
        ReactAgent preSales = buildPreSalesAgent(model, productName);
        ReactAgent postSales = buildPostSalesAgent(model);
        ReactAgent escalation = buildEscalationAgent(model);
        ReactAgent chitchat = buildChitchatAgent(model);

        String supervisorPrompt = buildSupervisorPrompt(productName, history);

        return SupervisorAgent.builder()
                .name("agent_router")
                .description("客服路由控制器：根据用户问题类型分派给售前/售后/升级/闲聊Agent")
                .model(model)
                .systemPrompt(supervisorPrompt)
                .subAgents(List.of(preSales, postSales, escalation, chitchat))
                .compileConfig(CompileConfig.builder().recursionLimit(3).build())
                .build();
    }

    // ==================== Supervisor Prompt ====================

    private String buildSupervisorPrompt(String productName, List<Map<String, String>> history) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个智能客服路由系统。你的任务是根据用户问题，将请求路由到最合适的专业Agent处理。\n\n");
        sb.append("## 可用的子Agent：\n");
        sb.append("- pre_sales_agent：售前咨询 → 商品信息、价格、优惠、库存、型号对比、品牌推荐、适用人群、规格选配、功能特点\n");
        sb.append("- post_sales_agent：售后支持 → 退换货、退款、物流、支付方式、发票、保修、订单修改/取消\n");
        sb.append("- escalation_agent：人工升级 → 投诉、复杂纠纷、用户要求人工、法律合规、Agent无法回答的问题\n");
        sb.append("- chitchat_agent：闲聊处理 → 问候、感谢、告别、与电商无关的话题\n\n");
        sb.append("## 路由规则：\n");
        sb.append("1. 先判断用户意图，再调用对应的子Agent\n");
        sb.append("2. 如果用户问题同时涉及售前和售后，优先调用最相关的那个\n");
        sb.append("3. 不确定时优先调用 escalation_agent 升级处理\n");
        sb.append("4. 调用子Agent后，将子Agent的回复直接返回给用户，不要修改内容\n\n");

        if (productName != null && !productName.isEmpty()) {
            sb.append("## 当前商品上下文\n");
            sb.append("用户正在查看：「").append(productName).append("」\n");
            sb.append("用户说「这个」「它」「该」等指代词时，指的就是「").append(productName).append("」\n\n");
        }

        if (history != null && !history.isEmpty()) {
            sb.append("## 对话历史\n");
            for (Map<String, String> msg : history) {
                String role = "user".equals(msg.get("role")) ? "用户" : "助手";
                sb.append(role).append(": ").append(msg.get("content")).append("\n");
            }
            sb.append("\n");
        }

        sb.append("重要：只调用一次子Agent，得到回复后立即停止，把子Agent的回复作为最终答案输出。不要重复调用同一个子Agent。");
        return sb.toString();
    }

    // ==================== 结果提取 ====================

    /**
     * 从 SupervisorAgent 执行结果中提取最终答案
     * 类型安全地获取子Agent输出，避免 toString() 产生 JSON/对象转储
     */
    public String extractAnswer(Optional<OverAllState> state) {
        if (state.isEmpty()) return "NEED_HUMAN: 客服系统暂时繁忙，已转人工处理";

        OverAllState s = state.get();

        // 按 outputKey 提取子Agent的输出
        for (String key : new String[]{"pre_sales_answer", "post_sales_answer",
                "escalation_answer", "chitchat_answer", "last_response", "messages"}) {
            try {
                java.util.Optional<?> val = s.value(key);
                if (val.isEmpty()) continue;

                Object raw = val.get();
                String answer = extractText(raw);
                if (answer != null && !answer.isBlank() && answer.length() > 5) {
                    // 过滤纯 JSON/对象转储（FaqTools 工具返回值等）
                    if (isRawDump(answer)) continue;
                    logger.info("提取到 {} 的输出，长度={}", key, answer.length());
                    return answer;
                }
            } catch (Exception ignored) {}
        }

        return "NEED_HUMAN: 客服系统暂时繁忙，已转人工处理";
    }

    /** 类型安全地从值中提取文本 */
    private String extractText(Object raw) {
        // 1) 直接是 AssistantMessage → 取 textContent
        if (raw instanceof AssistantMessage msg) {
            String text = msg.getText();
            if (text != null && !text.isBlank()) return text;
        }
        // 2) List/Collection → 找最后一条 AssistantMessage
        if (raw instanceof Collection<?> coll) {
            for (Object item : coll) {
                if (item instanceof AssistantMessage msg) {
                    String text = msg.getText();
                    if (text != null && !text.isBlank()) return text;
                }
            }
        }
        // 3) String → 直接用
        if (raw instanceof String str) {
            return str;
        }
        return null;
    }

    /** 判断是否为 Java 对象转储或原始 JSON（非正常回复文本） */
    private boolean isRawDump(String str) {
        String s = str.strip();
        // 以 JSON 对象/数组开头
        if (s.startsWith("{") || s.startsWith("[")) return true;
        // Java 对象 dump
        if (s.startsWith("AssistantMessage") || s.startsWith("ToolResponse")
                || s.startsWith("Message [")) return true;
        return false;
    }

    /**
     * 调用 SupervisorAgent 处理用户问题
     */
    public String executeSupervisor(SupervisorAgent supervisor, String question) throws GraphRunnerException {
        logger.info("执行 SupervisorAgent.invoke() - 自动路由+工具调用");
        Optional<OverAllState> state = supervisor.invoke(question);
        String answer = extractAnswer(state);
        logger.info("SupervisorAgent 完成，答案长度={}", answer.length());
        return answer;
    }
}
