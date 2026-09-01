package com.twojo.member.dto;

import java.util.UUID;

/** 내 정보 (08 §A · AU-03·07). 필드와 순서는 문서 그대로다. */
public record MeResponse(
        UUID memberId, String name, String email, String phone,
        String role, UUID companyId, String companyName) {}
