package org.example.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 统一上下文请求对象
 * 确保每次对话 Agent 都能识别：谁（user）→ 对哪个商品（product）→ 问了什么（message）
 */
@Setter
@Getter
public class ContextEnvelope {

    /** 会话标识，格式 "chat_{dbSessionId}"，用于 agent 内存会话管理 */
    private String sessionId;

    /** 数据库会话主键 */
    private Long conversationId;

    /** 用户信息 */
    private UserInfo user;

    /** 商品信息 */
    private ProductInfo product;

    /** 当前消息 */
    private MessageInfo message;

    /** 历史消息（从 DB 加载），按时间正序 */
    private List<HistoryMessage> history = new ArrayList<>();

    // ======== 子结构 ========

    @Setter
    @Getter
    public static class UserInfo {
        private Long userId;
        private String username;

        public UserInfo() {}

        public UserInfo(Long userId, String username) {
            this.userId = userId;
            this.username = username;
        }
    }

    @Setter
    @Getter
    public static class ProductInfo {
        private Long productId;
        private String productName;
        private String productPic;

        public ProductInfo() {}

        public ProductInfo(Long productId, String productName, String productPic) {
            this.productId = productId;
            this.productName = productName;
            this.productPic = productPic;
        }
    }

    @Setter
    @Getter
    public static class MessageInfo {
        /** user 或 assistant */
        private String role;
        private String content;

        public MessageInfo() {}

        public MessageInfo(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    @Setter
    @Getter
    public static class HistoryMessage {
        private String role;
        private String content;

        public HistoryMessage() {}

        public HistoryMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    // ======== 工厂方法 ========

    /** 构建 agent 日志可读的摘要 */
    public String contextSummary() {
        StringBuilder sb = new StringBuilder();
        if (user != null && user.userId != null) {
            sb.append("用户ID：").append(user.userId);
            if (user.username != null) sb.append("（").append(user.username).append("）");
            sb.append("；");
        }
        if (product != null && product.productId != null) {
            sb.append("商品ID：").append(product.productId);
            if (product.productName != null) sb.append("（").append(product.productName).append("）");
            sb.append("；");
        }
        if (conversationId != null) {
            sb.append("会话ID：").append(conversationId);
        }
        return sb.toString();
    }
}
