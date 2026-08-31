package com.twojo.approval.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 고객 열람 페이지 응답 (public — 토큰 인증, SC-07~09).
 * status·vatMode는 전이표 영문 코드 문자열, 금액 3분리는 서버 계산값 (QT-25).
 */
public record PublicQuoteResponse(
        String quoteNo,
        String status,
        String companyName,                       // 발송 회사
        AssigneeInfo assignee,                    // AP-18: Deal의 "현재" 담당자 동적 조회
        String vatMode,
        String terms,
        LocalDate validUntil,
        Long supplyAmount,
        Long vatAmount,
        Long totalAmount,
        List<ItemView> items,
        boolean respondable) {                    // false: 정지·응답완료 — 버튼 비활성 안내

    public record AssigneeInfo(String name, String email, String phone) {}

    public record ItemView(String name, String unit, int quantity, Long unitPrice, Long amount) {}
}
