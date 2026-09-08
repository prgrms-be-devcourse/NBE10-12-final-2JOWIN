package com.twojo.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.twojo.boundary.MemberQuery;
import com.twojo.boundary.Role;
import com.twojo.global.error.MissingReferenceException;
import com.twojo.member.entity.Member;
import com.twojo.member.repository.MemberRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * member 모듈이 밖에 내보이는 유일한 조회 경로 (boundary/MemberQuery · 11 §2 · §7.3).
 *
 * <p>이 클래스의 판정은 <b>없는 id를 어떻게 답하느냐</b>로 갈린다 — 그리고 그 답이
 * 메서드마다 다른 것이 규칙이다.
 *
 * <ul>
 *   <li>{@code get} · {@code getCredential} · {@code getContact} — 호출부가 FK로 존재를
 *       보장받는 자리다. 없으면 <b>데이터 이상</b>이라 {@link MissingReferenceException} (#165)</li>
 *   <li>{@code isActive} · {@code findCredentialByEmail} — 존재를 <b>묻는</b> 자리다.
 *       없는 것이 정상 답이라 던지지 않는다 (SC-09)</li>
 * </ul>
 *
 * <p>앞의 셋이 404를 내던 것이 #165의 문제였다. 그 404는 경계 밖(B {@code CustomerService} ·
 * C {@code DealService})으로 새어, 멀쩡한 고객사·Deal을 "없거나 권한 없음"으로 보이게 만들었다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class MemberQueryServiceTest {

    private static final UUID 한빛오피스 = UUID.fromString("c0000000-0000-4000-8000-000000000001");
    private static final UUID 김서연 = UUID.fromString("d0000000-0000-4000-8000-000000000001");
    private static final UUID 없는_구성원 = UUID.fromString("d0000000-0000-4000-8000-0000000000ff");

    @Mock private MemberRepository memberRepository;

    @Mock private Member member;

    @InjectMocks private MemberQueryService memberQueryService;

    @Nested
    @DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
    class FK가_보장하는_조회는 {

        /**
         * {@code customer.created_by_member_id} · {@code deal.assignee_member_id}가 가리키는
         * 자리다. 없다는 것은 조회 실패가 아니라 참조 무결성이 깨졌다는 뜻이다.
         */
        @Test
        void get은_행이_없으면_무결성_이상으로_터진다() {
            given(memberRepository.findById(없는_구성원)).willReturn(Optional.empty());

            assertThatThrownBy(() -> memberQueryService.get(없는_구성원))
                    .isInstanceOf(MissingReferenceException.class)
                    .hasMessageContaining("member")
                    .hasMessageContaining(없는_구성원.toString());
        }

        /** 필터·회전·비밀번호 변경이 부른다 — 직전 단계가 같은 행을 이미 확인한 뒤다. */
        @Test
        void getCredential도_같다() {
            given(memberRepository.findById(없는_구성원)).willReturn(Optional.empty());

            assertThatThrownBy(() -> memberQueryService.getCredential(없는_구성원))
                    .isInstanceOf(MissingReferenceException.class);
        }

        /** D의 열람 페이지 담당자 표시 (AP-18) — deal.assignee_member_id FK. */
        @Test
        void getContact도_같다() {
            given(memberRepository.findById(없는_구성원)).willReturn(Optional.empty());

            assertThatThrownBy(() -> memberQueryService.getContact(없는_구성원))
                    .isInstanceOf(MissingReferenceException.class);
        }

        /**
         * 무결성 이상을 404로 답하면 안 되는 이유가 이것이다 — 이 예외는 인증 필터가
         * 골라 잡을 수 있어야 하고(401 유지), 나머지 경로에서는 폴백 핸들러가 500으로 바꾼다.
         */
        @Test
        @DisplayName("IllegalStateException을 상속한다 — 필터가 이 타입만 골라 잡는 근거 (#165)")
        void 상태_오류_계열이다() {
            given(memberRepository.findById(없는_구성원)).willReturn(Optional.empty());

            assertThatThrownBy(() -> memberQueryService.get(없는_구성원))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
    class 존재를_묻는_조회는 {

        /**
         * SC-09 — 타사 id를 넘겼을 때 "비활성"과 "존재하지 않음"이 구별되면 안 된다.
         * 던지면 그 차이가 응답으로 새므로 여기서는 false가 정답이다.
         */
        @Test
        void isActive는_없는_id에_false를_돌려준다() {
            given(memberRepository.findById(없는_구성원)).willReturn(Optional.empty());

            assertThat(memberQueryService.isActive(없는_구성원)).isFalse();
        }

        /** 로그인 경로다 — 미가입 이메일에 던지면 응답이 갈려 가입 여부가 드러난다 (SC-09). */
        @Test
        void findCredentialByEmail은_없는_이메일에_빈_값을_돌려준다() {
            given(memberRepository.findByEmailLower("nobody@hanbit.co.kr"))
                    .willReturn(Optional.empty());

            assertThat(memberQueryService.findCredentialByEmail("nobody@hanbit.co.kr")).isEmpty();
        }

        /** 정규화를 여기서 한다 — 호출자에게 맡기면 한 곳만 빠뜨려도 조회가 조용히 실패한다. */
        @Test
        void findCredentialByEmail은_대소문자와_공백을_정규화한다() {
            given(memberRepository.findByEmailLower("seoyeon@hanbit.co.kr"))
                    .willReturn(Optional.of(member));
            given(member.getId()).willReturn(김서연);
            given(member.getCompanyId()).willReturn(한빛오피스);
            given(member.getRole()).willReturn(Role.COMPANY_ADMIN);

            assertThat(memberQueryService.findCredentialByEmail("  SeoYeon@Hanbit.CO.KR  "))
                    .isPresent();
        }

        /** null·빈 문자열이 리포지토리까지 내려가지 않는다. */
        @Test
        void findCredentialByEmail은_빈_입력을_바로_접는다() {
            assertThat(memberQueryService.findCredentialByEmail(null)).isEmpty();
            assertThat(memberQueryService.findCredentialByEmail("   ")).isEmpty();
        }
    }

    @Nested
    @DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
    class 경계_밖으로_나가는_값은 {

        /**
         * 필드를 하나 빠뜨려도 컴파일은 통과하고 화면에는 빈칸만 뜬다 —
         * B·C가 이 record로 담당자 이름을 찍는다.
         */
        @Test
        void MemberSummary_세_필드를_모두_옮긴다() {
            given(member.getId()).willReturn(김서연);
            given(member.getName()).willReturn("김서연");
            given(member.isActive()).willReturn(true);
            given(memberRepository.findById(김서연)).willReturn(Optional.of(member));

            assertThat(memberQueryService.get(김서연))
                    .isEqualTo(new MemberQuery.MemberSummary(김서연, "김서연", true));
        }

        /** D의 AP-18이 이름·이메일·연락처를 그대로 쓴다. */
        @Test
        void MemberContact_세_필드를_모두_옮긴다() {
            given(member.getName()).willReturn("김서연");
            given(member.getEmail()).willReturn("seoyeon@hanbit.co.kr");
            given(member.getPhone()).willReturn("010-1234-5678");
            given(memberRepository.findById(김서연)).willReturn(Optional.of(member));

            assertThat(memberQueryService.getContact(김서연)).isEqualTo(
                    new MemberQuery.MemberContact("김서연", "seoyeon@hanbit.co.kr", "010-1234-5678"));
        }

        /** Q-26 폴백 수신자 — 활성 기업 관리자만. MB-11이 최소 1명을 보장한다. */
        @Test
        void findAdminIds는_활성_관리자의_id만_돌려준다() {
            given(member.getId()).willReturn(김서연);
            given(memberRepository.findByCompanyIdAndRoleAndStatus(
                    한빛오피스, Role.COMPANY_ADMIN, Member.Status.ACTIVE))
                    .willReturn(List.of(member));

            assertThat(memberQueryService.findAdminIds(한빛오피스)).containsExactly(김서연);
        }

        /** ON-12 이용 현황 (Q-41) — 비활성 구성원도 센다. */
        @Test
        void countByCompany는_비활성도_포함한_수다() {
            given(memberRepository.countByCompanyId(한빛오피스)).willReturn(7L);

            assertThat(memberQueryService.countByCompany(한빛오피스)).isEqualTo(7);
        }
    }
}
