package com.macro.mall.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.dao.ChatAdminDao;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Api(tags = "ChatAdminController")
@Controller
@RequestMapping("/chat")
public class ChatAdminController {

    @Autowired
    private ChatAdminDao chatAdminDao;

    @Value("${agent.url:http://localhost:9900/api/chat}")
    private String agentUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    @ApiOperation("获取所有待处理会话列表")
    @GetMapping("/sessions")
    @ResponseBody
    public CommonResult<List<Map<String, Object>>> getSessions() {
        List<Map<String, Object>> sessions = chatAdminDao.getAllSessions();
        return CommonResult.success(sessions);
    }

    @ApiOperation("接单")
    @PostMapping("/session/take/{sessionId}")
    @ResponseBody
    public CommonResult<String> takeSession(@PathVariable Long sessionId) {
        chatAdminDao.takeSession(sessionId, 1L, "客服");
        return CommonResult.success("接单成功");
    }

    @ApiOperation("获取消息历史")
    @GetMapping("/messages/{sessionId}")
    @ResponseBody
    public CommonResult<List<Map<String, Object>>> getMessages(@PathVariable Long sessionId) {
        List<Map<String, Object>> messages = chatAdminDao.getMessages(sessionId);
        chatAdminDao.markRead(sessionId, 2);
        return CommonResult.success(messages);
    }

    @ApiOperation("发送消息")
    @PostMapping("/send")
    @ResponseBody
    public CommonResult<Map<String, Object>> sendMessage(@RequestBody Map<String, Object> body) {
        Long sessionId = Long.valueOf(body.get("sessionId").toString());
        String content = body.get("content").toString();

        Map<String, Object> msg = new HashMap<>();
        msg.put("sessionId", sessionId);
        msg.put("senderType", 2);
        msg.put("senderId", 1L);
        msg.put("senderName", "客服");
        msg.put("content", content);
        chatAdminDao.insertMessage(msg);

        String summary = content.length() > 50 ? content.substring(0, 50) + "..." : content;
        chatAdminDao.updateLastMsg(sessionId, summary);

        // 异步通知 agent 同步上下文（商家回复后 agent 需要知道对话进展）
        CompletableFuture.runAsync(() -> {
            try {
                Map<String, Object> syncBody = new HashMap<>();
                syncBody.put("sessionId", "chat_" + sessionId);
                syncBody.put("action", "admin_reply");
                Map<String, String> adminMsg = new HashMap<>();
                adminMsg.put("role", "assistant");
                adminMsg.put("content", content);
                syncBody.put("message", adminMsg);
                restTemplate.postForEntity(agentUrl + "/context/sync", syncBody, String.class);
            } catch (Exception ignored) {
                // agent 不可用时静默忽略
            }
        });

        return CommonResult.success(msg);
    }

    @ApiOperation("关闭会话")
    @PostMapping("/session/close/{sessionId}")
    @ResponseBody
    public CommonResult<String> closeSession(@PathVariable Long sessionId) {
        chatAdminDao.closeSession(sessionId);
        return CommonResult.success("会话已关闭");
    }

    @ApiOperation("撤回消息")
    @PostMapping("/recall/{messageId}")
    @ResponseBody
    public CommonResult<String> recallMessage(@PathVariable Long messageId) {
        int rows = chatAdminDao.recallMessage(messageId, 2); // 2=客服端撤回
        if (rows > 0) {
            return CommonResult.success("消息已撤回");
        }
        return CommonResult.failed("撤回失败，超过2分钟的消息无法撤回");
    }
}
