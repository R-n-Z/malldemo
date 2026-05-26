package com.macro.mall.portal.domain;

import lombok.Data;
import java.util.Date;

@Data
public class ChatMessage {
    private Long id;
    private Long sessionId;
    private Integer senderType;  // 1=用户 2=商家
    private Long senderId;
    private String senderName;
    private String content;
    private Integer msgType;     // 1=文字
    private Integer isRead;
    private Date createTime;
}
