package com.twojo.member.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.twojo.boundary.AccessContext;
import com.twojo.boundary.AccessScope;
import com.twojo.boundary.CompanyQuery;
import com.twojo.boundary.Role;
import com.twojo.global.error.MissingReferenceException;
import com.twojo.member.dto.UpdateMeRequest;
import com.twojo.member.repository.MemberRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 내 정보의 무결성 경로 (#165).
 *
 * <p>정상 경로의 조회·수정은 {@code MeServiceIntegrationTest}가 실제 DB로 본다.
 * 여기서는 <b>토큰이 가리키는 구성원 행이 사라진</b> 경우만 다룬다.
 *
 * <p>이 자리는 필터가 같은 행으로 인증을 통과시킨 직후다 — 없다는 것은 조회 실패가 아니라
 * 그 사이에 데이터가 깨졌다는 뜻이라 404가 아니라 500이다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class MeServiceTest {

    private static final UUID 한빛오피스 = UUID.fromString("c0000000-0000-4000-8000-000000000001");
    private static final UUID 김서연 = UUID.fromString("d0000000-0000-4000-8000-000000000001");

    private static final AccessContext 김서연_관리자 =
            new AccessContext(한빛오피스, 김서연, Role.COMPANY_ADMIN, AccessScope.COMPANY_ALL);

    @Mock private MemberRepository memberRepository;

    @Mock private CompanyQuery companyQuery;

    @InjectMocks private MeService meService;

    @Test
    @DisplayName("조회 — 토큰의 구성원 행이 사라졌으면 무결성 이상이다 (#165)")
    void 없는_구성원의_내_정보는_무결성_이상이다() {
        given(memberRepository.findById(김서연)).willReturn(Optional.empty());

        assertThatThrownBy(() -> meService.get(김서연_관리자))
                .isInstanceOf(MissingReferenceException.class)
                .hasMessageContaining("member")
                .hasMessageContaining(김서연.toString());
    }

    @Test
    @DisplayName("수정 — 같은 경로를 타므로 같은 예외다")
    void 없는_구성원의_프로필은_수정할_수_없다() {
        given(memberRepository.findById(김서연)).willReturn(Optional.empty());

        assertThatThrownBy(
                () -> meService.update(김서연_관리자, new UpdateMeRequest("김서연", "010-1234-5678")))
                .isInstanceOf(MissingReferenceException.class);
    }
}
