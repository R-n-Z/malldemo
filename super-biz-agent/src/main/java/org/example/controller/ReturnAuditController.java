package org.example.controller;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.OverAllState;
import org.example.dto.ReturnAuditRequest;
import org.example.dto.ReturnAuditResponse;
import org.example.service.ChatService;
import org.example.service.ReturnAgentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 退货审核 Agent 控制器
 */
@RestController
@RequestMapping("/api")
public class ReturnAuditController {

    private static final Logger logger = LoggerFactory.getLogger(ReturnAuditController.class);

    @Autowired
    private ReturnAgentService returnAgentService;

    @Autowired
    private ChatService chatService;

    @Autowired
    private ToolCallbackProvider tools;

    private final ExecutorService executor = Executors.newCachedThreadPool();

    /**
     * 退货审核接口（同步返回）
     */
    @PostMapping("/return/audit")
    public ReturnAuditResponse audit(@RequestBody ReturnAuditRequest request) {
        try {
            logger.info("收到退货审核请求: applyId={}", request.getApplyId());

            DashScopeApi dashScopeApi = chatService.createDashScopeApi();
            DashScopeChatModel chatModel = DashScopeChatModel.builder()
                    .dashScopeApi(dashScopeApi)
                    .defaultOptions(DashScopeChatOptions.builder()
                            .withModel(DashScopeChatModel.DEFAULT_MODEL_NAME)
                            .withTemperature(0.3)
                            .withMaxToken(4000)
                            .withTopP(0.9)
                            .build())
                    .build();

            ToolCallback[] toolCallbacks = tools.getToolCallbacks();

            Optional<OverAllState> state = returnAgentService.executeReturnAudit(
                    chatModel, toolCallbacks, request.getApplyId());

            if (state.isEmpty()) {
                logger.warn("退货审核未获得结果: applyId={}", request.getApplyId());
                return ReturnAuditResponse.error("多 Agent 编排未获取到有效结果");
            }

            Optional<String> reportOpt = returnAgentService.extractAuditReport(state.get());

            if (reportOpt.isEmpty()) {
                return ReturnAuditResponse.error("未能提取审核报告");
            }

            String report = reportOpt.get();
            logger.info("退货审核完成: applyId={}, 报告长度={}", request.getApplyId(), report.length());

            return parseAuditResult(request.getApplyId(), report);

        } catch (Exception e) {
            logger.error("退货审核失败: applyId={}", request.getApplyId(), e);
            return ReturnAuditResponse.error(e.getMessage());
        }
    }

    /**
     * 退货审核接口（SSE 流式）
     */
    @PostMapping(value = "/return/audit/stream", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter auditStream(@RequestBody ReturnAuditRequest request) {
        SseEmitter emitter = new SseEmitter(300000L);

        executor.execute(() -> {
            try {
                logger.info("收到退货审核流式请求: applyId={}", request.getApplyId());

                DashScopeApi dashScopeApi = chatService.createDashScopeApi();
                DashScopeChatModel chatModel = DashScopeChatModel.builder()
                        .dashScopeApi(dashScopeApi)
                        .defaultOptions(DashScopeChatOptions.builder()
                                .withModel(DashScopeChatModel.DEFAULT_MODEL_NAME)
                                .withTemperature(0.3)
                                .withMaxToken(4000)
                                .withTopP(0.9)
                                .build())
                        .build();

                ToolCallback[] toolCallbacks = tools.getToolCallbacks();

                emitter.send(sseEvent("退货审核 Agent 启动，开始分析退货申请 #" + request.getApplyId() + "...\n"));

                Optional<OverAllState> state = returnAgentService.executeReturnAudit(
                        chatModel, toolCallbacks, request.getApplyId());

                if (state.isEmpty()) {
                    emitter.send(sseEvent("审核未获得有效结果"));
                    emitter.complete();
                    return;
                }

                Optional<String> reportOpt = returnAgentService.extractAuditReport(state.get());

                if (reportOpt.isEmpty()) {
                    emitter.send(sseEvent("未能提取审核报告"));
                    emitter.complete();
                    return;
                }

                String report = reportOpt.get();
                emitter.send(sseEvent("\n" + "=".repeat(60) + "\n"));
                emitter.send(sseEvent("退货审核报告\n\n"));

                int chunkSize = 50;
                for (int i = 0; i < report.length(); i += chunkSize) {
                    int end = Math.min(i + chunkSize, report.length());
                    emitter.send(sseEvent(report.substring(i, end)));
                }

                emitter.send(sseEvent("\n" + "=".repeat(60) + "\n"));
                emitter.send(SseEmitter.event().name("message")
                        .data("{\"type\":\"done\"}", MediaType.APPLICATION_JSON));
                emitter.complete();

                logger.info("退货审核流式输出完成: applyId={}", request.getApplyId());

            } catch (Exception e) {
                logger.error("退货审核流式执行失败: applyId={}", request.getApplyId(), e);
                try {
                    emitter.send(SseEmitter.event().name("message")
                            .data("{\"type\":\"error\",\"data\":\"" + e.getMessage() + "\"}",
                                    MediaType.APPLICATION_JSON));
                } catch (IOException ex) {
                    logger.error("发送错误消息失败", ex);
                }
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    // ==================== 辅助方法 ====================

    private SseEmitter.SseEventBuilder sseEvent(String content) {
        return SseEmitter.event().name("message")
                .data("{\"type\":\"content\",\"data\":\"" + escapeJson(content) + "\"}",
                        MediaType.APPLICATION_JSON);
    }

    private String escapeJson(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    /**
     * 从审核报告中解析审计结果，更新拒绝计数
     */
    private ReturnAuditResponse parseAuditResult(Long applyId, String report) {
        String lowerReport = report.toLowerCase();
        boolean passed = lowerReport.contains("通过") && !lowerReport.contains("拒绝");
        boolean needHuman = lowerReport.contains("人工介入") || lowerReport.contains("转人工")
                || lowerReport.contains("needhumansupport");

        String reason = "";
        if (passed) {
            reason = "退货申请审核通过";
        } else if (needHuman) {
            reason = "连续多次被拒，已转人工客服跟进处置";
        } else {
            reason = "退货申请审核未通过";
        }

        String memberUsername = extractMemberUsername(report);

        if (passed) {
            if (memberUsername != null) returnAgentService.updateRejectionCount(memberUsername, true);
            return ReturnAuditResponse.passed(applyId, reason, report);
        } else {
            if (memberUsername != null) returnAgentService.updateRejectionCount(memberUsername, false);
            int rejectCount = memberUsername != null ? returnAgentService.getRejectionCount(memberUsername) : 0;
            return ReturnAuditResponse.rejected(applyId, reason, needHuman, rejectCount, report);
        }
    }

    private String extractMemberUsername(String report) {
        if (report == null) return null;
        int idx = report.indexOf("memberUsername");
        if (idx < 0) {
            idx = report.indexOf("用户名");
        }
        if (idx >= 0) {
            int start = report.indexOf(":", idx);
            if (start < 0) start = report.indexOf("：", idx);
            if (start >= 0) {
                int end = report.indexOf("\n", start);
                if (end < 0) end = Math.min(start + 50, report.length());
                return report.substring(start + 1, end).trim();
            }
        }
        return null;
    }
}
