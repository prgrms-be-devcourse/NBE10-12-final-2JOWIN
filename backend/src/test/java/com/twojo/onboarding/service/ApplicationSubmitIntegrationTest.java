package com.twojo.onboarding.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import com.twojo.onboarding.dto.CreateApplicationRequest;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * 대기 신청의 이메일 점유 — 표기를 타지 않고(Q-14), DB 제약으로도 한 번 더 막힌다(V102).
 *
 * <p><b>목으로는 성립하지 않는다.</b> 목 저장소를 쓰면 "무엇이 돌아오는가"를 테스트가
 * 정해주고, 저장된 표기와 조회 표기가 실제로 맞는지가 검증 경로에서 사라진다. 저장을 거쳐
 * 다시 찾아오는 왕복이 이 규칙의 전부다.
 *
 * <p><b>지키는 것은 {@code ApplicationService.normalize()}다.</b> 회귀 확인에서
 * {@code ApplicationRepository}의 {@code lower(a.email)}를 지워도 이 테스트는 초록불이었다 —
 * 서비스가 저장과 조회 양쪽을 이미 소문자로 맞추기 때문이다. 쿼리의 {@code lower()}는
 * 정규화 이전에 심긴 행(픽스처·시드)을 위한 방어일 뿐 이 테스트가 덮는 대상이 아니다.
 * 정규화를 지우면 이 테스트가 빨간불이 된다.
 *
 * <p><b>{@code @Transactional}을 붙이지 않는다.</b> 붙이면 서비스가 호출자 트랜잭션에 합류해
 * 첫 신청이 확정되지 않은 채로 두 번째 조회가 돈다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ApplicationSubmitIntegrationTest {

    /** 회사·이메일을 테스트마다 갈라 다른 통합 테스트의 잔여 행과 부딪히지 않게 한다. */
    private final String 사업자번호 = UUID.randomUUID().toString().substring(0, 13);
    private final String 소문자_이메일 = "seoyeon-" + UUID.randomUUID() + "@hanbit.co.kr";

    @Autowired private ApplicationService applicationService;
    @Autowired private JdbcTemplate jdbc;

    @AfterEach
    void 심은_신청을_지운다() {
        jdbc.update("delete from application where lower(email) = ?", 소문자_이메일);
    }

    @Test
    void 대문자로_표기한_이메일도_대기_신청에_걸린다() {
        // given — 김서연이 소문자 이메일로 신청해 검토 대기 행이 하나 생긴 상태다
        applicationService.submit(new CreateApplicationRequest(
                "한빛오피스", 사업자번호, 소문자_이메일, "김서연"));

        // when — 같은 사람이 대문자로 바꿔 다시 신청하면
        String 대문자_이메일 = 소문자_이메일.toUpperCase();
        var 재신청 = new CreateApplicationRequest("한빛오피스", 사업자번호, 대문자_이메일, "김서연");

        // then — 표기가 달라도 같은 사람으로 보고 막는다
        assertThatThrownBy(() -> applicationService.submit(재신청))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.APPLICATION_ALREADY_PENDING);

        // then — 대기 행은 여전히 하나다. 우회로 두 건이 쌓이지 않았다
        assertThat(대기_신청_수()).isEqualTo(1);
    }

    /**
     * V102 · PR #108 1차 리뷰 — 사전 검사만으로는 동시 요청 둘이 함께 통과한다.
     *
     * <p>스레드를 띄우지 않는다. 경합 재현은 CI에서 플래키하다(tests.md §2). 대신 검사를
     * 우회해 직접 행을 넣어 <b>제약이 실제로 있고 문다</b>는 것을 확인한다 — 경합에서
     * 진 요청이 닿는 자리가 여기다.
     *
     * <p>표기를 바꿔 넣는 것은 인덱스가 {@code lower(email)}로 걸렸는지 함께 보기 위해서다.
     * 조회는 소문자로 비교하는데 인덱스가 원문으로 걸리면 검사가 막은 것을 DB가 통과시킨다.
     */
    @Test
    void 대기_신청이_있으면_같은_이메일_행이_DB에서도_거부된다() {
        // given — 김서연의 검토 대기 신청이 하나 있다
        applicationService.submit(new CreateApplicationRequest(
                "한빛오피스", 사업자번호, 소문자_이메일, "김서연"));

        // when — 서비스 검사를 우회해 대문자 표기로 대기 행을 직접 넣으면
        // then — 부분 유니크 인덱스가 막는다
        assertThatThrownBy(() -> 신청_행을_직접_넣는다("PENDING", 소문자_이메일.toUpperCase()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /**
     * Q-15 — 반려 이력을 남기고 재신청을 허용한 결정이 제약에 갇히면 안 된다.
     *
     * <p>{@code uk_application_pending}을 부분 인덱스가 아니라 전체 유니크로 걸면, 한 번
     * 반려된 사람은 그 이메일로 영원히 다시 신청할 수 없다. 조건절이 그것을 막는다.
     */
    @Test
    void 종결된_신청은_같은_이메일이라도_제약을_받지_않는다() {
        // given — 김서연의 검토 대기 신청이 하나 있다
        applicationService.submit(new CreateApplicationRequest(
                "한빛오피스", 사업자번호, 소문자_이메일, "김서연"));

        // when, then — 같은 이메일의 반려·승인 행은 몇 건이든 공존한다 (이력 보존)
        신청_행을_직접_넣는다("REJECTED", 소문자_이메일);
        신청_행을_직접_넣는다("REJECTED", 소문자_이메일);
        신청_행을_직접_넣는다("APPROVED", 소문자_이메일);

        // then — 대기 행은 여전히 하나뿐이다
        assertThat(대기_신청_수()).isEqualTo(1);
    }

    /** 서비스의 사전 검사를 거치지 않고 행을 심는다 — 제약 자체를 보기 위한 우회다. */
    private void 신청_행을_직접_넣는다(String 상태, String 이메일) {
        jdbc.update("""
                insert into application (id, company_name, business_no, email, applicant_name, status)
                values (?, '한빛오피스', ?, ?, '김서연', ?)
                """, UUID.randomUUID(), 사업자번호, 이메일, 상태);
    }

    private Integer 대기_신청_수() {
        return jdbc.queryForObject(
                "select count(*) from application where lower(email) = ? and status = 'PENDING'",
                Integer.class, 소문자_이메일);
    }
}
