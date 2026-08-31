package com.twojo.approval.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 고객 문의 등록 요청 (AP-15) — 기록만, 답변은 외부 수단 (Q-20). */
public record CreateInquiryRequest(
        @NotBlank @Size(max = 1000) String content) {}
