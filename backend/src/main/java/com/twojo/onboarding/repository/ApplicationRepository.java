package com.twojo.onboarding.repository;

import com.twojo.onboarding.entity.Application;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** application 테이블 접근 — 모듈 내부. 신청은 회사가 생기기 전이라 company_id 스코프가 없다. */
public interface ApplicationRepository extends JpaRepository<Application, UUID> {

    /**
     * 같은 이메일의 검토 대기 신청 (05 §1 "막히는 것").
     *
     * <p>대소문자를 무시한다 — 저장 값은 서비스가 소문자로 정규화하지만, 그 이전에 들어온
     * 행이 남아 있으면 대문자 표기가 우회 경로가 된다. 구성원 조회와 같은 기준이다.
     *
     * <p>상태를 인자로 받는 이유는 JPQL에 enum 상수를 적으면 패키지 경로가 문자열로 굳어
     * 클래스를 옮기는 순간 조용히 깨지기 때문이다.
     *
     * <p>대기 행이 둘 이상일 수 없다는 보장은 DB에 없다(부분 유니크 인덱스 없음).
     * 접수 경로가 매번 이 검사를 통과시키는 것에 기대고 있다.
     */
    @Query("select a from Application a where lower(a.email) = :email and a.status = :status")
    Optional<Application> findByEmailLowerAndStatus(@Param("email") String email,
                                                    @Param("status") Application.Status status);

    /** 목록 (ON-03) — status를 비우면 처리된 신청도 함께 나온다. */
    Page<Application> findByStatus(Application.Status status, Pageable pageable);
}
