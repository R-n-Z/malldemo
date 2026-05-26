package com.macro.mall.portal.service.impl;

import com.macro.mall.portal.component.AgentClient;
import com.macro.mall.portal.dao.ChatDao;
import com.macro.mall.portal.domain.ChatMessage;
import com.macro.mall.portal.domain.ChatSession;
import com.macro.mall.portal.service.ChatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class ChatServiceImpl implements ChatService {

    @Autowired
    private ChatDao chatDao;

    @Autowired(required = false)
    private AgentClient agentClient;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Override
    public ChatSession getOrCreateSession(Long memberId, String memberName,
                                           Long productId, String productName, String productPic) {
        ChatSession session = chatDao.getSessionByMemberAndProduct(memberId, productId);
        if (session == null) {
            session = new ChatSession();
            session.setMemberId(memberId);
            session.setMemberName(memberName);
            session.setProductId(productId);
            session.setProductName(productName);
            session.setProductPic(productPic);
            chatDao.createSession(session);
            log.info("新客服会话: sessionId={}, memberId={}, productId={}", session.getId(), memberId, productId);
            messagingTemplate.convertAndSend("/topic/chat/newSession", session);
        }
        return session;
    }

    @Override
    public ChatSession getSession(Long sessionId) {
        return chatDao.getSessionById(sessionId);
    }

    @Override
    public List<ChatSession> getSessionList(Long adminId) {
        if (adminId != null) {
            return chatDao.getSessionList(adminId);
        }
        return chatDao.getAllSessions();
    }

    @Override
    public List<ChatSession> getAllSessions() {
        return chatDao.getAllSessions();
    }

    @Override
    public ChatMessage sendMessage(Long sessionId, Integer senderType, Long senderId,
                                    String senderName, String content) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setSenderType(senderType);
        message.setSenderId(senderId);
        message.setSenderName(senderName);
        message.setContent(content);
        chatDao.insertMessage(message);

        chatDao.updateSessionLastMsg(sessionId, content.length() > 50
                ? content.substring(0, 50) + "..." : content);

        messagingTemplate.convertAndSend("/topic/chat/" + sessionId, message);

        // 如果是用户消息，异步调用 Agent 自动回复
        if (senderType == 1 && agentClient != null) {
            CompletableFuture.runAsync(() -> {
                try {
                    ChatSession session = chatDao.getSessionById(sessionId);
                    String answer = agentClient.ask("chat_" + sessionId, content);
                    if (answer != null && !answer.startsWith("NEED_HUMAN")) {
                        ChatMessage reply = new ChatMessage();
                        reply.setSessionId(sessionId);
                        reply.setSenderType(2); // 客服
                        reply.setSenderId(0L);
                        reply.setSenderName("AI客服");
                        reply.setContent(answer);
                        chatDao.insertMessage(reply);
                        chatDao.updateSessionLastMsg(sessionId,
                                answer.length() > 50 ? answer.substring(0, 50) + "..." : answer);
                        messagingTemplate.convertAndSend("/topic/chat/" + sessionId, reply);
                        log.info("Agent自动回复: sessionId={}", sessionId);
                    }
                } catch (Exception e) {
                    log.warn("Agent自动回复异常: sessionId={}", sessionId, e);
                }
            });
        }

        return message;
    }

    @Override
    public List<ChatMessage> getMessages(Long sessionId, Integer senderType) {
        List<ChatMessage> messages = chatDao.getMessages(sessionId);
        if (senderType != null) {
            chatDao.markRead(sessionId, senderType);
        }
        return messages;
    }

    @Override
    public void takeSession(Long sessionId, Long adminId, String adminName) {
        chatDao.takeSession(sessionId, adminId, adminName);
    }

    @Override
    public void closeSession(Long sessionId) {
        chatDao.updateSessionStatus(sessionId, 2);
    }
}
