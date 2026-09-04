package com.twojo.global.sequence;

import com.twojo.global.sequence.DocumentSequence.DocType;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 채번 카운터 접근 (docs/06-erd.md §document_sequence).
 *
 * <p>메서드가 둘뿐이고 둘 다 {@link DocumentNumberService} 전용이다 — 카운터를 락 없이 읽는
 * 경로를 만들지 않으려고 일반 조회를 두지 않았다.
 */
interface DocumentSequenceRepository extends JpaRepository<DocumentSequence, UUID> {

    /**
     * 카운터 행을 <b>배타 락</b>과 함께 읽는다 — {@code SELECT ... FOR UPDATE}.
     *
     * <p>같은 회사·문서종류·연월의 다른 트랜잭션은 이 락이 풀릴 때까지 여기서 대기한다.
     * 락은 <b>호출자 트랜잭션이 끝날 때</b> 풀리므로, 번호를 받은 쪽이 커밋을 마치기 전에는
     * 다음 요청이 같은 {@code last_seq}를 읽지 못한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select s from DocumentSequence s
            where s.companyId = :companyId
              and s.docType = :docType
              and s.yearMonth = :yearMonth
            """)
    Optional<DocumentSequence> findForUpdate(@Param("companyId") UUID companyId,
                                             @Param("docType") DocType docType,
                                             @Param("yearMonth") String yearMonth);

    /**
     * 그 달의 첫 발급 — 카운터 행을 0으로 심는다. <b>이미 있으면 아무것도 하지 않는다.</b>
     *
     * <p>JPA {@code save()}를 쓰지 않는 이유: 두 요청이 같은 달의 첫 번호를 동시에 받으면
     * 둘 다 행이 없다고 보고 INSERT 하고, 진 쪽이 {@code uk_document_sequence}에 걸린다.
     * 그 순간 Hibernate 세션은 rollback-only가 되어 <b>같은 트랜잭션 안에서 복구할 수 없다</b> —
     * 견적 작성 전체가 500으로 끝난다. {@code ON CONFLICT DO NOTHING}은 예외를 만들지 않으므로
     * 진 쪽도 그대로 다음 줄(락 걸고 재조회)로 넘어간다.
     *
     * <p>{@code id}를 넘기는 것은 DB 함수({@code gen_random_uuid()})에 기대지 않으려는 것이고,
     * {@code created_at}·{@code updated_at}은 DDL 기본값이 채운다.
     */
    @Modifying
    @Query(value = """
            insert into document_sequence (id, company_id, doc_type, year_month, last_seq)
            values (:id, :companyId, :docType, :yearMonth, 0)
            on conflict (company_id, doc_type, year_month) do nothing
            """, nativeQuery = true)
    void insertIfAbsent(@Param("id") UUID id,
                        @Param("companyId") UUID companyId,
                        @Param("docType") String docType,
                        @Param("yearMonth") String yearMonth);
}
