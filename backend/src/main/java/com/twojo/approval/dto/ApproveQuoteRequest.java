package com.twojo.approval.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 견적 승인 요청 (AP-19, Q-44). responderName·responderTitle은 응답자가 직접 밝히는
 * 자기 신고이며 시스템이 검증하지 않는다 — D는 C의 도메인 메서드에 그대로 전달한다.
 */
public record ApproveQuoteRequest(
        @NotBlank @Size(max = 50) String responderName,   // 필수
        @Size(max = 50) String responderTitle) {}         // 선택
