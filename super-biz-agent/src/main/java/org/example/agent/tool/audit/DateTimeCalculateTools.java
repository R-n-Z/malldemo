package org.example.agent.tool.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

@Component
public class DateTimeCalculateTools {

    private static final Logger logger = LoggerFactory.getLogger(DateTimeCalculateTools.class);

    public static final String TOOL_CALCULATE_RECEIVE_DAYS = "calculateReceiveDays";

    /** 常见日期格式列表 */
    private static final DateTimeFormatter[] FORMATTERS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS"),
            DateTimeFormatter.ISO_LOCAL_DATE_TIME
    };

    @Tool(name = TOOL_CALCULATE_RECEIVE_DAYS,
          description = "计算收货天数。输入收货时间和申请时间，返回两个时间之间的天数差")
    public String calculateReceiveDays(
            @ToolParam(description = "收货时间字符串") String receiveTime,
            @ToolParam(description = "申请时间字符串") String applyTime) {
        logger.info("计算收货天数: receiveTime={}, applyTime={}", receiveTime, applyTime);
        try {
            LocalDateTime receive = parseDateTime(receiveTime);
            LocalDateTime apply = parseDateTime(applyTime);

            if (receive == null || apply == null) {
                return "{\"error\": \"时间解析失败\", \"receiveTime\": \"" + receiveTime
                        + "\", \"applyTime\": \"" + applyTime + "\"}";
            }

            long days = ChronoUnit.DAYS.between(receive, apply);
            long hours = ChronoUnit.HOURS.between(receive, apply);
            String threshold;
            if (days <= 7) {
                threshold = "WITHIN_7_DAYS";
            } else if (days <= 15) {
                threshold = "WITHIN_15_DAYS";
            } else {
                threshold = "OVER_15_DAYS";
            }
            return String.format(
                    "{\"receiveTime\": \"%s\", \"applyTime\": \"%s\", \"daysSinceReceive\": %d,"
                    + " \"hoursSinceReceive\": %d, \"threshold\": \"%s\"}",
                    receiveTime, applyTime, days, hours, threshold);
        } catch (Exception e) {
            logger.error("计算收货天数失败: {}", e.getMessage());
            return "{\"error\": \"计算失败: " + e.getMessage() + "\"}";
        }
    }

    private LocalDateTime parseDateTime(String timeStr) {
        if (timeStr == null || timeStr.trim().isEmpty()) {
            return null;
        }
        String cleaned = timeStr.trim().replace("Z", "").replace("z", "");
        if (cleaned.length() >= 19) {
            cleaned = cleaned.substring(0, 19);
        }
        for (DateTimeFormatter fmt : FORMATTERS) {
            try {
                return LocalDateTime.parse(cleaned, fmt);
            } catch (Exception ignored) {
            }
        }
        return null;
    }
}
