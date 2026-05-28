package org.example.agent.tool.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class ReturnOrderTools {

    private static final Logger logger = LoggerFactory.getLogger(ReturnOrderTools.class);

    public static final String TOOL_GET_RETURN_APPLY_DETAIL = "getApplyDetail";
    public static final String TOOL_GET_ORDER_RECEIVE_TIME = "getOrderTime";

    @Value("${mall.admin.base-url:http://localhost:8080}")
    private String mallAdminBaseUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    @Tool(name = TOOL_GET_RETURN_APPLY_DETAIL,
          description = "查询退货申请详情。输入applyId，返回完整申请信息（商品属性、退货原因、用户信息）")
    public String getApplyDetail(
            @ToolParam(description = "退货申请ID") Long applyId) {
        logger.info("查询退货申请详情: applyId={}", applyId);
        try {
            String result = restTemplate.getForObject(
                    mallAdminBaseUrl + "/returnApply/{id}", String.class, applyId);
            logger.info("退货申请详情查询成功: applyId={}", applyId);
            return result;
        } catch (Exception e) {
            logger.error("查询退货申请详情失败: applyId={}, error={}", applyId, e.getMessage());
            return "{\"error\": \"查询退货申请失败: " + e.getMessage() + "\"}";
        }
    }

    @Tool(name = TOOL_GET_ORDER_RECEIVE_TIME,
          description = "查询订单收货时间。输入orderId，返回收货时间、发货时间、支付时间等")
    public String getOrderTime(
            @ToolParam(description = "订单ID") Long orderId) {
        logger.info("查询订单收货时间: orderId={}", orderId);
        try {
            String result = restTemplate.getForObject(
                    mallAdminBaseUrl + "/returnApply/audit/order/{orderId}", String.class, orderId);
            logger.info("订单收货时间查询成功: orderId={}", orderId);
            return result;
        } catch (Exception e) {
            logger.error("查询订单收货时间失败: orderId={}, error={}", orderId, e.getMessage());
            return "{\"error\": \"查询订单信息失败: " + e.getMessage() + "\"}";
        }
    }
}
