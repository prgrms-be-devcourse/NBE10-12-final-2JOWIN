package com.twojo.onboarding.controller;

import com.twojo.onboarding.dto.ApplicationResponse;
import com.twojo.onboarding.dto.CreateApplicationRequest;
import com.twojo.onboarding.service.ApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사용 신청 (07 §A · ON-01·02) — 비로그인 경로.
 *
 * <p>AccessContext를 받지 않는다. 이 체인은 permitAll이라 주입할 인증 정보가 없다.
 */
@RestController
@RequestMapping("/public/api/v1/applications")
@RequiredArgsConstructor
public class PublicApplicationController {

    private final ApplicationService applicationService;

    /**
     * 접수 (ON-01) — 201.
     *
     * <p>접수 확인(ON-02)이 응답 그 자체다. 조회 엔드포인트가 07 §A에 없어 신청자가 나중에
     * 다시 볼 경로가 없다 — 상태는 결과 메일로 온다 (NT-13).
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationResponse submit(@Valid @RequestBody CreateApplicationRequest request) {
        return applicationService.submit(request);
    }
}
