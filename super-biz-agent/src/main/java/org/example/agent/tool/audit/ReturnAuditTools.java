package org.example.agent.tool.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ReturnAuditTools {

    private static final Logger logger = LoggerFactory.getLogger(ReturnAuditTools.class);

    public static final String TOOL_GET_USER_RETURN_HISTORY = "getUserReturnHistory";
    public static final String TOOL_GET_USER_REJECTION_COUNT = "getUserRejectionCount";

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ReturnAuditTools(RestClient mallAdminRestClient) {
        this.restClient = mallAdminRestClient;
    }

    @Tool(name = TOOL_GET_USER_RETURN_HISTORY,
          description = "查询用户历史退货申请记录。输入用户名，返回最近10条退货申请的ID、商品名、退货原因、状态、创建时间")
    public String getUserReturnHistory(
            @ToolParam(description = "用户名") String memberUsername) {
        logger.info("查询用户退货历史: memberUsername={}", memberUsername);
        try {
            String result = restClient.get()
                    .uri("/returnApply/audit/history?memberUsername={username}&limit=10", memberUsername)
                    .retrieve()
                    .body(String.class);
            logger.info("用户退货历史查询成功: memberUsername={}", memberUsername);
            return result;
        } catch (Exception e) {
            logger.error("查询用户退货历史失败: memberUsername={}, error={}", memberUsername, e.getMessage());
            return "{\"error\": \"查询退货历史失败: " + e.getMessage() + "\"}";
        }
    }

    @Tool(name = TOOL_GET_USER_REJECTION_COUNT,
          description = "查询用户最近连续被拒绝的退货申请次数。输入用户名，返回连续拒绝次数")
    public String getUserRejectionCount(
            @ToolParam(description = "用户名") String memberUsername) {
        logger.info("查询用户连续拒绝次数: memberUsername={}", memberUsername);
        try {
            String result = restClient.get()
                    .uri("/returnApply/audit/history?memberUsername={username}&limit=10", memberUsername)
                    .retrieve()
                    .body(String.class);

            int consecutive = countConsecutiveRejections(result);
            return String.format(
                    "{\"memberUsername\": \"%s\", \"consecutiveRejections\": %d}",
                    memberUsername, consecutive);
        } catch (Exception e) {
            logger.error("查询拒绝次数失败: memberUsername={}, error={}", memberUsername, e.getMessage());
            return "{\"error\": \"查询失败: " + e.getMessage() + "\", \"consecutiveRejections\": -1}";
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
                    if (status == 3) {
                        count++;
                    } else {
                        break;
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("解析退货历史失败: {}", e.getMessage());
        }
        return count;
    }
}
