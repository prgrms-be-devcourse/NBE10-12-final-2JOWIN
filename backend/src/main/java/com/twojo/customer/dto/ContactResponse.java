package com.twojo.customer.dto;

import java.util.UUID;

/** 고객사 담당자 응답. {@code primary}는 대표 담당자 여부 (CU-11, 고객사당 1명). */
public record ContactResponse(
        UUID id,
        String name,
        String title,
        String phone,
        String email,
        boolean primary) {}
