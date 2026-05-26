package com.macro.mall.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.dao.ChatAdminDao;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Api(tags = "ChatAdminController")
@Controller
@RequestMapping("/chat")
public class ChatAdminController {

    @Autowired
    private ChatAdminDao chatAdminDao;

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

        return CommonResult.success(msg);
    }

    @ApiOperation("关闭会话")
    @PostMapping("/session/close/{sessionId}")
    @ResponseBody
    public CommonResult<String> closeSession(@PathVariable Long sessionId) {
        chatAdminDao.closeSession(sessionId);
        return CommonResult.success("会话已关闭");
    }
}
