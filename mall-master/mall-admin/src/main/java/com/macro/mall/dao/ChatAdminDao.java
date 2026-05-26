package com.macro.mall.dao;

import com.macro.mall.model.*;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

public interface ChatAdminDao {
    List<Map<String, Object>> getAllSessions();
    List<Map<String, Object>> getMessages(@Param("sessionId") Long sessionId);
    int insertMessage(Map<String, Object> msg);
    int takeSession(@Param("id") Long id, @Param("adminId") Long adminId, @Param("adminName") String adminName);
    int closeSession(@Param("id") Long id);
    int markRead(@Param("sessionId") Long sessionId, @Param("senderType") Integer senderType);
    int updateLastMsg(@Param("id") Long id, @Param("msg") String msg);
}
