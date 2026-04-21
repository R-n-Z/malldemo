package com.macro.mall.portal.service.impl;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.macro.mall.mapper.UmsMemberMapper;
import com.macro.mall.model.UmsMember;
import com.macro.mall.model.UmsMemberExample;
import com.macro.mall.portal.domain.JwtLoginRequest;
import com.macro.mall.portal.service.UmsMemberService;
import com.macro.mall.portal.util.JwtTokenUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 会员Service实现
 */
@Slf4j
@Service
public class UmsMemberServiceImpl implements UmsMemberService {

    @Autowired
    private UmsMemberMapper memberMapper;

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Value("${redis.database:mall}")
    private String redisDatabase;

    private static final String AUTH_CODE_KEY = "ums:authCode:";
    private static final long AUTH_CODE_EXPIRE = 90; // 验证码90秒过期

    @Override
    public String login(JwtLoginRequest request) {
        String username = request.getUsername();
        String password = request.getPassword();

        // 校验验证码（如果有）
        if (StrUtil.isNotBlank(request.getAuthCode())) {
            if (!verifyAuthCode(username, request.getAuthCode())) {
                throw new RuntimeException("验证码错误");
            }
        }

        // 查询会员
        UmsMember member = getByUsername(username);
        if (member == null) {
            throw new RuntimeException("用户不存在");
        }

        // 验证密码
        String md5Password = DigestUtils.md5DigestAsHex(password.getBytes());
        if (!md5Password.equals(member.getPassword())) {
            throw new RuntimeException("密码错误");
        }

        // 生成Token
        String token = jwtTokenUtil.generateToken(member.getId(), member.getUsername());
        
        // 缓存用户信息到Redis
        String memberKey = redisDatabase + ":ums:member:" + member.getId();
        redisTemplate.opsForValue().set(memberKey, member, 7 * 24 * 3600, TimeUnit.SECONDS);

        log.info("会员登录成功: username={}", username);
        return token;
    }

    @Override
    public UmsMember getById(Long id) {
        String memberKey = redisDatabase + ":ums:member:" + id;
        Object cached = redisTemplate.opsForValue().get(memberKey);
        if (cached != null) {
            return (UmsMember) cached;
        }

        UmsMember member = memberMapper.selectByPrimaryKey(id);
        if (member != null) {
            redisTemplate.opsForValue().set(memberKey, member, 7 * 24 * 3600, TimeUnit.SECONDS);
        }
        return member;
    }

    @Override
    public UmsMember getByUsername(String username) {
        UmsMemberExample example = new UmsMemberExample();
        example.createCriteria().andUsernameEqualTo(username);
        List<UmsMember> members = memberMapper.selectByExample(example);
        return members != null && !members.isEmpty() ? members.get(0) : null;
    }

    @Override
    public void register(String username, String password, String telephone) {
        // 检查用户名是否存在
        if (getByUsername(username) != null) {
            throw new RuntimeException("用户名已存在");
        }

        // 检查手机号是否已注册
        UmsMemberExample example = new UmsMemberExample();
        example.createCriteria().andPhoneEqualTo(telephone);
        List<UmsMember> members = memberMapper.selectByExample(example);
        if (members != null && !members.isEmpty()) {
            throw new RuntimeException("手机号已注册");
        }

        // 创建会员
        UmsMember member = new UmsMember();
        member.setUsername(username);
        member.setPassword(DigestUtils.md5DigestAsHex(password.getBytes()));
        member.setPhone(telephone);
        member.setStatus(1); // 启用状态
        member.setCreateTime(new java.util.Date());
        member.setIntegration(0); // 初始积分
        member.setGrowth(0); // 初始成长值

        memberMapper.insert(member);
        log.info("会员注册成功: username={}, phone={}", username, telephone);
    }

    @Override
    public String generateAuthCode(String telephone) {
        String code = RandomUtil.randomNumbers(6);
        String key = redisDatabase + ":" + AUTH_CODE_KEY + telephone;
        redisTemplate.opsForValue().set(key, code, AUTH_CODE_EXPIRE, TimeUnit.SECONDS);
        log.info("验证码已生成: phone={}, code={}", telephone, code);
        return code;
    }

    @Override
    public boolean verifyAuthCode(String telephone, String authCode) {
        String key = redisDatabase + ":" + AUTH_CODE_KEY + telephone;
        Object cachedCode = redisTemplate.opsForValue().get(key);
        if (cachedCode == null) {
            return false;
        }
        return authCode.equals(cachedCode.toString());
    }

    @Override
    public UmsMember getCurrentMember() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return null;
        }
        Object memberIdObj = attrs.getAttribute("memberId", RequestAttributes.SCOPE_REQUEST);
        if (memberIdObj == null) {
            return null;
        }
        Long memberId;
        if (memberIdObj instanceof Long) {
            memberId = (Long) memberIdObj;
        } else {
            try {
                memberId = Long.parseLong(memberIdObj.toString());
            } catch (Exception e) {
                return null;
            }
        }
        return getById(memberId);
    }

    @Override
    public void updateIntegration(Long memberId, Integer integration) {
        if (memberId == null || integration == null) {
            return;
        }
        UmsMember update = new UmsMember();
        update.setId(memberId);
        update.setIntegration(integration);
        memberMapper.updateByPrimaryKeySelective(update);

        String memberKey = redisDatabase + ":ums:member:" + memberId;
        redisTemplate.delete(memberKey);
    }

    @Override
    public String refreshToken(String token) {
        return jwtTokenUtil.refreshToken(token);
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        UmsMember member = getByUsername(username);
        if (member == null) {
            return null;
        }
        return new User(member.getUsername(), member.getPassword(), true, true, true, true, AuthorityUtils.NO_AUTHORITIES);
    }
}