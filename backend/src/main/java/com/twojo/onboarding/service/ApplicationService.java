package com.twojo.onboarding.service;

import com.twojo.boundary.MemberQuery;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import com.twojo.onboarding.dto.ApplicationResponse;
import com.twojo.onboarding.dto.CreateApplicationRequest;
import com.twojo.onboarding.entity.Application;
import com.twojo.onboarding.repository.ApplicationRepository;
import java.time.Instant;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용 신청 접수 (ON-01·02) — 비로그인 경로.
 *
 * <p>인증 없이 누구나 부를 수 있다. 그래서 응답이 갈리는 자리를 만들지 않는 것이 중요한데,
 * 여기서는 갈려야 한다 — 07 §A가 두 충돌을 서로 다른 코드로 규정한다. 가입 여부를 훑는
 * 데 쓰일 수 있지만, 접수 결과를 알려주지 않으면 신청자가 무엇을 고쳐야 할지 알 수 없다.
 *
 * <p>사업자번호 중복은 여기서 보지 않는다. 재신청을 허용하므로(Q-15) 막을 자리는 승인이다.
 *
 * <p>대기 신청 중복은 사전 검사와 {@code uk_application_pending} 두 겹으로 막는다 —
 * 검사만으로는 동시 요청 둘이 함께 통과한다 (V102).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final MemberQuery memberQuery;

    /**
     * 접수 (ON-01) — 검토 대기로 들어간다.
     *
     * <p>순서가 의미를 갖는다. 구성원 검사가 먼저다 — 이미 계정이 있는 사람에게 "검토 중인
     * 신청이 있습니다"라고 답하면 로그인하라는 안내에 닿지 못한다.
     */
    public ApplicationResponse submit(CreateApplicationRequest request) {
        String email = normalize(request.email());

        requireNotMember(email);
        requireNoPendingApplication(email);

        return ApplicationResponse.of(saveOrConflict(Application.submit(
                request.companyName().trim(),
                request.businessNo().trim(),
                email,
                request.applicantName().trim())));
    }

    /**
     * 사전 검사를 통과했는데도 부분 유니크에 걸리는 동시 요청을 409로 바꾼다.
     *
     * <p>인증 없이 누구나 부르는 경로라 그 창이 실제로 열려 있다. 검사와 INSERT 사이에
     * 다른 요청이 같은 이메일로 대기 행을 만들면 여기서 걸린다.
     *
     * <p><b>{@code save()}가 아니라 {@code saveAndFlush()}다.</b> {@code save()}는 INSERT를
     * 커밋 시점까지 미루므로 예외가 이 try 블록 밖에서 터져 500이 된다.
     *
     * <p>{@code GlobalExceptionHandler}가 아니라 여기서 잡는다 — 그 예외는 모든 도메인의
     * 모든 제약 위반에서 나므로, 어느 제약인지 아는 자리에서 잡아야 한다.
     * 이 트랜잭션에서 걸릴 수 있는 제약은 {@code uk_application_pending} 하나다.
     */
    private Application saveOrConflict(Application application) {
        try {
            return applicationRepository.saveAndFlush(application);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.APPLICATION_ALREADY_PENDING);
        }
    }

    /**
     * 이미 계정이 있으면 신청이 아니라 로그인이다 (ON-13의 뒷면).
     *
     * <p>member 테이블을 직접 보지 않고 경계로 묻는다 — 이메일은 전역 유일이라(Q-14)
     * 회사를 몰라도 판정이 된다.
     */
    private void requireNotMember(String email) {
        if (memberQuery.findCredentialByEmail(email).isPresent()) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_MEMBER);
        }
    }

    /**
     * 대기 신청이 이미 있으면 막는다 (05 §1 "막히는 것").
     *
     * <p>반려·승인된 행은 세지 않는다 — 반려 후 재신청이 Q-15의 결론이다.
     *
     * <p>초대와 달리 기한이 없다. 신청은 관리자가 처리할 때까지 유효하고, 만료 개념을
     * 두면 05 §1에 없는 전이를 만드는 것이 된다.
     */
    private void requireNoPendingApplication(String email) {
        applicationRepository.findByEmailLowerAndStatus(email, Application.Status.PENDING)
                .ifPresent(pending -> {
                    throw new BusinessException(ErrorCode.APPLICATION_ALREADY_PENDING);
                });
    }

    /**
     * 저장 값을 소문자로 맞춘다 — 조회도 소문자로 하므로 둘이 어긋나면 대기 행을 못 찾는다.
     *
     * <p>Locale.ROOT는 터키어 로케일에서 'I'가 점 없는 'ı'로 바뀌는 것을 막는다.
     */
    private String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
