package com.macro.mall.portal.domain;

import lombok.Data;

import java.io.Serializable;

/**
 * JWT登录请求参数
 */
@Data
public class JwtLoginRequest implements Serializable {
    private static final long serialVersionUID = 1L;
    
    /**
     * 用户名
     */
    private String username;
    
    /**
     * 密码
     */
    private String password;
    
    /**
     * 验证码
     */
    private String authCode;
    
    /**
     * 验证码UUID
     */
    private String uuid;
}