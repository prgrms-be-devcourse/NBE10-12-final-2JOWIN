package com.twojo.member.dto;

import java.util.UUID;

/**
 * 구성원 비활성화 요청 (08 §A · MB-09·14).
 *
 * <p>이관 대상은 <b>진행 중(리드~협상) 담당 Deal이 있을 때만</b> 필수다. 종결된 Deal은 옮기지 않아
 * 필수 판정에서도 빠진다 — 성사 실적이 후임 것이 되면 되돌릴 수 없기 때문이다 (Q-48).
 *
 * <p>담당 Deal이 없으면 이 값이 없어도 된다. 그래서 필수 애너테이션이 붙지 않는다 — 필요한지
 * 아닌지가 요청만 보고는 정해지지 않고 담당 건수를 세어 봐야 안다.
 */
public record DeactivateMemberRequest(UUID transferToMemberId) {}
