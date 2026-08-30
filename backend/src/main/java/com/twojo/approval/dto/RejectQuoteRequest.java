package com.twojo.approval.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 견적 반려 요청 — 사유 필수 (AP-10), 응답자 정보는 승인과 동일 (AP-19, 자기 신고). */
public record RejectQuoteRequest(
        @NotBlank String reason,
        @NotBlank @Size(max = 50) String responderName,
        @Size(max = 50) String responderTitle) {}
