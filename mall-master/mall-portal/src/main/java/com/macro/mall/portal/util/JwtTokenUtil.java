package com.macro.mall.portal.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT Token工具类
 */
@Slf4j
@Component
public class JwtTokenUtil {

    @Value("${jwt.secret:mall-portal-secret}")
    private String secret;

    @Value("${jwt.expiration:604800}")
    private Long expiration;

    @Value("${jwt.tokenHeader:Authorization}")
    private String tokenHeader;

    @Value("${jwt.tokenHead:Bearer }")
    private String tokenHead;

    /**
     * 获取签名密钥
     */
    private SecretKey getSecretKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 生成Token
     */
    public String generateToken(Long memberId, String username) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("memberId", memberId);
        claims.put("username", username);
        
        return createToken(claims, memberId.toString());
    }

    /**
     * 创建Token
     */
    private String createToken(Map<String, Object> claims, String subject) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration * 1000);

        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSecretKey())
                .compact();
    }

    /**
     * 解析Token
     */
    public Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getSecretKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            log.warn("Token已过期: {}", e.getMessage());
            return e.getClaims();
        } catch (JwtException e) {
            log.warn("Token解析失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 获取用户ID
     */
    public Long getMemberIdFromToken(String token) {
        Claims claims = parseToken(token);
        if (claims == null) {
            return null;
        }
        Object memberId = claims.get("memberId");
        if (memberId instanceof Integer) {
            return ((Integer) memberId).longValue();
        } else if (memberId instanceof Long) {
            return (Long) memberId;
        } else if (memberId instanceof String) {
            return Long.parseLong((String) memberId);
        }
        return null;
    }

    /**
     * 获取用户名
     */
    public String getUsernameFromToken(String token) {
        Claims claims = parseToken(token);
        if (claims == null) {
            return null;
        }
        return (String) claims.get("username");
    }

    /**
     * 验证Token是否有效
     */
    public boolean validateToken(String token) {
        Claims claims = parseToken(token);
        if (claims == null) {
            return false;
        }
        return !claims.getExpiration().before(new Date());
    }

    /**
     * 刷新Token
     */
    public String refreshToken(String token) {
        Claims claims = parseToken(token);
        if (claims == null) {
            return null;
        }
        
        Long memberId = getMemberIdFromToken(token);
        String username = getUsernameFromToken(token);
        
        return generateToken(memberId, username);
    }

    /**
     * 从请求头获取Token
     */
    public String getTokenFromHeader(String authHeader) {
        if (authHeader != null && authHeader.startsWith(tokenHead)) {
            return authHeader.substring(tokenHead.length());
        }
        return null;
    }

    public String getTokenHeader() {
        return tokenHeader;
    }

    public String getTokenHead() {
        return tokenHead;
    }
}