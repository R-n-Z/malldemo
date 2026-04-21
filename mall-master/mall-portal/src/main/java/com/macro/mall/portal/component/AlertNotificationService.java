package com.macro.mall.portal.component;

import com.macro.mall.portal.component.AlertManager.AlertInfo;
import com.macro.mall.portal.component.AlertManager.AlertLevel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 告警通知服务
 * 统一管理所有告警通知渠道
 */
@Slf4j
@Service
public class AlertNotificationService {

    @Value("${alert.notification.enabled:true}")
    private boolean notificationEnabled;

    @Value("${alert.notification.channels:console}")
    private String channels;

    @Autowired(required = false)
    private EmailAlertNotifier emailAlertNotifier;

    @Autowired(required = false)
    private DingTalkAlertNotifier dingTalkAlertNotifier;

    @Autowired(required = false)
    private SmsAlertNotifier smsAlertNotifier;

    // 告警通知器列表
    private final List<AlertNotifier> notifiers = new ArrayList<>();

    // 告警级别阈值（只有高于此级别的告警才会发送通知）
    private final ConcurrentHashMap<String, AlertLevel> levelThresholds = new ConcurrentHashMap<>();

    /**
     * 初始化通知器
     */
    @jakarta.annotation.PostConstruct
    public void init() {
        // 根据配置加载通知器
        String[] channelArray = channels.split(",");
        for (String channel : channelArray) {
            channel = channel.trim().toLowerCase();
            switch (channel) {
                case "email":
                    if (emailAlertNotifier != null) {
                        notifiers.add(emailAlertNotifier);
                        log.info("启用邮件告警通知");
                    }
                    break;
                case "dingtalk":
                case "dingding":
                    if (dingTalkAlertNotifier != null) {
                        notifiers.add(dingTalkAlertNotifier);
                        log.info("启用钉钉告警通知");
                    }
                    break;
                case "sms":
                    if (smsAlertNotifier != null) {
                        notifiers.add(smsAlertNotifier);
                        log.info("启用短信告警通知");
                    }
                    break;
                case "console":
                    notifiers.add(new ConsoleAlertNotifier());
                    log.info("启用控制台告警通知");
                    break;
                default:
                    log.warn("未知的告警通知渠道: {}", channel);
            }
        }

        // 设置默认告警级别阈值
        levelThresholds.put("email", AlertLevel.WARNING);
        levelThresholds.put("dingtalk", AlertLevel.WARNING);
        levelThresholds.put("sms", AlertLevel.CRITICAL);

        log.info("告警通知服务初始化完成，启用 {} 个通知渠道", notifiers.size());
    }

    /**
     * 发送告警通知
     */
    public void sendAlert(AlertInfo alert) {
        if (!notificationEnabled) {
            log.debug("告警通知已禁用");
            return;
        }

        if (notifiers.isEmpty()) {
            log.warn("没有可用的告警通知器");
            return;
        }

        // 根据告警级别过滤通知器
        for (AlertNotifier notifier : notifiers) {
            try {
                AlertLevel threshold = levelThresholds.getOrDefault(
                        notifier.getChannelName(), AlertLevel.INFO);
                if (alert.getLevel().ordinal() >= threshold.ordinal()) {
                    notifier.send(alert);
                }
            } catch (Exception e) {
                log.error("发送告警通知失败: channel={}", notifier.getChannelName(), e);
            }
        }
    }

    /**
     * 添加通知器
     */
    public void addNotifier(AlertNotifier notifier) {
        notifiers.add(notifier);
    }

    /**
     * 移除通知器
     */
    public void removeNotifier(AlertNotifier notifier) {
        notifiers.remove(notifier);
    }

    /**
     * 设置渠道告警级别阈值
     */
    public void setLevelThreshold(String channel, AlertLevel level) {
        levelThresholds.put(channel.toLowerCase(), level);
    }

    /**
     * 告警通知器接口
     */
    public interface AlertNotifier {
        /**
         * 获取渠道名称
         */
        String getChannelName();

        /**
         * 发送告警通知
         */
        void send(AlertInfo alert);
    }

    /**
     * 控制台通知器（默认）
     */
    @Slf4j
    public static class ConsoleAlertNotifier implements AlertNotifier {
        @Override
        public String getChannelName() {
            return "console";
        }

        @Override
        public void send(AlertInfo alert) {
            String emoji = getEmoji(alert.getLevel());
            log.info("{} [{}] {} - {} = {} (第{}次触发, 时间: {})",
                    emoji,
                    alert.getLevel(),
                    alert.getRuleName(),
                    alert.getTarget(),
                    String.format("%.2f", alert.getValue()),
                    alert.getCount(),
                    formatTime(alert.getTime())
            );
        }

        private String getEmoji(AlertLevel level) {
            switch (level) {
                case INFO:
                    return "ℹ️";
                case WARNING:
                    return "⚠️";
                case CRITICAL:
                    return "🚨";
                case EMERGENCY:
                    return "🆘";
                default:
                    return "📢";
            }
        }

        private String formatTime(long timestamp) {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            return sdf.format(new java.util.Date(timestamp));
        }
    }
}