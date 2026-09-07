package com.twojo.onboarding.entity;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * V101이 신청자 이름을 필수로 만든다 (08 v1.6.11 · ON-07).
 *
 * <p>승인은 이 값을 {@code member.name}(NOT NULL)으로 복사한다. 신청 쪽이 비어 있을 수 있으면
 * 승인 시점에 넣을 값이 없어 회사 생성까지 진행한 뒤 제약 위반 500으로 끝난다 — 막을 자리는
 * 접수다.
 *
 * <p><b>백필 자체는 테스트하지 않는다.</b> {@code UPDATE ... WHERE applicant_name IS NULL}은
 * 마이그레이션 이전에 심긴 행에만 의미가 있고, 마이그레이션이 이미 끝난 DB에서는 그 상황을
 * 다시 만들 수 없다. 여기서 고정하는 것은 마이그레이션이 실제로 적용됐고 제약이 살아 있다는
 * 것이다 — 그 둘이 이 컬럼을 쓰는 모든 코드의 전제다.
 *
 * <p>DB 제약을 직접 확인하므로 실 DB가 필요하다 (tests.md §2 — 마이그레이션).
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ApplicantNameMigrationTest {

    @Autowired private JdbcTemplate jdbc;

    @Test
    void 신청자_이름_없이는_신청_행을_만들_수_없다() {
        // given — 이름 칸만 비운 신청. 나머지 필수 값은 전부 채웠다
        UUID 신청_ID = UUID.randomUUID();
        String 사업자번호 = 신청_ID.toString().substring(0, 13);

        // when — 그대로 넣으면
        // then — NOT NULL 제약이 막는다. 이름 없는 신청은 승인될 수 없다
        assertThatThrownBy(() -> jdbc.update("""
                insert into application (id, company_name, business_no, email, status)
                values (?, '한빛오피스', ?, 'seoyeon@hanbit.co.kr', 'PENDING')
                """, 신청_ID, 사업자번호))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
