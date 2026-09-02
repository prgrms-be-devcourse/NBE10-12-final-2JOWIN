package com.twojo.deal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDate;
import java.util.UUID;

/** Deal 요청 DTO (docs/08-dto.md §C). 금액은 원 단위 정수, 음수 불가 (Q-12). */
public final class DealRequests {

    private DealRequests() {
    }

    /** 생성 (DL-01~04) */
    public record CreateDeal(
            @NotNull UUID customerId,
            @NotBlank String title,
            @PositiveOrZero Long expectedAmount,   // DL-02 — null 허용(미정)
            LocalDate dueDate,
            UUID assigneeMemberId) {}              // null이면 생성자 본인. 활성 구성원만

    /**
     * 수정 (DL-02·03) — <b>부분 수정이다. null 필드는 변경하지 않는다.</b>
     *
     * <p>08의 B 도메인 record(UpdateContactRequest·UpdateActivityRequest)가 같은 규칙을 쓴다.
     * 부작용 하나 — 예상 금액·마감일을 "미정"으로 되돌릴 수 없다. v1에서는 지원하지 않는다.
     */
    public record UpdateDeal(
            String title,
            @PositiveOrZero Long expectedAmount,
            LocalDate dueDate,
            @NotNull Integer version) {}           // 낙관적 락 — 불일치 409 STALE_VERSION

    /** 담당자 변경 (DL-05, SC-06) — 기업 관리자 전용. 대상은 같은 회사의 활성 구성원 */
    public record ChangeAssignee(
            @NotNull UUID assigneeMemberId,
            @NotNull Integer version) {}
}
