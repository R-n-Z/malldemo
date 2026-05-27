package com.macro.mall.portal.component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * AI Agent 客户端
 * 构建统一的 ContextEnvelope 确保 agent 能识别：谁→对哪个商品→问了什么
 */
@Slf4j
@Component
public class AgentClient {

    @Value("${agent.url:http://localhost:9900/api/chat}")
    private String agentUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 向 AI Agent 提问（完整上下文版本）
     * @param sessionId 数据库会话ID
     * @param question 用户问题
     * @param memberId 用户ID
     * @param memberName 用户名
     * @param productId 商品ID
     * @param productName 商品名
     * @param productPic 商品图片
     * @param history 会话历史（DB中最近的消息，按时间正序）
     * @return Agent 回复，NEED_HUMAN 前缀表示需人工处理
     */
    public String ask(Long sessionId, String question,
                      Long memberId, String memberName,
                      Long productId, String productName, String productPic,
                      List<Map<String, String>> history) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("sessionId", "chat_" + sessionId);
            body.put("conversationId", sessionId);

            Map<String, Object> user = new LinkedHashMap<>();
            user.put("userId", memberId);
            user.put("username", memberName);
            body.put("user", user);

            Map<String, Object> product = new LinkedHashMap<>();
            product.put("productId", productId);
            if (productName != null) product.put("productName", productName);
            if (productPic != null) product.put("productPic", productPic);
            body.put("product", product);

            Map<String, String> message = new LinkedHashMap<>();
            message.put("role", "user");
            message.put("content", question);
            body.put("message", message);

            if (history != null && !history.isEmpty()) {
                body.put("history", history);
            }

            // 兼容旧格式字段
            body.put("Id", "chat_" + sessionId);
            body.put("Question", question);
            if (productName != null) body.put("productName", productName);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(agentUrl, request, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode data = root.get("data");
                if (data != null && data.has("answer")) {
                    String answer = data.get("answer").asText();
                    log.info("Agent回复: sessionId={}, answer={}", sessionId,
                            answer.length() > 50 ? answer.substring(0, 50) + "..." : answer);
                    return answer;
                }
            }
        } catch (Exception e) {
            log.warn("Agent调用失败: sessionId={}, error={}", sessionId, e.getMessage());
        }
        return "NEED_HUMAN: Agent服务暂不可用";
    }
}
