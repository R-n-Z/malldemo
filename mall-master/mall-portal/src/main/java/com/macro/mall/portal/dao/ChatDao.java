package com.macro.mall.portal.dao;

import com.macro.mall.portal.domain.ChatMessage;
import com.macro.mall.portal.domain.ChatSession;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ChatDao {

    ChatSession getSessionByMemberAndProduct(@Param("memberId") Long memberId, @Param("productId") Long productId);

    int createSession(ChatSession session);

    int updateSessionStatus(@Param("id") Long id, @Param("status") Integer status);

    int updateSessionLastMsg(@Param("id") Long id, @Param("lastMessage") String lastMessage);

    List<ChatSession> getSessionList(@Param("adminId") Long adminId);

    List<ChatSession> getAllSessions();

    int insertMessage(ChatMessage message);

    List<ChatMessage> getMessages(@Param("sessionId") Long sessionId);

    int markRead(@Param("sessionId") Long sessionId, @Param("senderType") Integer senderType);

    ChatSession getSessionById(@Param("id") Long id);

    int takeSession(@Param("id") Long id, @Param("adminId") Long adminId, @Param("adminName") String adminName);
}
