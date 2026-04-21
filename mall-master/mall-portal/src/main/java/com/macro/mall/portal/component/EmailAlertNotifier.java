package com.macro.mall.portal.component;

import com.macro.mall.portal.component.AlertManager.AlertInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

/**
 * 邮件告警通知器
 */
@Slf4j
@Component
public class EmailAlertNotifier implements AlertNotificationService.AlertNotifier {

    @Value("${alert.email.enabled:false}")
    private boolean enabled;

    @Value("${alert.email.host:smtp.example.com}")
    private String host;

    @Value("${alert.email.port:465}")
    private int port;

    @Value("${alert.email.username:}")
    private String username;

    @Value("${alert.email.password:}")
    private String password;

    @Value("${alert.email.from:alert@example.com}")
    private String from;

    @Value("${alert.email.to:admin@example.com}")
    private String to;

    @Value("${alert.email.subject-prefix:[告警通知]}")
    private String subjectPrefix;

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Override
    public String getChannelName() {
        return "email";
    }

    @Override
    public void send(AlertInfo alert) {
        if (!enabled) {
            log.debug("邮件告警通知已禁用");
            return;
        }

        if (mailSender == null) {
            log.warn("邮件发送器未配置，无法发送邮件告警");
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(from);
            helper.setTo(to.split(","));
            helper.setSubject(subjectPrefix + " " + alert.getLevel() + " - " + alert.getRuleName());
            helper.setText(buildEmailContent(alert), true);

            mailSender.send(message);
            log.info("邮件告警发送成功: {}", alert.getRuleName());
        } catch (MessagingException e) {
            log.error("发送邮件告警失败", e);
        }
    }

    private String buildEmailContent(AlertInfo alert) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String levelColor = getLevelColor(alert.getLevel());

        String template = "<!DOCTYPE html>\n"
                + "<html>\n"
                + "<head>\n"
                + "    <meta charset=\"UTF-8\">\n"
                + "    <style>\n"
                + "        body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }\n"
                + "        .container { max-width: 600px; margin: 0 auto; padding: 20px; }\n"
                + "        .header { background-color: %s; color: white; padding: 15px; text-align: center; }\n"
                + "        .content { padding: 20px; background-color: #f9f9f9; }\n"
                + "        .info-row { margin: 10px 0; padding: 10px; background-color: white; border-left: 4px solid %s; }\n"
                + "        .label { font-weight: bold; color: #666; }\n"
                + "        .value { color: #333; }\n"
                + "        .footer { text-align: center; padding: 10px; color: #999; font-size: 12px; }\n"
                + "    </style>\n"
                + "</head>\n"
                + "<body>\n"
                + "    <div class=\"container\">\n"
                + "        <div class=\"header\">\n"
                + "            <h2>系统告警通知</h2>\n"
                + "        </div>\n"
                + "        <div class=\"content\">\n"
                + "            <div class=\"info-row\">\n"
                + "                <span class=\"label\">告警规则:</span>\n"
                + "                <span class=\"value\">%s</span>\n"
                + "            </div>\n"
                + "            <div class=\"info-row\">\n"
                + "                <span class=\"label\">告警级别:</span>\n"
                + "                <span class=\"value\">%s</span>\n"
                + "            </div>\n"
                + "            <div class=\"info-row\">\n"
                + "                <span class=\"label\">监控目标:</span>\n"
                + "                <span class=\"value\">%s</span>\n"
                + "            </div>\n"
                + "            <div class=\"info-row\">\n"
                + "                <span class=\"label\">当前数值:</span>\n"
                + "                <span class=\"value\">%.2f</span>\n"
                + "            </div>\n"
                + "            <div class=\"info-row\">\n"
                + "                <span class=\"label\">触发次数:</span>\n"
                + "                <span class=\"value\">%d 次</span>\n"
                + "            </div>\n"
                + "            <div class=\"info-row\">\n"
                + "                <span class=\"label\">发生时间:</span>\n"
                + "                <span class=\"value\">%s</span>\n"
                + "            </div>\n"
                + "        </div>\n"
                + "        <div class=\"footer\">\n"
                + "            <p>此邮件由系统自动发送，请勿回复</p>\n"
                + "        </div>\n"
                + "    </div>\n"
                + "</body>\n"
                + "</html>\n";

        return String.format(
                template,
                levelColor,
                levelColor,
                alert.getRuleName(),
                alert.getLevel(),
                alert.getTarget(),
                alert.getValue(),
                alert.getCount(),
                sdf.format(new Date(alert.getTime()))
        );
    }

    private String getLevelColor(AlertManager.AlertLevel level) {
        switch (level) {
            case EMERGENCY:
                return "#dc3545";
            case CRITICAL:
                return "#fd7e14";
            case WARNING:
                return "#ffc107";
            default:
                return "#17a2b8";
        }
    }
}