package com.macro.mall.portal.component;

import com.macro.mall.portal.component.AlertManager.AlertInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * 短信告警通知器
 * 支持阿里云短信、腾讯云短信等主流短信平台
 */
@Slf4j
@Component
public class SmsAlertNotifier implements AlertNotificationService.AlertNotifier {

    @Value("${alert.sms.enabled:false}")
    private boolean enabled;

    @Value("${alert.sms.provider:aliyun}")
    private String provider;

    @Value("${alert.sms.access-key:}")
    private String accessKey;

    @Value("${alert.sms.secret-key:}")
    private String secretKey;

    @Value("${alert.sms.sign-name:}")
    private String signName;

    @Value("${alert.sms.template-code:}")
    private String templateCode;

    @Value("${alert.sms.phone-numbers:}")
    private String phoneNumbers;

    @Value("${alert.sms.region-id:cn-hangzhou}")
    private String regionId;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public String getChannelName() {
        return "sms";
    }

    @Override
    public void send(AlertInfo alert) {
        if (!enabled) {
            log.debug("短信告警通知已禁用");
            return;
        }

        if (phoneNumbers == null || phoneNumbers.isEmpty()) {
            log.warn("短信接收号码未配置，无法发送短信告警");
            return;
        }

        try {
            switch (provider.toLowerCase()) {
                case "aliyun":
                    sendAliyunSms(alert);
                    break;
                case "tencent":
                    sendTencentSms(alert);
                    break;
                default:
                    log.warn("不支持的短信提供商: {}", provider);
            }
        } catch (Exception e) {
            log.error("发送短信告警失败", e);
        }
    }

    /**
     * 阿里云短信发送
     */
    private void sendAliyunSms(AlertInfo alert) {
        try {
            // 阿里云短信API endpoint
            String endpoint = "https://dysmsapi.aliyuncs.com";

            // 构建请求参数
            Map<String, String> params = new HashMap<>();
            params.put("Action", "SendSms");
            params.put("Version", "2017-05-25");
            params.put("RegionId", regionId);
            params.put("AccessKeyId", accessKey);
            params.put("SignatureMethod", "HMAC-SHA1");
            params.put("Timestamp", formatTimestamp());
            params.put("SignatureVersion", "1.0");
            params.put("SignatureNonce", generateNonce());
            params.put("PhoneNumbers", phoneNumbers);
            params.put("SignName", signName);
            params.put("TemplateCode", templateCode);

            // 模板参数
            String templateParam = String.format(
                    "{\"level\":\"%s\",\"rule\":\"%s\",\"target\":\"%s\",\"value\":\"%.2f\"}",
                    alert.getLevel(),
                    alert.getRuleName(),
                    alert.getTarget(),
                    alert.getValue()
            );
            params.put("TemplateParam", templateParam);

            // 生成签名
            String signature = generateAliyunSignature(params, secretKey);
            params.put("Signature", signature);

            // 发送请求
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(params, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(endpoint, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                log.info("阿里云短信告警发送成功: {}", alert.getRuleName());
            } else {
                log.error("阿里云短信告警发送失败: {}", response.getBody());
            }
        } catch (Exception e) {
            log.error("发送阿里云短信告警失败", e);
        }
    }

    /**
     * 腾讯云短信发送
     */
    private void sendTencentSms(AlertInfo alert) {
        try {
            // 腾讯云短信API endpoint
            String endpoint = "https://sms.tencentcloudapi.com";

            // 构建请求参数
            Map<String, Object> params = new HashMap<>();
            params.put("Action", "SendSms");
            params.put("Version", "2021-01-11");
            params.put("Region", regionId);
            params.put("Timestamp", System.currentTimeMillis() / 1000);
            params.put("Nonce", (int) (Math.random() * 10000));
            params.put("SecretId", accessKey);

            // 发送对象
            String[] phoneNumberSet = phoneNumbers.split(",");
            params.put("PhoneNumberSet", phoneNumberSet);

            // 模板参数
            Map<String, String> templateParamMap = new HashMap<>();
            templateParamMap.put("level", alert.getLevel().name());
            templateParamMap.put("rule", alert.getRuleName());
            templateParamMap.put("target", alert.getTarget());
            templateParamMap.put("value", String.format("%.2f", alert.getValue()));
            params.put("TemplateParamSet", new Map[]{templateParamMap});

            params.put("SignName", signName);
            params.put("TemplateId", templateCode);

            // 生成签名（简化版，实际应使用腾讯云SDK）
            String signature = generateTencentSignature(params, secretKey);
            params.put("Signature", signature);

            // 发送请求
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(params, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(endpoint, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                log.info("腾讯云短信告警发送成功: {}", alert.getRuleName());
            } else {
                log.error("腾讯云短信告警发送失败: {}", response.getBody());
            }
        } catch (Exception e) {
            log.error("发送腾讯云短信告警失败", e);
        }
    }

    private String formatTimestamp() {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        return sdf.format(new java.util.Date());
    }

    private String generateNonce() {
        return String.valueOf(System.currentTimeMillis());
    }

    private String generateAliyunSignature(Map<String, String> params, String secret) throws Exception {
        // 按参数名排序并拼接
        String sortedParams = params.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .filter(e -> !e.getKey().equals("Signature"))
                .map(e -> percentEncode(e.getKey()) + "=" + percentEncode(e.getValue()))
                .reduce((a, b) -> a + "&" + b)
                .orElse("");

        String stringToSign = "POST&%2F&" + percentEncode(sortedParams);

        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA1");
        mac.init(new javax.crypto.spec.SecretKeySpec((secret + "&").getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA1"));
        byte[] signData = mac.doFinal(stringToSign.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return java.util.Base64.getEncoder().encodeToString(signData);
    }

    private String percentEncode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("*", "%2A")
                .replace("%7E", "~");
    }

    private String generateTencentSignature(Map<String, Object> params, String secret) throws Exception {
        // 简化实现，实际应使用腾讯云SDK
        String sortedParams = params.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + e.getValue())
                .reduce((a, b) -> a + "&" + b)
                .orElse("");

        String stringToSign = "POSTsms.tencentcloudapi.com/?\n" + sortedParams;

        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] signData = mac.doFinal(stringToSign.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return java.util.Base64.getEncoder().encodeToString(signData);
    }
}