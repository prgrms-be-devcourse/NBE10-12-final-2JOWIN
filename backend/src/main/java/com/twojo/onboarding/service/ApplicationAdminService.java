package com.twojo.onboarding.service;

import com.twojo.auth.InitialPasswordSetup;
import com.twojo.boundary.MailCommand;
import com.twojo.boundary.MemberCommand;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import com.twojo.global.response.PageResponse;
import com.twojo.onboarding.dto.ApplicationResponse;
import com.twojo.onboarding.dto.RejectApplicationRequest;
import com.twojo.onboarding.entity.Application;
import com.twojo.onboarding.entity.Company;
import com.twojo.onboarding.repository.ApplicationRepository;
import com.twojo.onboarding.repository.CompanyRepository;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 가입 신청 심사 (ON-03~07 · 14) — 플랫폼 관리자 전용.
 *
 * <p>역할 검사가 없다. 이 서비스에 닿는 경로는 {@code /admin/api/v1/**} 체인 하나뿐이고
 * 그 체인은 플랫폼 관리자 토큰만 통과시킨다 — 구성원 역할(COMPANY_ADMIN·SALES_REP)이
 * 여기까지 오지 못한다. 회사 축이 아니라 계정 축이 갈리는 자리라 {@code AccessContext}도 없다.
 *
 * <p>승인은 <b>한 트랜잭션에서 네 가지를 한다</b> — 신청 상태 전이 · 회사 생성 · 관리자 계정
 * 생성 · 설정 링크 발급과 메일 예약. 갈라지면 회사는 생겼는데 관리자가 없거나, 계정은
 * 생겼는데 비밀번호를 정할 링크가 없는 상태가 남는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApplicationAdminService {

    /** 메일 본문은 프론트를 거치지 않는 최종 표시물이라 서버가 KST로 바꿔 넣는다. */
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private static final DateTimeFormatter EXPIRES_AT_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT);

    private static final String APPROVED_SUBJECT = "[2JO] 사용 신청이 승인되었습니다";

    private static final String APPROVED_BODY = """
            {applicantName}님, {companyName}의 2JO 사용 신청이 승인되었습니다.

            아래 링크에서 비밀번호를 설정하면 로그인할 수 있습니다.

            {link}

            이 링크는 {expiresAt} (KST)까지 유효합니다.
            """;

    private static final String REJECTED_SUBJECT = "[2JO] 사용 신청이 반려되었습니다";

    private static final String REJECTED_BODY = """
            {applicantName}님, {companyName}의 2JO 사용 신청이 반려되었습니다.

            사유: {reason}

            내용을 보완해 같은 이메일로 다시 신청할 수 있습니다.
            """;

    private final ApplicationRepository applicationRepository;
    private final CompanyRepository companyRepository;
    private final MemberCommand memberCommand;
    private final InitialPasswordSetup initialPasswordSetup;
    private final MailCommand mailCommand;

    /** 목록 (ON-03) — status를 비우면 처리된 신청도 함께 나온다. */
    public PageResponse<ApplicationResponse> list(Application.Status status, Pageable pageable) {
        return PageResponse.from(
                (status == null
                        ? applicationRepository.findAll(pageable)
                        : applicationRepository.findByStatus(status, pageable))
                        .map(ApplicationResponse::of));
    }

    /** 상세 — 신청은 회사가 생기기 전이라 스코프로 좁힐 축이 없다. 없으면 404다. */
    public ApplicationResponse get(UUID applicationId) {
        return ApplicationResponse.of(find(applicationId));
    }

    /**
     * 승인 (ON-04·06·07) — 회사와 첫 기업 관리자를 만들고 설정 링크를 메일로 보낸다.
     *
     * <p>사업자번호 중복을 상태 전이보다 먼저 본다. 뒤집으면 신청이 APPROVED로 넘어간 뒤
     * 롤백되는데, 07 §A는 이 경우를 "반려 유도"로 규정한다 — 관리자가 다시 반려할 수 있으려면
     * 신청이 대기로 남아야 한다.
     *
     * <p>비밀번호는 만들지 않는다. {@code password_hash}가 NULL인 계정이 생기고, 본인이
     * 링크로 채운다 (Q-33). 그전까지 로그인은 자격 증명 불일치로 막힌다.
     */
    @Transactional
    public ApplicationResponse approve(UUID applicationId) {
        Application application = requirePending(find(applicationId));
        Instant now = Instant.now();

        requireBusinessNoFree(application.getBusinessNo());

        Company company = companyRepository.save(Company.create(
                application.getId(), application.getCompanyName(), application.getBusinessNo()));

        UUID adminMemberId = memberCommand.createCompanyAdmin(
                company.getId(), application.getEmail(), application.getApplicantName());

        application.approve(now);

        sendApprovedMail(application, company.getId(),
                initialPasswordSetup.issueLink(adminMemberId, now));

        return ApplicationResponse.of(application);
    }

    /**
     * 반려 (ON-05·14) — 사유를 남기고 행을 보존한다. 같은 이메일로 재신청할 수 있다 (Q-15).
     */
    @Transactional
    public ApplicationResponse reject(UUID applicationId, RejectApplicationRequest request) {
        Application application = requirePending(find(applicationId));

        application.reject(request.reason().trim(), Instant.now());

        sendRejectedMail(application);

        return ApplicationResponse.of(application);
    }

    private Application find(UUID applicationId) {
        return applicationRepository.findById(applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    /** 처리된 신청은 되살아나지 않는다 — 승인·반려 둘 다 대기 상태에서만 가능하다. */
    private Application requirePending(Application application) {
        if (!application.isPending()) {
            throw new BusinessException(ErrorCode.APPLICATION_ALREADY_DECIDED);
        }
        return application;
    }

    /**
     * 사업자번호당 테넌트는 하나다 (ERD 전역 UNIQUE).
     *
     * <p>{@code company} 쪽만 본다. 같은 번호의 대기 신청이 여럿 있는 것은 막지 않는다 —
     * 재신청 허용(Q-15)이 그것을 전제하고, 첫 승인이 나면 나머지가 여기서 걸린다.
     */
    private void requireBusinessNoFree(String businessNo) {
        if (companyRepository.existsByBusinessNo(businessNo)) {
            throw new BusinessException(ErrorCode.COMPANY_BUSINESS_NO_DUPLICATED);
        }
    }

    /**
     * 승인 통보 = 비밀번호 설정 링크 (NT-13 · Q-33).
     *
     * <p>{@code refId}는 신청서 id다 — 승인은 신청당 한 번뿐이라 멱등 키
     * {@code (type, refId, recipientEmail)}가 그대로 재발송을 막는다. 토큰 id를 쓰는
     * 재설정 메일과 갈리는 이유가 그것이다.
     *
     * <p>수신 주소는 신청서의 이메일이다. 접수 때 이미 소문자로 정규화돼 저장됐다 —
     * 멱등 키의 일부라 표기가 흔들리면 중복 방어가 무력해진다.
     */
    private void sendApprovedMail(Application application, UUID companyId,
                                  InitialPasswordSetup.SetupLink link) {
        mailCommand.schedule(
                MailCommand.TemplateType.SIGNUP_APPROVED,
                companyId,
                application.getEmail(),
                application.getId(),
                APPROVED_SUBJECT,
                renderApprovedBody(application, link));
    }

    /**
     * 반려 통보 (NT-13 · ON-06).
     *
     * <p>회사 id 자리가 null이다 — 반려는 회사를 만들지 않는다. 계약이 가입 통보 계열에만
     * 이 자리를 비워 두도록 허용한다.
     *
     * <p>발송 식별자는 신청서 id다. 한 신청은 한 번만 반려되므로 중복 발송을 막는 키가 그대로
     * 재발송을 막는다 — 승인 통보와 같은 방식이다.
     */
    private void sendRejectedMail(Application application) {
        mailCommand.schedule(
                MailCommand.TemplateType.SIGNUP_REJECTED,
                null,
                application.getEmail(),
                application.getId(),
                REJECTED_SUBJECT,
                renderRejectedBody(application));
    }

    /**
     * 반려 사유를 본문에 싣는다. 사유를 기록하게 한 것(ON-14)과 결과를 통보하는 것(ON-06)이
     * 이어지지 않으면, 받는 사람은 무엇을 고쳐 다시 신청해야 하는지 알 수 없다.
     */
    private String renderRejectedBody(Application application) {
        return REJECTED_BODY
                .replace("{applicantName}", application.getApplicantName())
                .replace("{companyName}", application.getCompanyName())
                .replace("{reason}", application.getRejectReason());
    }

    /**
     * 평문 최소 렌더 — 재설정 안내와 같은 수준이다. 템플릿 엔진도 확정 문안도 아직 없다.
     *
     * <p>formatted() 대신 replace를 쓴다. 포맷 문자열의 줄바꿈은 %n이어야 하는데 그 값은
     * 실행 환경을 따라가고, 메일 본문의 줄바꿈은 환경과 무관해야 한다.
     */
    private String renderApprovedBody(Application application, InitialPasswordSetup.SetupLink link) {
        return APPROVED_BODY
                .replace("{applicantName}", application.getApplicantName())
                .replace("{companyName}", application.getCompanyName())
                .replace("{link}", link.url())
                .replace("{expiresAt}", EXPIRES_AT_FORMAT.format(link.expiresAt().atZone(SEOUL)));
    }
}
