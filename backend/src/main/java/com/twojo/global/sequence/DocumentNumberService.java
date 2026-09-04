package com.twojo.global.sequence;

import com.twojo.global.sequence.DocumentSequence.DocType;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 표시 번호 발급 — {@code Q-2608-017} · {@code O-2608-004} (docs/06-erd.md §document_sequence).
 *
 * <p>회사·문서종류·연월마다 카운터가 하나 있고, 발급은 그 행에 배타 락을 걸고 +1 하는 것이다.
 * {@code MAX+1 재시도} 방식은 폐기됐다 — 번호에서 숫자를 파싱해야 하고 재시도 루프가 호출부마다
 * 복제된다(ERD).
 *
 * <p><b>왜 {@code MANDATORY}인가.</b> 락은 트랜잭션이 끝날 때 풀린다. 이 서비스가 자기
 * 트랜잭션을 열고 닫으면 번호를 넘겨준 직후 락이 풀려, 호출자가 견적 저장에 실패해도 번호는
 * 이미 소비된다(번호가 비는 구멍). 호출자 트랜잭션에 합류하면 락이 견적 커밋까지 유지되고,
 * 롤백 시 번호도 함께 되돌아간다. 트랜잭션 없이 부르면 락이 조용히 걸리지 않으므로
 * {@code MANDATORY}로 <b>그 자리에서 실패</b>하게 한다.
 */
@Service
public class DocumentNumberService {

    /**
     * 연월 판정 기준. 표시 번호는 사람이 읽는 값이라 한국 날짜로 끊는다 —
     * UTC로 끊으면 월말 자정 무렵 국내 09시 이전 발급이 지난 달 번호를 받는다.
     * (저장·전송은 여전히 UTC다 — {@code jackson.time-zone})
     */
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    /** 2026-08 → "2608" (DDL의 {@code year_month VARCHAR(4)}). */
    private static final DateTimeFormatter YEAR_MONTH = DateTimeFormatter.ofPattern("yyMM");

    private static final Map<DocType, String> PREFIX = Map.of(
            DocType.QUOTE, "Q",
            DocType.ORDER, "O");

    private final DocumentSequenceRepository repository;

    DocumentNumberService(DocumentSequenceRepository repository) {
        this.repository = repository;
    }

    /** 오늘(KST) 기준으로 다음 표시 번호를 발급한다. 호출자 트랜잭션 안에서만 부를 수 있다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public String next(UUID companyId, DocType docType) {
        return next(companyId, docType, LocalDate.now(SEOUL));
    }

    /**
     * 발급 본체 — 날짜를 받아 테스트에서 월 경계를 재현할 수 있게 분리했다.
     *
     * <p>행이 없으면 심고 <b>다시 락을 걸고 읽는다</b>. 심는 것과 읽는 것을 합치지 않는 이유는
     * {@link DocumentSequenceRepository#insertIfAbsent}에 적었다 — 동시 첫 발급에서 진 쪽이
     * 예외 없이 이 경로로 합류해야 한다.
     */
    String next(UUID companyId, DocType docType, LocalDate today) {
        String yearMonth = today.format(YEAR_MONTH);

        DocumentSequence sequence = repository.findForUpdate(companyId, docType, yearMonth)
                .orElseGet(() -> {
                    repository.insertIfAbsent(UUID.randomUUID(), companyId, docType.name(), yearMonth);
                    return repository.findForUpdate(companyId, docType, yearMonth)
                            .orElseThrow(() -> new IllegalStateException(
                                    "채번 행을 심은 직후 찾지 못했다 — " + companyId + "/" + docType + "/" + yearMonth));
                });

        return format(docType, yearMonth, sequence.next());
    }

    /**
     * {@code Q-2608-017}. 순번은 세 자리로 채우되 <b>1000번째부터는 자연히 늘어난다</b>
     * ({@code Q-2608-1000}) — 자리를 넘겼다고 번호를 못 주는 편보다 낫다.
     * 월 1000건은 v1 규모(구성원 5~30명, docs/01 §2.1)를 한참 넘는다.
     */
    private static String format(DocType docType, String yearMonth, int seq) {
        return "%s-%s-%03d".formatted(PREFIX.get(docType), yearMonth, seq);
    }
}
