package com.macro.mall.portal.component;

import com.macro.mall.portal.component.AlertManager.AlertInfo;
import com.macro.mall.portal.component.AlertManager.AlertLevel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * 钉钉告警通知器
 */
@Slf4j
@Component
public class DingTalkAlertNotifier implements AlertNotificationService.AlertNotifier {

    @Value("${alert.dingtalk.enabled:false}")
    private boolean enabled;

    @Value("${alert.dingtalk.webhook:}")
    private String webhook;

    @Value("${alert.dingtalk.secret:}")
    private String secret;

    @Value("${alert.dingtalk.mobile:}")
    private String mobile;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public String getChannelName() {
        return "dingtalk";
    }

    @Override
    public void send(AlertInfo alert) {
        if (!enabled) {
            log.debug("钉钉告警通知已禁用");
            return;
        }

        if (webhook == null || webhook.isEmpty()) {
            log.warn("钉钉Webhook未配置，无法发送钉钉告警");
            return;
        }

        try {
            // 构建消息内容
            Map<String, Object> message = buildMessage(alert);

            // 如果配置了加密密钥，添加签名
            if (secret != null && !secret.isEmpty()) {
                addSignature(message);
            }

            // 发送请求
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(message, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(webhook, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                log.info("钉钉告警发送成功: {}", alert.getRuleName());
            } else {
                log.error("钉钉告警发送失败: {}", response.getBody());
            }
        } catch (Exception e) {
            log.error("发送钉钉告警失败", e);
        }
    }

    private Map<String, Object> buildMessage(AlertInfo alert) {
        Map<String, Object> message = new HashMap<>();
        message.put("msgtype", "markdown");

        Map<String, Object> markdown = new HashMap<>();
        markdown.put("title", formatTitle(alert));
        markdown.put("text", formatContent(alert));

        message.put("markdown", markdown);

        // 添加@功能
        if (mobile != null && !mobile.isEmpty()) {
            Map<String, Object> at = new HashMap<>();
            at.put("atMobiles", mobile.split(","));
            at.put("isAtAll", false);
            message.put("at", at);
        }

        return message;
    }

    private String formatTitle(AlertInfo alert) {
        String levelEmoji = getLevelEmoji(alert.getLevel());
        return String.format("%s [%s] %s", levelEmoji, alert.getLevel(), alert.getRuleName());
    }

    private String formatContent(AlertInfo alert) {
        return String.format(
            "### 🚨 系统告警通知\n\n" +
            "**告警规则**: %s\n" +
            "**告警级别**: %s\n" +
            "**监控目标**: `%s`\n" +
            "**当前数值**: %.2f\n" +
            "**触发次数**: %d 次\n" +
            "**发生时间**: %s\n\n" +
            "---\n" +
            "*此消息由系统自动发送*",
                alert.getRuleName(),
                alert.getLevel(),
                alert.getTarget(),
                alert.getValue(),
                alert.getCount(),
                formatTime(alert.getTime())
        );
    }

    private void addSignature(Map<String, Object> message) {
        try {
            long timestamp = System.currentTimeMillis();
            String sign = generateSign(timestamp, secret);

            // 添加签名到URL
            String signUrl = webhook + "&timestamp=" + timestamp + "&sign=" + sign;
            message.put("webhook", signUrl);
        } catch (Exception e) {
            log.error("生成钉钉签名失败", e);
        }
    }

    private String generateSign(long timestamp, String secret) throws Exception {
        String stringToSign = timestamp + "\n" + secret;
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes("UTF-8"), "HmacSHA256"));
        byte[] signData = mac.doFinal(stringToSign.getBytes("UTF-8"));
        return java.util.Base64.getEncoder().encodeToString(signData);
    }

    private String getLevelEmoji(AlertLevel level) {
        switch (level) {
            case EMERGENCY:
                return "🆘";
            case CRITICAL:
                return "🚨";
            case WARNING:
                return "⚠️";
            default:
                return "ℹ️";
        }
    }

    private String formatTime(long timestamp) {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return sdf.format(new java.util.Date(timestamp));
    }
}