package com.twojo.member.dto;

/**
 * 초대 확인 응답 (08 §A · MB-03) — 수락 화면이 그릴 값.
 *
 * <p>초대한 사람도, 초대 id도 담지 않는다. 아직 로그인하지 않은 사람이 보는 화면이라
 * 회사의 내부 정보를 알려줄 이유가 없다.
 */
public record InvitationInfoResponse(String companyName, String email, String role) {}
