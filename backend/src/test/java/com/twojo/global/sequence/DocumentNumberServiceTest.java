package com.twojo.global.sequence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.twojo.global.sequence.DocumentSequence.DocType;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 표시 번호 조립·연월 판정 — 카운터 값이 번호 문자열이 되는 지점을 고정한다.
 *
 * <p><b>여기서 검증되지 않는 것</b>: 동시 발급에서 번호가 겹치지 않는지. 그건
 * {@code SELECT ... FOR UPDATE}가 실제로 거는지의 문제라 목으로는 성립하지 않는다 —
 * {@link DocumentNumberConcurrencyTest}가 맡는다.
 */
@ExtendWith(MockitoExtension.class)
class DocumentNumberServiceTest {

    private static final UUID COMPANY_ID = UUID.randomUUID();
    private static final UUID OTHER_COMPANY_ID = UUID.randomUUID();

    @Mock private DocumentSequenceRepository repository;
    @InjectMocks private DocumentNumberService documentNumberService;

    /** last_seq가 주어진 값인 카운터 행. 증가는 엔티티가 하므로 그 시작점만 심는다. */
    private static DocumentSequence counterAt(int lastSeq) {
        DocumentSequence sequence = new DocumentSequence();
        ReflectionTestUtils.setField(sequence, "lastSeq", lastSeq);
        return sequence;
    }

    @Test
    @DisplayName("견적은 Q, 주문은 O — 접두어가 문서 종류를 가른다")
    void 접두어() {
        // 호출마다 새 카운터 — 같은 인스턴스를 돌려주면 두 번째 호출이 이미 증가된 값을 본다
        given(repository.findForUpdate(any(), any(), any()))
                .willAnswer(invocation -> Optional.of(counterAt(0)));

        assertThat(documentNumberService.next(COMPANY_ID, DocType.QUOTE, LocalDate.of(2026, 8, 20)))
                .isEqualTo("Q-2608-001");
        assertThat(documentNumberService.next(COMPANY_ID, DocType.ORDER, LocalDate.of(2026, 8, 20)))
                .isEqualTo("O-2608-001");
    }

    @Test
    @DisplayName("시드의 last_seq를 이어받는다 — 16이면 다음은 017이다")
    void 이어받기() {
        given(repository.findForUpdate(COMPANY_ID, DocType.QUOTE, "2608"))
                .willReturn(Optional.of(counterAt(16)));

        assertThat(documentNumberService.next(COMPANY_ID, DocType.QUOTE, LocalDate.of(2026, 8, 20)))
                .isEqualTo("Q-2608-017");
    }

    @Test
    @DisplayName("순번은 세 자리로 채우되 1000번째부터는 자리가 늘어난다 — 번호를 못 주는 것보다 낫다")
    void 자릿수() {
        given(repository.findForUpdate(any(), any(), any())).willReturn(Optional.of(counterAt(999)));

        assertThat(documentNumberService.next(COMPANY_ID, DocType.QUOTE, LocalDate.of(2026, 8, 20)))
                .isEqualTo("Q-2608-1000");
    }

    @Test
    @DisplayName("연월은 한국 날짜로 끊는다 — 카운터도 번호도 같은 '2609'를 쓴다")
    void 연월_판정() {
        given(repository.findForUpdate(COMPANY_ID, DocType.QUOTE, "2609"))
                .willReturn(Optional.of(counterAt(0)));

        assertThat(documentNumberService.next(COMPANY_ID, DocType.QUOTE, LocalDate.of(2026, 9, 1)))
                .isEqualTo("Q-2609-001");
    }

    @Test
    @DisplayName("달이 바뀌면 그 달의 카운터를 찾는다 — 8월 카운터가 9월 번호에 쓰이지 않는다")
    void 월별_리셋() {
        given(repository.findForUpdate(COMPANY_ID, DocType.QUOTE, "2608"))
                .willReturn(Optional.of(counterAt(16)));
        given(repository.findForUpdate(COMPANY_ID, DocType.QUOTE, "2609"))
                .willReturn(Optional.of(counterAt(0)));

        assertThat(documentNumberService.next(COMPANY_ID, DocType.QUOTE, LocalDate.of(2026, 8, 31)))
                .isEqualTo("Q-2608-017");
        assertThat(documentNumberService.next(COMPANY_ID, DocType.QUOTE, LocalDate.of(2026, 9, 1)))
                .isEqualTo("Q-2609-001");
    }

    @Test
    @DisplayName("회사가 다르면 다른 카운터를 본다 — 번호는 회사 안에서만 이어진다")
    void 회사_스코프() {
        given(repository.findForUpdate(COMPANY_ID, DocType.QUOTE, "2608"))
                .willReturn(Optional.of(counterAt(16)));
        given(repository.findForUpdate(OTHER_COMPANY_ID, DocType.QUOTE, "2608"))
                .willReturn(Optional.of(counterAt(0)));

        assertThat(documentNumberService.next(COMPANY_ID, DocType.QUOTE, LocalDate.of(2026, 8, 20)))
                .isEqualTo("Q-2608-017");
        assertThat(documentNumberService.next(OTHER_COMPANY_ID, DocType.QUOTE, LocalDate.of(2026, 8, 20)))
                .isEqualTo("Q-2608-001");
    }

    @Test
    @DisplayName("행이 있으면 심지 않는다 — INSERT는 그 달 첫 발급에만 나간다")
    void 행이_있으면_INSERT_없음() {
        given(repository.findForUpdate(any(), any(), any())).willReturn(Optional.of(counterAt(3)));

        documentNumberService.next(COMPANY_ID, DocType.ORDER, LocalDate.of(2026, 8, 20));

        then(repository).should(never()).insertIfAbsent(any(), any(), any(), any());
    }

    @Test
    @DisplayName("행이 없으면 심고 락을 걸어 다시 읽는다 — 심은 값을 그대로 쓰지 않는다")
    void 첫_발급은_심고_재조회() {
        given(repository.findForUpdate(COMPANY_ID, DocType.QUOTE, "2609"))
                .willReturn(Optional.empty())            // 1회차 — 아직 없다
                .willReturn(Optional.of(counterAt(0)));  // INSERT 후 — 락을 잡고 읽는다

        assertThat(documentNumberService.next(COMPANY_ID, DocType.QUOTE, LocalDate.of(2026, 9, 1)))
                .isEqualTo("Q-2609-001");

        then(repository).should().insertIfAbsent(any(), eq(COMPANY_ID), eq("QUOTE"), eq("2609"));
    }
}
