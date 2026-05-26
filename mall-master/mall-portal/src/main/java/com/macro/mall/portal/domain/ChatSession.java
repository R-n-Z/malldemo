package com.macro.mall.portal.domain;

import lombok.Data;
import java.util.Date;

@Data
public class ChatSession {
    private Long id;
    private Long memberId;
    private String memberName;
    private Long productId;
    private String productName;
    private String productPic;
    private Long adminId;
    private String adminName;
    private Integer status;
    private String lastMessage;
    private Date createTime;
    private Date updateTime;
}
