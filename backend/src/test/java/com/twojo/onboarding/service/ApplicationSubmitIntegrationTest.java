package com.twojo.onboarding.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import com.twojo.onboarding.dto.CreateApplicationRequest;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * 대기 신청의 이메일 점유는 표기를 타지 않는다 (05 §1 · Q-14).
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

    private Integer 대기_신청_수() {
        return jdbc.queryForObject(
                "select count(*) from application where lower(email) = ? and status = 'PENDING'",
                Integer.class, 소문자_이메일);
    }
}
