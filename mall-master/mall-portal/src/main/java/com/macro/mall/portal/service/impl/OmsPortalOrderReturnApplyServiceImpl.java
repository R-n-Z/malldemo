package com.macro.mall.portal.service.impl;

import com.github.pagehelper.PageHelper;
import com.macro.mall.mapper.OmsOrderReturnApplyMapper;
import com.macro.mall.model.OmsOrderReturnApply;
import com.macro.mall.model.OmsOrderReturnApplyExample;
import com.macro.mall.model.UmsMember;
import com.macro.mall.portal.domain.OmsOrderReturnApplyParam;
import com.macro.mall.portal.service.OmsPortalOrderReturnApplyService;
import com.macro.mall.portal.service.UmsMemberService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
public class OmsPortalOrderReturnApplyServiceImpl implements OmsPortalOrderReturnApplyService {

    @Autowired
    private OmsOrderReturnApplyMapper returnApplyMapper;

    @Autowired
    private UmsMemberService memberService;

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
        return returnApplyMapper.insert(realApply);
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
