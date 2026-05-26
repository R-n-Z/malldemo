package com.macro.mall.portal.service;

import com.macro.mall.portal.domain.ChatMessage;
import com.macro.mall.portal.domain.ChatSession;

import java.util.List;

public interface ChatService {

    ChatSession getOrCreateSession(Long memberId, String memberName, Long productId, String productName, String productPic);

    ChatSession getSession(Long sessionId);

    List<ChatSession> getSessionList(Long adminId);

    List<ChatSession> getAllSessions();

    ChatMessage sendMessage(Long sessionId, Integer senderType, Long senderId,
                            String senderName, String content);

    List<ChatMessage> getMessages(Long sessionId, Integer senderType);

    void takeSession(Long sessionId, Long adminId, String adminName);

    void closeSession(Long sessionId);
}
