package com.macro.mall.portal.component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * AI Agent 客户端
 * 调用 SuperBizAgent 服务进行智能问答
 */
@Slf4j
@Component
public class AgentClient {

    @Value("${agent.url:http://localhost:9900/api/chat}")
    private String agentUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 向 AI Agent 提问
     * @param sessionId 会话ID
     * @param question 用户问题
     * @return Agent 回复，如果返回 NEED_HUMAN 前缀则表示需要人工处理
     */
    public String ask(String sessionId, String question) {
        try {
            Map<String, String> body = new HashMap<>();
            body.put("Id", sessionId);
            body.put("Question", question);
            body.put("question", question);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

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
            log.warn("Agent调用失败: sessionId={}, question={}, error={}",
                    sessionId, question, e.getMessage());
        }
        return "NEED_HUMAN: Agent服务暂不可用";
    }
}
