package com.macro.mall.portal.service;

import com.macro.mall.model.UmsMember;
import com.macro.mall.portal.domain.JwtLoginRequest;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * 会员Service接口
 */
public interface UmsMemberService {

    /**
     * 会员登录
     * @param request 登录参数
     * @return 登录结果（包含token）
     */
    String login(JwtLoginRequest request);

    /**
     * 根据ID获取会员
     */
    UmsMember getById(Long id);

    /**
     * 根据用户名获取会员
     */
    UmsMember getByUsername(String username);

    /**
     * 注册会员
     */
    void register(String username, String password, String telephone);

    /**
     * 生成验证码
     */
    String generateAuthCode(String telephone);

    /**
     * 校验验证码
     */
    boolean verifyAuthCode(String telephone, String authCode);

    /**
     * 获取当前登录会员（基于请求上下文）
     */
    UmsMember getCurrentMember();

    /**
     * 更新会员积分
     */
    void updateIntegration(Long memberId, Integer integration);

    /**
     * 刷新token
     */
    String refreshToken(String token);

    /**
     * 加载会员信息（用于Spring Security）
     */
    UserDetails loadUserByUsername(String username);
}