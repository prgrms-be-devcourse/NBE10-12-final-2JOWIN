package com.twojo.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 프로필 수정 요청 (08 §A · AU-07).
 *
 * <p>바꿀 대상(memberId)이 없다. access token에서 오기 때문이다 — 필드를 두면
 * 남의 프로필을 바꾸는 요청을 만들 수 있게 된다.
 *
 * <p>길이 상한은 member 테이블의 컬럼 폭과 같은 값이다. 이게 없으면 넘치는 값이 검증을
 * 통과해 DB에서 거부되고, 그 실패는 400이 아니라 500으로 나간다.
 */
public record UpdateMeRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 30) String phone) {}
