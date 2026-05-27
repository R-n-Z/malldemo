package com.macro.mall.portal.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.model.UmsMember;
import com.macro.mall.portal.domain.ChatMessage;
import com.macro.mall.portal.domain.ChatSession;
import com.macro.mall.portal.service.ChatService;
import com.macro.mall.portal.service.UmsMemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@Tag(name = "ChatController", description = "客服聊天管理")
@RestController
@RequestMapping("/chat")
public class ChatController {

    @Autowired
    private ChatService chatService;

    @Autowired
    private UmsMemberService memberService;

    @Operation(summary = "创建或获取客服会话")
    @PostMapping("/session/create")
    public CommonResult<ChatSession> createSession(@RequestBody Map<String, Object> body) {
        UmsMember member = memberService.getCurrentMember();
        if (member == null) {
            return CommonResult.unauthorized(null);
        }
        Long productId = body.get("productId") != null ? Long.valueOf(body.get("productId").toString()) : 0L;
        String productName = body.get("productName") != null ? body.get("productName").toString() : "";
        String productPic = body.get("productPic") != null ? body.get("productPic").toString() : "";
        ChatSession session = chatService.getOrCreateSession(
                member.getId(), member.getUsername(), productId, productName, productPic);
        return CommonResult.success(session);
    }

    @Operation(summary = "获取会话消息历史")
    @GetMapping("/messages/{sessionId}")
    public CommonResult<List<ChatMessage>> getMessages(@PathVariable Long sessionId) {
        List<ChatMessage> messages = chatService.getMessages(sessionId, 1);
        return CommonResult.success(messages);
    }

    @Operation(summary = "发送消息")
    @PostMapping("/send")
    public CommonResult<ChatMessage> sendMessage(@RequestBody Map<String, Object> body) {
        UmsMember member = memberService.getCurrentMember();
        if (member == null) {
            return CommonResult.unauthorized(null);
        }
        Long sessionId = Long.valueOf(body.get("sessionId").toString());
        String content = body.get("content").toString();
        ChatMessage message = chatService.sendMessage(sessionId, 1, member.getId(),
                member.getUsername(), content);
        return CommonResult.success(message);
    }

    @Operation(summary = "关闭会话")
    @PostMapping("/session/close/{sessionId}")
    public CommonResult<String> closeSession(@PathVariable Long sessionId) {
        chatService.closeSession(sessionId);
        return CommonResult.success("会话已关闭");
    }

    @Operation(summary = "撤回消息")
    @PostMapping("/recall/{messageId}")
    public CommonResult<String> recallMessage(@PathVariable Long messageId) {
        UmsMember member = memberService.getCurrentMember();
        if (member == null) {
            return CommonResult.unauthorized(null);
        }
        int rows = chatService.recallMessage(messageId, 1); // 1=用户端撤回
        if (rows > 0) {
            return CommonResult.success("消息已撤回");
        }
        return CommonResult.failed("撤回失败，超过2分钟的消息无法撤回");
    }
}
