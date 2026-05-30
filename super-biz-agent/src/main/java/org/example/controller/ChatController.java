package org.example.controller;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.SupervisorAgent;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;
import org.example.dto.ConversationState;
import org.example.service.AiOpsService;
import org.example.service.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * 统一 API 控制器
 * 适配前端接口需求
 */
@RestController
@RequestMapping("/api")
public class ChatController {

    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);

    @Autowired
    private AiOpsService aiOpsService;
    
    @Autowired
    private ChatService chatService;

    @Autowired
    private ToolCallbackProvider tools;

    private final ExecutorService executor = Executors.newCachedThreadPool();

    // 存储会话信息
    private final Map<String, SessionInfo> sessions = new ConcurrentHashMap<>();
    
    // 最大历史消息窗口大小（成对计算：用户消息+AI回复=1对）
    private static final int MAX_WINDOW_SIZE = 6;

    /**
     * 普通对话接口（支持工具调用）
     * 与 /chat_react 逻辑一致，但直接返回完整结果而非流式输出
     */
    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<ChatResponse>> chat(@RequestBody String body) {
        try {
            // 手动解析 JSON（兼容 ContextEnvelope 和旧 ChatRequest 两种格式）
            ObjectMapper localMapper = new ObjectMapper();
            Map<String, Object> raw = localMapper.readValue(body, Map.class);

            String sessionId;
            String question;
            String productName;
            List<Map<String, String>> history;
            String contextSummary = "";

            if (raw.containsKey("user") || raw.containsKey("message")) {
                // === ContextEnvelope 格式 ===
                @SuppressWarnings("unchecked")
                Map<String, Object> userMap = raw.get("user") instanceof Map
                        ? (Map<String, Object>) raw.get("user") : Collections.emptyMap();
                @SuppressWarnings("unchecked")
                Map<String, Object> productMap = raw.get("product") instanceof Map
                        ? (Map<String, Object>) raw.get("product") : Collections.emptyMap();
                @SuppressWarnings("unchecked")
                Map<String, Object> msgMap = raw.get("message") instanceof Map
                        ? (Map<String, Object>) raw.get("message") : Collections.emptyMap();

                sessionId = str(raw.get("sessionId"), str(raw.get("Id"), "unknown"));
                question = str(msgMap.get("content"), str(raw.get("Question"), ""));
                productName = str(productMap.get("productName"), str(raw.get("productName"), null));
                history = parseHistory(raw.get("history"));

                Long userId = lng(userMap.get("userId"));
                String username = str(userMap.get("username"), null);
                Long productId = lng(productMap.get("productId"));
                contextSummary = String.format("用户ID=%d(%s) 商品ID=%d(%s)",
                        userId, username, productId, productName);

                logger.info("收到 ContextEnvelope 请求 - SessionId: {}, Question: {}, Context: {}",
                        sessionId, question, contextSummary);
            } else {
                // === 旧 ChatRequest 格式 ===
                sessionId = str(raw.get("Id"), "unknown");
                question = str(raw.get("Question"), "");
                productName = str(raw.get("productName"), null);
                history = null;
            }

            if (question == null || question.trim().isEmpty()) {
                logger.warn("问题内容为空");
                return ResponseEntity.ok(ApiResponse.success(ChatResponse.error("问题内容不能为空")));
            }

            // 获取或创建会话
            SessionInfo session = getOrCreateSession(sessionId);

            // 优先用请求中的 history（来自DB），否则用内存中的
            if (history != null && !history.isEmpty()) {
                session.replaceHistory(history);
                logger.info("使用请求中的DB历史, 共 {} 条", history.size());
            }
            // 使用结构化对话状态替代原始历史
            ConversationState convState = session.getConversationState();
            logger.info("对话状态: 轮次={}, 意图={}, 实体={}",
                convState.getTurnCount(), convState.getLastIntent(),
                String.join(",", convState.getEntities()));

            DashScopeApi dashScopeApi = chatService.createDashScopeApi();
            DashScopeChatModel chatModel = chatService.createStandardChatModel(dashScopeApi);

            logger.info("开始 SupervisorAgent 多智能体对话");

            // 创建 SupervisorAgent（传入 ConversationState）
            SupervisorAgent supervisor = chatService.createSupervisorAgent(
                chatModel, productName, contextSummary, convState);

            // 执行对话
            String fullAnswer = chatService.executeSupervisor(supervisor, question);

            // 更新会话历史
            session.addMessage(question, fullAnswer);
            logger.info("已更新会话历史 - SessionId: {}, 当前消息对数: {}",
                sessionId, session.getMessagePairCount());

            return ResponseEntity.ok(ApiResponse.success(ChatResponse.success(fullAnswer)));

        } catch (Exception e) {
            logger.error("对话失败", e);
            return ResponseEntity.ok(ApiResponse.success(ChatResponse.error(e.getMessage())));
        }
    }

    /**
     * 清空会话历史
     */
    @PostMapping("/chat/clear")
    public ResponseEntity<ApiResponse<String>> clearChatHistory(@RequestBody ClearRequest request) {
        try {
            logger.info("收到清空会话历史请求 - SessionId: {}", request.getId());

            if (request.getId() == null || request.getId().isEmpty()) {
                return ResponseEntity.ok(ApiResponse.error("会话ID不能为空"));
            }

            SessionInfo session = sessions.get(request.getId());
            if (session != null) {
                session.clearHistory();
                return ResponseEntity.ok(ApiResponse.success("会话历史已清空"));
            } else {
                return ResponseEntity.ok(ApiResponse.error("会话不存在"));
            }

        } catch (Exception e) {
            logger.error("清空会话历史失败", e);
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * ReactAgent 对话接口（SSE 流式模式，支持多轮对话，支持自动工具调用，例如获取当前时间，查询日志，告警等）
     * 支持 session 管理，保留对话历史
     */
    @PostMapping(value = "/chat_stream", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter chatStream(@RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(300000L); // 5分钟超时

        // 参数校验
        if (request.getQuestion() == null || request.getQuestion().trim().isEmpty()) {
            logger.warn("问题内容为空");
            try {
                emitter.send(SseEmitter.event().name("message").data(SseMessage.error("问题内容不能为空"), MediaType.APPLICATION_JSON));
                emitter.complete();
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
            return emitter;
        }

        executor.execute(() -> {
            try {
                logger.info("收到 ReactAgent 对话请求 - SessionId: {}, Question: {}", request.getId(), request.getQuestion());

                // 获取或创建会话
                SessionInfo session = getOrCreateSession(request.getId());
                
                // 获取历史消息
                List<Map<String, String>> history = session.getHistory();
                logger.info("ReactAgent 会话历史消息对数: {}", history.size() / 2);

                // 创建 DashScope API 和 ChatModel
                DashScopeApi dashScopeApi = chatService.createDashScopeApi();
                DashScopeChatModel chatModel = chatService.createStandardChatModel(dashScopeApi);

                // 记录可用工具
                chatService.logAvailableTools();

                logger.info("开始 SupervisorAgent 流式对话");

                // 创建 SupervisorAgent（流式请求使用新建 ConversationState）
                ConversationState streamState = new ConversationState();
                if (history != null) {
                    for (int i = 0; i < history.size(); i++) {
                        Map<String, String> msg = history.get(i);
                        String role = msg.get("role");
                        String content = msg.get("content");
                        if ("user".equals(role) && i + 1 < history.size()) {
                            Map<String, String> next = history.get(i + 1);
                            if ("assistant".equals(next.get("role"))) {
                                streamState.recordTurn(content, next.get("content"), "");
                                i++; // skip next assistant
                            }
                        }
                    }
                }
                SupervisorAgent supervisor = chatService.createSupervisorAgent(
                    chatModel, request.getProductName(), null, streamState);

                // 用于累积完整答案
                StringBuilder fullAnswerBuilder = new StringBuilder();

                // 执行对话（非流式，但通过SSE分块发送）
                String fullAnswer = chatService.executeSupervisor(supervisor, request.getQuestion());
                fullAnswerBuilder.append(fullAnswer);

                // 分块发送答案（模拟流式效果）
                int chunkSize = 50;
                for (int i = 0; i < fullAnswer.length(); i += chunkSize) {
                    int end = Math.min(i + chunkSize, fullAnswer.length());
                    emitter.send(SseEmitter.event().name("message")
                        .data(SseMessage.content(fullAnswer.substring(i, end)), MediaType.APPLICATION_JSON));
                }

                // 更新会话历史
                session.addMessage(request.getQuestion(), fullAnswer);
                logger.info("SupervisorAgent 流式完成 - SessionId: {}, 答案长度: {}",
                    request.getId(), fullAnswer.length());

                emitter.send(SseEmitter.event().name("message")
                    .data(SseMessage.done(), MediaType.APPLICATION_JSON));
                emitter.complete();

            } catch (Exception e) {
                logger.error("SupervisorAgent 初始化失败", e);
                try {
                    emitter.send(SseEmitter.event()
                            .name("message")
                            .data(SseMessage.error(e.getMessage()), MediaType.APPLICATION_JSON));
                } catch (IOException ex) {
                    logger.error("发送错误消息失败", ex);
                }
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    /**
     * AI 智能运维接口（SSE 流式模式）- 自动分析告警并生成运维报告
     * 无需用户输入，自动执行告警分析流程
     */
    @PostMapping(value = "/ai_ops", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter aiOps() {
        SseEmitter emitter = new SseEmitter(600000L); // 10分钟超时（告警分析可能较慢）

        executor.execute(() -> {
            try {
                logger.info("收到 AI 智能运维请求 - 启动多 Agent 协作流程");

                DashScopeApi dashScopeApi = chatService.createDashScopeApi();
                DashScopeChatModel chatModel = DashScopeChatModel.builder()
                        .dashScopeApi(dashScopeApi)
                        .defaultOptions(DashScopeChatOptions.builder()
                                .withModel(DashScopeChatModel.DEFAULT_MODEL_NAME)
                                .withTemperature(0.3)
                                .withMaxToken(8000)
                                .withTopP(0.9)
                                .build())
                        .build();

                ToolCallback[] toolCallbacks = tools.getToolCallbacks();

                emitter.send(SseEmitter.event().name("message").data(SseMessage.content("正在读取告警并拆解任务...\n")));
                
                // 调用 AiOpsService 执行分析流程
                Optional<OverAllState> overAllStateOptional = aiOpsService.executeAiOpsAnalysis(chatModel, toolCallbacks);

                if (overAllStateOptional.isEmpty()) {
                    emitter.send(SseEmitter.event().name("message")
                            .data(SseMessage.error("多 Agent 编排未获取到有效结果"), MediaType.APPLICATION_JSON));
                    emitter.complete();
                    return;
                }

                OverAllState state = overAllStateOptional.get();
                logger.info("AI Ops 编排完成，开始提取最终报告...");

                // 提取最终报告
                Optional<String> finalReportOptional = aiOpsService.extractFinalReport(state);

                // 输出最终报告
                if (finalReportOptional.isPresent()) {
                    String finalReportText = finalReportOptional.get();
                    logger.info("提取到 Planner 最终报告，长度: {}", finalReportText.length());
                    
                    // 发送分隔线
                    emitter.send(SseEmitter.event().name("message")
                            .data(SseMessage.content("\n\n" + "=".repeat(60) + "\n"), MediaType.APPLICATION_JSON));
                    
                    // 发送完整的告警分析报告
                    emitter.send(SseEmitter.event().name("message")
                            .data(SseMessage.content("📋 **告警分析报告**\n\n"), MediaType.APPLICATION_JSON));
                    
                    int chunkSize = 50;
                    for (int i = 0; i < finalReportText.length(); i += chunkSize) {
                        int end = Math.min(i + chunkSize, finalReportText.length());
                        String chunk = finalReportText.substring(i, end);
                        
                        emitter.send(SseEmitter.event().name("message")
                                .data(SseMessage.content(chunk), MediaType.APPLICATION_JSON));
                    }
                    
                    // 发送结束分隔线
                    emitter.send(SseEmitter.event().name("message")
                            .data(SseMessage.content("\n" + "=".repeat(60) + "\n\n"), MediaType.APPLICATION_JSON));
                    
                    logger.info("最终报告已完整输出");
                } else {
                    logger.warn("未能提取到 Planner 最终报告");
                    emitter.send(SseEmitter.event().name("message")
                            .data(SseMessage.content("⚠️ 多 Agent 流程已完成，但未能生成最终报告。"), MediaType.APPLICATION_JSON));
                }

                emitter.send(SseEmitter.event().name("message").data(SseMessage.done(), MediaType.APPLICATION_JSON));
                emitter.complete();
                logger.info("AI Ops 多 Agent 编排完成");

            } catch (Exception e) {
                logger.error("AI Ops 多 Agent 协作失败", e);
                try {
                    emitter.send(SseEmitter.event().name("message")
                            .data(SseMessage.error("AI Ops 流程失败: " + e.getMessage()), MediaType.APPLICATION_JSON));
                } catch (IOException ex) {
                    logger.error("发送错误消息失败", ex);
                }
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }


    /**
     * 获取会话信息
     */
    /**
     * 商家端回复后同步上下文（保持 agent 会话视图与 DB 一致）
     */
    @PostMapping("/chat/context/sync")
    public ResponseEntity<ApiResponse<String>> syncContext(@RequestBody Map<String, Object> body) {
        try {
            String sessionId = body.get("sessionId") != null ? body.get("sessionId").toString() : null;
            if (sessionId == null) {
                return ResponseEntity.ok(ApiResponse.error("sessionId required"));
            }
            SessionInfo session = sessions.get(sessionId);
            if (session != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> msg = body.get("message") instanceof Map
                        ? (Map<String, Object>) body.get("message") : null;
                if (msg != null) {
                    String role = msg.get("role") != null ? msg.get("role").toString() : "assistant";
                    String content = msg.get("content") != null ? msg.get("content").toString() : "";
                    session.appendSingle(role, content);
                    logger.info("商家回复已同步到 agent 会话: sessionId={}", sessionId);
                }
            }
            return ResponseEntity.ok(ApiResponse.success("ok"));
        } catch (Exception e) {
            logger.warn("同步上下文失败: {}", e.getMessage());
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/chat/session/{sessionId}")
    public ResponseEntity<ApiResponse<SessionInfoResponse>> getSessionInfo(@PathVariable String sessionId) {
        try {
            logger.info("收到获取会话信息请求 - SessionId: {}", sessionId);

            SessionInfo session = sessions.get(sessionId);
            if (session != null) {
                SessionInfoResponse response = new SessionInfoResponse();
                response.setSessionId(sessionId);
                response.setMessagePairCount(session.getMessagePairCount());
                response.setCreateTime(session.createTime);
                return ResponseEntity.ok(ApiResponse.success(response));
            } else {
                return ResponseEntity.ok(ApiResponse.error("会话不存在"));
            }

        } catch (Exception e) {
            logger.error("获取会话信息失败", e);
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    // ==================== 辅助方法 ====================

    @SuppressWarnings("unchecked")
    private Map<String, Object> safeMap(Object obj) {
        return obj instanceof Map ? (Map<String, Object>) obj : Collections.emptyMap();
    }

    private String str(Object obj, String defaultVal) {
        return obj != null ? obj.toString() : defaultVal;
    }

    private Long lng(Object obj) {
        if (obj == null) return 0L;
        if (obj instanceof Number) return ((Number) obj).longValue();
        try { return Long.parseLong(obj.toString()); } catch (NumberFormatException e) { return 0L; }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, String>> parseHistory(Object historyObj) {
        if (!(historyObj instanceof List)) return null;
        List<?> rawList = (List<?>) historyObj;
        return rawList.stream().filter(Map.class::isInstance).map(item -> {
            Map<String, String> entry = new HashMap<>();
            Map<?, ?> m = (Map<?, ?>) item;
            entry.put("role", str(m.get("role"), "user"));
            entry.put("content", str(m.get("content"), ""));
            return entry;
        }).collect(Collectors.toList());
    }

    private SessionInfo getOrCreateSession(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = UUID.randomUUID().toString();
        }
        return sessions.computeIfAbsent(sessionId, SessionInfo::new);
    }

    // ==================== 内部类 ====================

    /**
     * 会话信息
     * 管理单个会话的历史消息，支持自动清理和线程安全
     */
    private static class SessionInfo {
        private final String sessionId;
        private final List<Map<String, String>> messageHistory;
        private final ConversationState conversationState;
        private final long createTime;
        private final ReentrantLock lock;

        public SessionInfo(String sessionId) {
            this.sessionId = sessionId;
            this.messageHistory = new ArrayList<>();
            this.conversationState = new ConversationState();
            this.createTime = System.currentTimeMillis();
            this.lock = new ReentrantLock();
        }

        /** 获取结构化对话状态 */
        public ConversationState getConversationState() {
            lock.lock();
            try {
                return conversationState;
            } finally {
                lock.unlock();
            }
        }

        /**
         * 添加一对消息（用户问题 + AI回复），同时更新 ConversationState
         */
        public void addMessage(String userQuestion, String aiAnswer) {
            lock.lock();
            try {
                Map<String, String> userMsg = new HashMap<>();
                userMsg.put("role", "user");
                userMsg.put("content", userQuestion);
                messageHistory.add(userMsg);

                Map<String, String> assistantMsg = new HashMap<>();
                assistantMsg.put("role", "assistant");
                assistantMsg.put("content", aiAnswer);
                messageHistory.add(assistantMsg);

                int maxMessages = MAX_WINDOW_SIZE * 2;
                while (messageHistory.size() > maxMessages) {
                    messageHistory.remove(0);
                    if (!messageHistory.isEmpty()) {
                        messageHistory.remove(0);
                    }
                }

                // 更新 ConversationState: 记录轮次和意图
                String detectedIntent = detectIntent(userQuestion, aiAnswer);
                conversationState.recordTurn(userQuestion, aiAnswer, detectedIntent);

                // 提取提到的商品实体
                extractEntities(userQuestion, aiAnswer);

                logger.debug("会话 {} 更新历史消息，当前消息对数: {}", 
                    sessionId, messageHistory.size() / 2);

            } finally {
                lock.unlock();
            }
        }

        /**
         * 获取历史消息（线程安全）
         * 返回副本以避免并发修改
         */
        public List<Map<String, String>> getHistory() {
            lock.lock();
            try {
                return new ArrayList<>(messageHistory);
            } finally {
                lock.unlock();
            }
        }

        /**
         * 清空历史消息
         */
        public void clearHistory() {
            lock.lock();
            try {
                messageHistory.clear();
                logger.info("会话 {} 历史消息已清空", sessionId);
            } finally {
                lock.unlock();
            }
        }

        /** 用 DB 中的历史替换内存历史（agent 重启后恢复上下文） */
        public void replaceHistory(List<Map<String, String>> dbHistory) {
            lock.lock();
            try {
                messageHistory.clear();
                messageHistory.addAll(dbHistory);
                logger.info("会话 {} 替换为DB历史, 共 {} 条", sessionId, dbHistory.size());
            } finally {
                lock.unlock();
            }
        }

        /** 追加单条消息（商家回复时同步） */
        public void appendSingle(String role, String content) {
            lock.lock();
            try {
                Map<String, String> msg = new HashMap<>();
                msg.put("role", role);
                msg.put("content", content);
                messageHistory.add(msg);
                // 保持窗口大小
                int maxMessages = MAX_WINDOW_SIZE * 2;
                while (messageHistory.size() > maxMessages) {
                    messageHistory.remove(0);
                }
            } finally {
                lock.unlock();
            }
        }

        /**
         * 获取当前消息对数
         */
        public int getMessagePairCount() {
            lock.lock();
            try {
                return messageHistory.size() / 2;
            } finally {
                lock.unlock();
            }
        }

        /** 简单意图检测 — 从用户消息和 AI 回复中推断意图类别 */
        private String detectIntent(String question, String answer) {
            String combined = (question + " " + answer).toLowerCase();
            if (combined.contains("售后") || combined.contains("退货") || combined.contains("退款")
                    || combined.contains("保修") || combined.contains("物流") || combined.contains("换货"))
                return "POST_SALES";
            if (combined.contains("多少钱") || combined.contains("价格") || combined.contains("有货")
                    || combined.contains("规格") || combined.contains("对比") || combined.contains("推荐")
                    || combined.contains("品牌"))
                return "PRE_SALES";
            if (combined.contains("投诉") || combined.contains("律师") || combined.contains("12315")
                    || combined.contains("升级") || combined.contains("人工"))
                return "ESCALATION";
            if (answer.contains("NEED_HUMAN"))
                return "ESCALATION";
            return "CHITCHAT";
        }

        /** 从消息中提取商品名等实体 */
        private void extractEntities(String question, String answer) {
            String combined = question + " " + answer;
            // 简单关键词提取
            String[] productHints = {"iPhone", "华为", "小米", "海澜之家", "耐克", "NIKE",
                    "iPad", "MacBook", "三文鱼", "文胸", "T恤", "面霜", "面膜"};
            for (String hint : productHints) {
                if (combined.contains(hint)) {
                    conversationState.addEntity(hint);
                }
            }
            // 提取订单号模式
            if (combined.matches(".*\\d{6,}.*")) {
                conversationState.addEntity("订单");
            }
        }
    }

    /**
     * 聊天请求
     */
    @Setter
    @Getter
    public static class ChatRequest {
        @com.fasterxml.jackson.annotation.JsonProperty(value = "Id")
        @com.fasterxml.jackson.annotation.JsonAlias({"id", "ID"})
        private String Id;

        @com.fasterxml.jackson.annotation.JsonProperty(value = "Question")
        @com.fasterxml.jackson.annotation.JsonAlias({"question", "QUESTION"})
        private String Question;

        @com.fasterxml.jackson.annotation.JsonProperty(value = "productName")
        @com.fasterxml.jackson.annotation.JsonAlias({"productName", "ProductName"})
        private String productName;
    }

    /**
     * 清空会话请求
     */
    @Setter
    @Getter
    public static class ClearRequest {
        @com.fasterxml.jackson.annotation.JsonProperty(value = "Id")
        @com.fasterxml.jackson.annotation.JsonAlias({"id", "ID"})
        private String Id;
    }

    // ==================== 内部类 ====================

    /**
     * 会话信息响应
     */
    @Setter
    @Getter
    public static class SessionInfoResponse {
        private String sessionId;
        private int messagePairCount;
        private long createTime;
    }

    /**
     * 统一聊天响应格式
     * 适用于所有普通返回模式的对话接口
     */
    @Setter
    @Getter
    public static class ChatResponse {
        private boolean success;
        private String answer;
        private String errorMessage;

        public static ChatResponse success(String answer) {
            ChatResponse response = new ChatResponse();
            response.setSuccess(true);
            response.setAnswer(answer);
            return response;
        }

        public static ChatResponse error(String errorMessage) {
            ChatResponse response = new ChatResponse();
            response.setSuccess(false);
            response.setErrorMessage(errorMessage);
            return response;
        }
    }

    /**
     * 统一 SSE 流式消息格式
     * 适用于所有 SSE 流式返回模式的对话接口
     */
    @Setter
    @Getter
    public static class SseMessage {
        private String type;  // content: 内容块, error: 错误, done: 完成
        private String data;

        public static SseMessage content(String data) {
            SseMessage message = new SseMessage();
            message.setType("content");
            message.setData(data);
            return message;
        }

        public static SseMessage error(String errorMessage) {
            SseMessage message = new SseMessage();
            message.setType("error");
            message.setData(errorMessage);
            return message;
        }

        public static SseMessage done() {
            SseMessage message = new SseMessage();
            message.setType("done");
            message.setData(null);
            return message;
        }
    }


    @Getter
    @Setter
    public static class ApiResponse<T> {
        private int code;
        private String message;
        private T data;

        public static <T> ApiResponse<T> success(T data) {
            ApiResponse<T> response = new ApiResponse<>();
            response.setCode(200);
            response.setMessage("success");
            response.setData(data);
            return response;
        }

        public static <T> ApiResponse<T> error(String message) {
            ApiResponse<T> response = new ApiResponse<>();
            response.setCode(500);
            response.setMessage(message);
            return response;
        }

    }
}
