package com.twojo.quote.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** 견적 요청 DTO — docs/08-dto.md 316~330행 그대로. */
public final class QuoteRequests {

    private QuoteRequests() {
    }

    /** 작성 시작 (QT-01) — 종결 Deal이면 409 QUOTE_DEAL_CLOSED */
    public record CreateQuote(@NotNull UUID dealId) {
    }

    /**
     * 작성 중 전체 갱신 (QT-02~11·23) — <b>PUT이다.</b>
     *
     * <p>부분 수정이 아니라 전체 교체라 {@code terms}에 null이 오면 조건 문구를 지운다.
     * 08의 B 도메인 record들과 반대인데, 08이 이 record에 "PATCH: null은 미변경"을
     * 달지 않은 것과 일관된다.
     */
    public record UpdateQuote(
            @NotNull @Future LocalDate validUntil,          // QT-09 = 링크 만료 (Q-17)
            @NotNull String vatMode,                        // EXCLUDED(기본) / INCLUDED (Q-16)
            @Size(max = 2000) String terms,
            @NotEmpty @Valid List<Item> items,
            @NotNull Integer version) {

        /** 단가는 <b>항상 세전</b>이다 — vat_mode와 무관하다 (Q-46) */
        public record Item(
                UUID productId,                             // null = 직접 입력 (QT-03)
                @NotBlank String name,
                @NotBlank String unit,
                @Positive int quantity,
                @NotNull @PositiveOrZero Long unitPrice,    // 0원 하한 — 음수는 400 (Q-02)
                int sortOrder) {
        }
    }
}
