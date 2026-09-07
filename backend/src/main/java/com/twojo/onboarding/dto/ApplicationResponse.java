package com.twojo.onboarding.dto;

import com.twojo.onboarding.entity.Application;
import java.time.Instant;
import java.util.UUID;

/**
 * 신청 응답 (08 §A).
 *
 * <p>접수 확인(ON-02)과 관리자 목록·상세(ON-03)가 같은 record를 쓴다. 신청자가 보는 것과
 * 관리자가 보는 것이 같은 행의 전부라 나눌 이유가 없다 — 신청서에는 비밀 필드가 없다.
 *
 * <p>번호가 없다. 신청은 id로 식별한다 (06 v1.6 — 채번 APPLICATION 제외).
 */
public record ApplicationResponse(
        UUID id,
        String companyName,
        String businessNo,
        String email,
        String applicantName,
        String status,
        String rejectReason,
        Instant decidedAt,
        Instant createdAt) {

    public static ApplicationResponse of(Application application) {
        return new ApplicationResponse(
                application.getId(), application.getCompanyName(), application.getBusinessNo(),
                application.getEmail(), application.getApplicantName(),
                application.getStatus().name(), application.getRejectReason(),
                application.getDecidedAt(), application.getCreatedAt());
    }
}
