package com.macro.mall.portal.interceptor;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.portal.util.JwtTokenUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * JWT认证拦截器
 */
@Slf4j
@Component
public class JwtAuthInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    @Value("${jwt.tokenHead:Bearer }")
    private String tokenHead;

    // 白名单路径
    private static final String[] IGNORE_PATHS = {
            "/sso/login",
            "/sso/register",
            "/sso/getAuthCode",
            "/home/**",
            "/product/**",
            "/brand/**",
            "/alipay/**",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/doc.html",
            "/webjars/**",
            "/favicon.ico"
    };

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 检查是否在白名单
        String path = request.getRequestURI();
        for (String ignorePath : IGNORE_PATHS) {
            if (path.startsWith(ignorePath.replace("**", ""))) {
                return true;
            }
        }

        // 2. 获取Token
        String authHeader = request.getHeader("Authorization");
        String token = jwtTokenUtil.getTokenFromHeader(authHeader);

        if (!StringUtils.hasText(token)) {
            log.warn("请求缺少Token: path={}", path);
            writeUnauthorizedResponse(response, "请先登录");
            return false;
        }

        // 3. 验证Token
        if (!jwtTokenUtil.validateToken(token)) {
            log.warn("Token无效或已过期: path={}", path);
            writeUnauthorizedResponse(response, "登录已过期，请重新登录");
            return false;
        }

        // 4. 解析用户信息并设置到请求属性
        Long memberId = jwtTokenUtil.getMemberIdFromToken(token);
        if (memberId != null) {
            request.setAttribute("memberId", memberId);
            log.debug("Token验证成功: memberId={}, path={}", memberId, path);
        }

        return true;
    }

    /**
     * 写入未授权响应
     */
    private void writeUnauthorizedResponse(HttpServletResponse response, String message) throws Exception {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        
        CommonResult<Object> result = CommonResult.unauthorized(null);
        result.setMessage(message);
        ObjectMapper mapper = new ObjectMapper();
        response.getWriter().write(mapper.writeValueAsString(result));
    }
}