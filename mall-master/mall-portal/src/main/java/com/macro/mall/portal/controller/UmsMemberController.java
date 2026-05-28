package com.macro.mall.portal.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.model.UmsMember;
import com.macro.mall.portal.annotation.RateLimit;
import com.macro.mall.portal.annotation.RateLimit.Algorithm;
import com.macro.mall.portal.annotation.RateLimit.LimitType;
import com.macro.mall.portal.domain.JwtLoginRequest;
import com.macro.mall.portal.service.UmsMemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

/**
 * 会员登录注册Controller
 */
@Slf4j
@Tag(name = "UmsMemberController", description = "会员登录注册管理")
@RestController
@RequestMapping("/sso")
public class UmsMemberController {

    @Autowired
    private UmsMemberService memberService;

    @Value("${jwt.tokenHead:Bearer }")
    private String tokenHead;

    @Operation(summary = "会员登录")
    @RateLimit(type = LimitType.IP, algorithm = Algorithm.FIXED_WINDOW, param1 = 5, param2 = 60)
    @PostMapping("/login")
    public CommonResult login(@RequestBody JwtLoginRequest request) {
        try {
            String token = memberService.login(request);
            Map<String, String> result = new HashMap<>();
            result.put("token", token);
            result.put("tokenHead", tokenHead);
            return CommonResult.success(result, "登录成功");
        } catch (RuntimeException e) {
            log.warn("登录失败: {}", e.getMessage());
            return CommonResult.failed(e.getMessage());
        }
    }

    @Operation(summary = "会员注册")
    @PostMapping("/register")
    public CommonResult register(
            @RequestParam String username,
            @RequestParam String password,
            @RequestParam String telephone) {
        try {
            memberService.register(username, password, telephone);
            return CommonResult.success("注册成功");
        } catch (RuntimeException e) {
            log.warn("注册失败: {}", e.getMessage());
            return CommonResult.failed(e.getMessage());
        }
    }

    @Operation(summary = "获取验证码")
    @GetMapping("/getAuthCode")
    public CommonResult getAuthCode(@RequestParam String telephone) {
        String authCode = memberService.generateAuthCode(telephone);
        return CommonResult.success(authCode, "获取验证码成功");
    }

    @Operation(summary = "获取当前会员信息")
    @GetMapping("/info")
    public CommonResult info() {
        UmsMember member = memberService.getCurrentMember();
        if (member == null) {
            return CommonResult.unauthorized(null);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("id", member.getId());
        result.put("username", member.getUsername());
        result.put("phone", member.getPhone());
        result.put("icon", member.getIcon());
        result.put("gender", member.getGender());
        result.put("nickname", member.getNickname());
        return CommonResult.success(result);
    }

    @Operation(summary = "刷新Token")
    @PostMapping("/refreshToken")
    public CommonResult refreshToken(@RequestHeader("Authorization") String authHeader) {
        // 从请求头提取Token
        if (authHeader != null && authHeader.startsWith(tokenHead)) {
            String oldToken = authHeader.substring(tokenHead.length());
            String newToken = memberService.refreshToken(oldToken);
            if (newToken != null) {
                Map<String, String> result = new HashMap<>();
                result.put("token", newToken);
                result.put("tokenHead", tokenHead);
                return CommonResult.success(result, "Token刷新成功");
            }
        }
        return CommonResult.failed("Token无效");
    }
}