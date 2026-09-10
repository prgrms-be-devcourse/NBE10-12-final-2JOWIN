package com.twojo.boundary;

import java.util.List;
import java.util.UUID;

/**
 * 회사 조회 계약 — 구현: A(onboarding 모듈). 다른 모듈은 company 테이블을 직접 조회하지 않는다.
 *
 * <p>11 §7.2 인터페이스 목록에 빠져 있던 것을 메운다. 회사명이 필요한 곳이 둘이다 —
 * 로그인 응답(08 §A LoginResponse.companyName)과 고객 열람 응답(07 §D PublicQuoteResponse.companyName).
 */
public interface CompanyQuery {

    /**
     * 없으면 RESOURCE_NOT_FOUND — FK가 존재를 보장하는 자리라 없다는 것은 데이터 이상이다.
     *
     * <p><b>회사 하나를 읽는 경로는 이것 하나뿐이다.</b> 정지 여부만 필요한 호출자도 이것을 쓴다 —
     * {@code isSuspended} 같은 메서드를 따로 두면 <b>같은 행을 읽는 경로가 둘</b>이 되고,
     * 회사 상태가 {@code ACTIVE}/{@code SUSPENDED} 둘뿐이라 {@link CompanySummary#active()}
     * 하나로 판정이 끝난다. 고객 열람 페이지는 어차피 이름·사업자번호를 받으려고 이 메서드를
     * 부르므로, 그 한 번의 호출에서 정지 여부까지 함께 온다.
     *
     * <p>이 규칙은 <b>id를 이미 아는 호출자</b>에게 적용된다. 어떤 회사를 볼지 자체가 결과인 조회
     * ({@link #findActiveIds()})는 여기서 파생시킬 수 없어 별도 메서드다.
     */
    CompanySummary get(UUID companyId);

    /**
     * 운영 중({@code ACTIVE})인 회사 id 전체 — 배치가 회사 단위로 순회하는 진입점이다 (NT-05·06).
     * 정지된 회사는 빠진다 (Q-27). 없으면 빈 목록이고, 순서는 보장하지 않는다.
     *
     * <p><b>회사 스코프를 인자로 받지 않는 것이 이 메서드의 목적이다.</b> 배치에는 요청이 없어
     * {@code AccessContext}가 따라오지 않으므로, 어떤 회사를 돌지를 스스로 정해야 한다.
     * {@code QuoteQuery.findAwaitingResponse}가 회사 전체를 돌려주고 호출자가 거르기로 한 것과
     * 같은 자리다 (11 v2.0.11).
     *
     * <p><b>페이지네이션하지 않는다.</b> 잘린 목록은 알림이 조용히 가지 않는 회사를 만드는데,
     * 호출자는 잘렸다는 사실을 알 방법이 없다. v1 규모에서 회사 수가 적고 배치는 단발 실행이다.
     *
     * <p>돌려주는 것은 <b>호출 시점의 스냅샷</b>이다. 목록을 받은 뒤 회사가 정지될 수 있으므로,
     * 회사마다 시간이 걸리는 호출자는 처리 직전에 {@link #get}으로 다시 본다.
     */
    List<UUID> findActiveIds();

    /**
     * 한 번의 조회로 회사 표시와 정지 판정을 모두 덮는다.
     *
     * @param name       로그인 응답(08 §A) · 고객 열람 페이지 상단
     * @param businessNo 사업자등록번호 — 고객 열람 페이지가 회사명과 함께 최상단에 표시한다
     *                   (10-screen-design.md §5.6 · GAP-05 완화안). 전역 UNIQUE (06 §제약)
     * @param active     정지 여부 (ON-08~10). SC-10(정지 회사의 승인·반려 차단)과
     *                   Q-27(정지 중 배치 알림 억제)이 {@code !active()}로 판정한다
     */
    record CompanySummary(UUID id, String name, String businessNo, boolean active) {}
}
