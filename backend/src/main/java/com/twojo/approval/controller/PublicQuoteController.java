package com.twojo.approval.controller;

import com.twojo.approval.dto.ApproveQuoteRequest;
import com.twojo.approval.dto.CreateInquiryRequest;
import com.twojo.approval.dto.PublicQuoteResponse;
import com.twojo.approval.dto.RejectQuoteRequest;
import com.twojo.approval.service.CustomerQuoteService;
import jakarta.validation.Valid;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 고객 견적 열람·승인·반려·문의 (07 §D · AP-02·07·08~10·15·18·19) — 비로그인 경로.
 *
 * <p>토큰이 곧 인증이라 {@code AccessContext}를 받지 않는다 (publicChain permitAll, SC-07~09).
 * "지금"은 컨트롤러가 주입한다 ({@code PublicPasswordController}와 같은 패턴).
 */
@RestController
@RequestMapping("/public/api/v1/quotes")
@RequiredArgsConstructor
public class PublicQuoteController {

    private final CustomerQuoteService customerQuoteService;

    /** 열람 조회 (AP-02·07·18) — 첫 열람이면 열람 시각 기록 + NT-03. */
    @GetMapping("/{token}")
    public PublicQuoteResponse view(@PathVariable String token) {
        return customerQuoteService.view(token, Instant.now());
    }

    /** 승인 (AP-08·19) — 상태만 바뀌고 돌려줄 바디가 없다. */
    @PostMapping("/{token}/approve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void approve(@PathVariable String token, @Valid @RequestBody ApproveQuoteRequest request) {
        customerQuoteService.approve(token, request, Instant.now());
    }

    /** 반려 (AP-09·10·19). */
    @PostMapping("/{token}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reject(@PathVariable String token, @Valid @RequestBody RejectQuoteRequest request) {
        customerQuoteService.reject(token, request, Instant.now());
    }

    /** 문의 등록 (AP-15, Q-20) — 담당 구성원·기업 관리자에게 NT-10. */
    @PostMapping("/{token}/inquiries")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void createInquiry(@PathVariable String token,
                              @Valid @RequestBody CreateInquiryRequest request) {
        customerQuoteService.createInquiry(token, request, Instant.now());
    }
}
