package com.macro.mall.portal.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pagehelper.PageHelper;
import com.macro.mall.mapper.OmsOrderReturnApplyMapper;
import com.macro.mall.model.OmsOrderReturnApply;
import com.macro.mall.model.OmsOrderReturnApplyExample;
import com.macro.mall.model.UmsMember;
import com.macro.mall.portal.domain.OmsOrderReturnApplyParam;
import com.macro.mall.portal.service.OmsPortalOrderReturnApplyService;
import com.macro.mall.portal.service.UmsMemberService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
public class OmsPortalOrderReturnApplyServiceImpl implements OmsPortalOrderReturnApplyService {

    private static final Logger logger = LoggerFactory.getLogger(OmsPortalOrderReturnApplyServiceImpl.class);

    @Autowired
    private OmsOrderReturnApplyMapper returnApplyMapper;

    @Autowired
    private UmsMemberService memberService;

    @Value("${agent.url:http://localhost:9900/api/chat}")
    private String agentUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public int create(OmsOrderReturnApplyParam returnApply) {
        OmsOrderReturnApply realApply = new OmsOrderReturnApply();
        BeanUtils.copyProperties(returnApply, realApply);
        realApply.setCreateTime(new Date());
        realApply.setStatus(0);
        // 从当前登录用户补充信息，不依赖前端传值
        try {
            UmsMember member = memberService.getCurrentMember();
            realApply.setMemberUsername(member.getUsername());
            if (realApply.getReturnName() == null || realApply.getReturnName().isEmpty()) {
                realApply.setReturnName(
                        member.getNickname() != null ? member.getNickname() : member.getUsername());
            }
            if (realApply.getReturnPhone() == null || realApply.getReturnPhone().isEmpty()) {
                realApply.setReturnPhone(member.getPhone());
            }
        } catch (Exception ignored) {
            // 未登录时使用前端传值作为兜底
        }
        int rows = returnApplyMapper.insert(realApply);

        // 异步触发 AI 自动审核
        final Long applyId = realApply.getId();
        if (applyId != null) {
            CompletableFuture.runAsync(() -> autoAudit(applyId));
        }

        return rows;
    }

    private void autoAudit(Long applyId) {
        try {
            String auditBaseUrl = agentUrl.replace("/api/chat", "/api/return/audit");
            Map<String, Object> body = new HashMap<>();
            body.put("applyId", applyId);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            String response = restTemplate.postForObject(auditBaseUrl, request, String.class);
            JsonNode root = objectMapper.readTree(response);

            if (root.has("auditPassed") && root.get("auditPassed").asBoolean()) {
                // AI 审核通过 → 自动确认退货
                OmsOrderReturnApply update = new OmsOrderReturnApply();
                update.setId(applyId);
                update.setStatus(1); // 退货中
                update.setHandleTime(new Date());
                update.setHandleMan("AI自动审核");
                update.setHandleNote("AI自动审核通过");
                returnApplyMapper.updateByPrimaryKeySelective(update);
                logger.info("AI 自动审核通过: applyId={}", applyId);
            } else if (root.has("needHumanSupport") && root.get("needHumanSupport").asBoolean()) {
                // 转人工 → 保持 status=0，添加备注
                OmsOrderReturnApply update = new OmsOrderReturnApply();
                update.setId(applyId);
                update.setHandleNote("AI审核建议转人工: " +
                        (root.has("reason") ? root.get("reason").asText() : ""));
                returnApplyMapper.updateByPrimaryKeySelective(update);
                logger.info("AI 审核建议转人工: applyId={}", applyId);
            } else {
                // 审核拒绝 → 自动拒绝
                OmsOrderReturnApply update = new OmsOrderReturnApply();
                update.setId(applyId);
                update.setStatus(3); // 已拒绝
                update.setHandleTime(new Date());
                update.setHandleMan("AI自动审核");
                update.setHandleNote(root.has("reason") ? root.get("reason").asText() : "AI自动审核拒绝");
                returnApplyMapper.updateByPrimaryKeySelective(update);
                logger.info("AI 自动审核拒绝: applyId={}, reason={}",
                        applyId, root.has("reason") ? root.get("reason").asText() : "");
            }
        } catch (Exception e) {
            logger.warn("AI 自动审核调用失败，保持待处理状态: applyId={}, error={}",
                    applyId, e.getMessage());
        }
    }

    @Override
    public List<OmsOrderReturnApply> listByMember(String username, Integer pageSize, Integer pageNum) {
        PageHelper.startPage(pageNum, pageSize);
        OmsOrderReturnApplyExample example = new OmsOrderReturnApplyExample();
        example.createCriteria().andMemberUsernameEqualTo(username);
        example.setOrderByClause("create_time DESC");
        return returnApplyMapper.selectByExample(example);
    }
}
