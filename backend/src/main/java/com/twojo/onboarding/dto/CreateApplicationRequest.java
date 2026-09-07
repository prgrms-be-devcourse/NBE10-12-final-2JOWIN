package com.twojo.onboarding.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 사용 신청 요청 (08 §A · ON-01).
 *
 * <p>사업자번호 형식은 검사하지 않는다 — 07·08 어디에도 형식 규정이 없고, 표기(하이픈 유무)를
 * 서버가 정하면 문서에 없는 규칙이 생긴다. 중복 판정은 승인 시점의 전역 UNIQUE가 맡는다.
 *
 * <p>{@code applicantName}은 승인 시 기업 관리자 계정의 이름이 된다 (08 v1.6.11 · ON-07).
 */
public record CreateApplicationRequest(
        @NotBlank @Size(max = 255) String companyName,
        @NotBlank @Size(max = 20) String businessNo,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(max = 100) String applicantName) {}
