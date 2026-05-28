package org.example.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class ReturnAuditRequest {
    @JsonProperty(value = "applyId")
    @JsonAlias({"applyId", "ApplyId", "APPLYID"})
    private Long applyId;
}
