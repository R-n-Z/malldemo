package com.macro.mall.seckill.config;

import com.alibaba.csp.sentinel.annotation.aspectj.SentinelResourceAspect;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

/**
 * Sentinel限流配置
 */
@Configuration
public class SentinelConfig {

    /**
     * 开启Sentinel注解支持
     */
    @Bean
    public SentinelResourceAspect sentinelResourceAspect() {
        return new SentinelResourceAspect();
    }

    /**
     * 配置限流规则
     */
    @PostConstruct
    public void initFlowRules() {
        List<FlowRule> rules = new ArrayList<>();

        // 秒杀接口限流：1000 QPS
        FlowRule seckillRule = new FlowRule();
        seckillRule.setResource("seckill");
        seckillRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        seckillRule.setCount(1000);
        seckillRule.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_WARM_UP);
        seckillRule.setWarmUpPeriodSec(10);
        rules.add(seckillRule);

        // 库存查询限流：2000 QPS
        FlowRule stockRule = new FlowRule();
        stockRule.setResource("getStock");
        stockRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        stockRule.setCount(2000);
        rules.add(stockRule);

        // 验证码接口限流：500 QPS
        FlowRule captchaRule = new FlowRule();
        captchaRule.setResource("captcha");
        captchaRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        captchaRule.setCount(500);
        rules.add(captchaRule);

        FlowRuleManager.loadRules(rules);
    }
}