package org.example.agent.tool.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ReturnOrderTools {

    private static final Logger logger = LoggerFactory.getLogger(ReturnOrderTools.class);

    public static final String TOOL_GET_RETURN_APPLY_DETAIL = "getReturnApplyDetail";
    public static final String TOOL_GET_ORDER_RECEIVE_TIME = "getOrderReceiveTime";

    private final RestClient restClient;

    public ReturnOrderTools(RestClient mallAdminRestClient) {
        this.restClient = mallAdminRestClient;
    }

    @Tool(name = TOOL_GET_RETURN_APPLY_DETAIL,
          description = "查询退货申请详情。输入退货申请ID，返回完整申请信息（含商品属性、退货原因、用户信息等）")
    public String getReturnApplyDetail(
            @ToolParam(description = "退货申请ID") Long applyId) {
        logger.info("查询退货申请详情: applyId={}", applyId);
        try {
            String result = restClient.get()
                    .uri("/returnApply/{id}", applyId)
                    .retrieve()
                    .body(String.class);
            logger.info("退货申请详情查询成功: applyId={}", applyId);
            return result;
        } catch (Exception e) {
            logger.error("查询退货申请详情失败: applyId={}, error={}", applyId, e.getMessage());
            return "{\"error\": \"查询退货申请失败: " + e.getMessage() + "\"}";
        }
    }

    @Tool(name = TOOL_GET_ORDER_RECEIVE_TIME,
          description = "查询订单收货时间。输入订单ID，返回订单的收货时间、发货时间、支付时间等关键时间节点")
    public String getOrderReceiveTime(
            @ToolParam(description = "订单ID") Long orderId) {
        logger.info("查询订单收货时间: orderId={}", orderId);
        try {
            String result = restClient.get()
                    .uri("/returnApply/audit/order/{orderId}", orderId)
                    .retrieve()
                    .body(String.class);
            logger.info("订单收货时间查询成功: orderId={}", orderId);
            return result;
        } catch (Exception e) {
            logger.error("查询订单收货时间失败: orderId={}, error={}", orderId, e.getMessage());
            return "{\"error\": \"查询订单信息失败: " + e.getMessage() + "\"}";
        }
    }
}
