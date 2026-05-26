package org.example.service;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
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

import java.util.List;
import java.util.Map;

/**
 * 聊天服务
 * 封装 ReactAgent 对话的公共逻辑，包括模型创建、系统提示词构建、Agent 配置等
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

    /**
     * 创建 DashScope API 实例
     */
    public DashScopeApi createDashScopeApi() {
        return DashScopeApi.builder()
                .apiKey(dashScopeApiKey)
                .build();
    }

    /**
     * 创建 ChatModel
     * @param temperature 控制随机性 (0.0-1.0)
     * @param maxToken 最大输出长度
     * @param topP 核采样参数
     */
    public DashScopeChatModel createChatModel(DashScopeApi dashScopeApi, double temperature, int maxToken, double topP) {
        return DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi)
                .defaultOptions(DashScopeChatOptions.builder()
                        .withModel(DashScopeChatModel.DEFAULT_MODEL_NAME)
                        .withTemperature(temperature)
                        .withMaxToken(maxToken)
                        .withTopP(topP)
                        .build())
                .build();
    }

    /**
     * 创建标准对话 ChatModel（默认参数）
     */
    public DashScopeChatModel createStandardChatModel(DashScopeApi dashScopeApi) {
        return createChatModel(dashScopeApi, 0.7, 2000, 0.9);
    }

    /**
     * 构建系统提示词（包含历史消息和商品上下文）
     * @param history 历史消息列表
     * @param productName 当前会话关联的商品名称（可为null）
     * @return 完整的系统提示词
     */
    public String buildSystemPrompt(List<Map<String, String>> history, String productName) {
        StringBuilder systemPromptBuilder = new StringBuilder();

        // 基础系统提示
        systemPromptBuilder.append("你是一个电商客服助手，专门回答商品相关问题。\n");
        systemPromptBuilder.append("当用户询问商品、优惠、价格、库存、型号对比、适用人群、品牌推荐、尺码规格、退换货、支付方式、物流等问题时，必须优先使用 searchFaq 工具查询知识库。\n");
        systemPromptBuilder.append("如果 searchFaq 返回了匹配的答案，直接使用该答案回复用户，不要修改或添加额外内容，答案后加 '[自动回复]'。\n");
        systemPromptBuilder.append("如果 searchFaq 的匹配分数太低或无匹配结果，回复 'NEED_HUMAN: 您的问题已转接人工客服'。\n");
        systemPromptBuilder.append("对于型号对比类问题，从知识库中找到对应商品信息后按表格形式呈现对比结果。\n");
        systemPromptBuilder.append("当前时间由 getCurrentDateTime 提供。\n\n");

        // 注入商品上下文
        if (productName != null && !productName.isEmpty()) {
            systemPromptBuilder.append("【当前商品上下文】用户正在查看的商品是：「").append(productName).append("」。\n");
            systemPromptBuilder.append("重要规则：当用户使用「这个」「这款」「它」「该商品」「这」「该」等指代词时，你必须先将指代词替换为「").append(productName).append("」再调用 searchFaq。\n");
            systemPromptBuilder.append("例如：用户问「这个有优惠吗」→ 你应该用 searchFaq 搜索「").append(productName).append(" 有优惠吗」。\n");
            systemPromptBuilder.append("搜索到答案后，只回复关于「").append(productName).append("」的信息，不要列出其他商品。\n\n");
        }
        
        // 添加历史消息
        if (!history.isEmpty()) {
            systemPromptBuilder.append("--- 对话历史 ---\n");
            for (Map<String, String> msg : history) {
                String role = msg.get("role");
                String content = msg.get("content");
                if ("user".equals(role)) {
                    systemPromptBuilder.append("用户: ").append(content).append("\n");
                } else if ("assistant".equals(role)) {
                    systemPromptBuilder.append("助手: ").append(content).append("\n");
                }
            }
            systemPromptBuilder.append("--- 对话历史结束 ---\n\n");
        }
        
        systemPromptBuilder.append("请基于以上对话历史，回答用户的新问题。");
        
        return systemPromptBuilder.toString();
    }

    /**
     * 构建客服Agent方法工具数组（仅FAQ检索+时间查询）
     */
    public Object[] buildMethodToolsArray() {
        return new Object[]{faqTools, dateTimeTools};
    }

    /**
     * 获取工具回调列表，mcp服务提供的工具
     */
    public ToolCallback[] getToolCallbacks() {
        return tools.getToolCallbacks();
    }

    /**
     * 记录可用工具列表：mcp服务提供的工具
     */
    public void logAvailableTools() {
        ToolCallback[] toolCallbacks = tools.getToolCallbacks();
        logger.info("可用工具列表:");
        for (ToolCallback toolCallback : toolCallbacks) {
            logger.info(">>> {}", toolCallback.getToolDefinition().name());
        }
    }

    /**
     * 创建 ReactAgent
     * @param chatModel 聊天模型
     * @param systemPrompt 系统提示词
     * @return 配置好的 ReactAgent
     */
    public ReactAgent createReactAgent(DashScopeChatModel chatModel, String systemPrompt) {
        return ReactAgent.builder()
                .name("intelligent_assistant")
                .model(chatModel)
                .systemPrompt(systemPrompt)
                .methodTools(buildMethodToolsArray())
                .tools(getToolCallbacks())
                .build();
    }

    /**
     * 执行 ReactAgent 对话（非流式）
     * @param agent ReactAgent 实例
     * @param question 用户问题
     * @return AI 回复
     */
    public String executeChat(ReactAgent agent, String question) throws GraphRunnerException {
        logger.info("执行 ReactAgent.call() - 自动处理工具调用");
        var response = agent.call(question);
        String answer = response.getText();
        logger.info("ReactAgent 对话完成，答案长度: {}", answer.length());
        return answer;
    }
}
