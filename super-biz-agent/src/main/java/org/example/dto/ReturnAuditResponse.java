package org.example.dto;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class ReturnAuditResponse {
    private boolean success;
    private Long applyId;
    private boolean auditPassed;
    private String decision;
    private String reason;
    private boolean needHumanSupport;
    private int consecutiveRejections;
    private String auditReport;

    public static ReturnAuditResponse passed(Long applyId, String reason, String report) {
        ReturnAuditResponse r = new ReturnAuditResponse();
        r.success = true;
        r.applyId = applyId;
        r.auditPassed = true;
        r.decision = "APPROVE";
        r.reason = reason;
        r.auditReport = report;
        return r;
    }

    public static ReturnAuditResponse rejected(Long applyId, String reason, boolean needHuman,
                                                int rejectCount, String report) {
        ReturnAuditResponse r = new ReturnAuditResponse();
        r.success = true;
        r.applyId = applyId;
        r.auditPassed = false;
        r.decision = "REJECT";
        r.reason = reason;
        r.needHumanSupport = needHuman;
        r.consecutiveRejections = rejectCount;
        r.auditReport = report;
        return r;
    }

    public static ReturnAuditResponse error(String reason) {
        ReturnAuditResponse r = new ReturnAuditResponse();
        r.success = false;
        r.auditPassed = false;
        r.reason = reason;
        return r;
    }
}
