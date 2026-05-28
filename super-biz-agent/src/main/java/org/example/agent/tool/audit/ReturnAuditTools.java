package org.example.agent.tool.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class ReturnAuditTools {

    private static final Logger logger = LoggerFactory.getLogger(ReturnAuditTools.class);

    public static final String TOOL_GET_USER_RETURN_HISTORY = "getUserHistory";
    public static final String TOOL_GET_USER_REJECTION_COUNT = "getRejectCount";

    @Value("${mall.admin.base-url:http://localhost:8080}")
    private String mallAdminBaseUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Tool(name = TOOL_GET_USER_RETURN_HISTORY,
          description = "查询用户历史退货申请记录。输入用户名，返回最近10条退货申请")
    public String getUserHistory(
            @ToolParam(description = "用户名") String memberUsername) {
        logger.info("查询用户退货历史: memberUsername={}", memberUsername);
        try {
            String result = restTemplate.getForObject(
                    mallAdminBaseUrl + "/returnApply/audit/history?memberUsername={username}&limit=10",
                    String.class, memberUsername);
            return result;
        } catch (Exception e) {
            return "{\"error\": \"查询退货历史失败\"}";
        }
    }

    @Tool(name = TOOL_GET_USER_REJECTION_COUNT,
          description = "查询用户最近连续被拒绝的退货申请次数。输入用户名，返回连续拒绝次数")
    public String getRejectCount(
            @ToolParam(description = "用户名") String memberUsername) {
        try {
            String result = restTemplate.getForObject(
                    mallAdminBaseUrl + "/returnApply/audit/history?memberUsername={username}&limit=10",
                    String.class, memberUsername);
            int consecutive = countConsecutiveRejections(result);
            return String.format("{\"memberUsername\": \"%s\", \"consecutiveRejections\": %d}",
                    memberUsername, consecutive);
        } catch (Exception e) {
            return "{\"error\": \"查询失败\", \"consecutiveRejections\": -1}";
        }
    }

    private int countConsecutiveRejections(String jsonResponse) {
        int count = 0;
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            JsonNode data = root.has("data") ? root.get("data") : root;
            if (data.isArray()) {
                for (JsonNode item : data) {
                    int status = item.has("status") ? item.get("status").asInt() : -1;
                    if (status == 3) count++; else break;
                }
            }
        } catch (Exception e) { logger.warn("解析退货历史失败: {}", e.getMessage()); }
        return count;
    }
}
