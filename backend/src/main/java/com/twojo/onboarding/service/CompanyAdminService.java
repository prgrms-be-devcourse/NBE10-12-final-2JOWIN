package com.twojo.onboarding.service;

import com.twojo.auth.SessionRevoker;
import com.twojo.boundary.MemberQuery;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import com.twojo.global.response.PageResponse;
import com.twojo.onboarding.dto.CompanyResponse;
import com.twojo.onboarding.dto.SuspendCompanyRequest;
import com.twojo.onboarding.entity.Company;
import com.twojo.onboarding.repository.CompanyRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회사 목록·정지·해제 (ON-08·10·12) — 플랫폼 관리자 전용.
 *
 * <p>영업 데이터에 닿지 않는다 (ON-11). 이용 현황은 구성원 수 하나이고(Q-41), 그 수도
 * member 테이블을 직접 읽지 않고 {@link MemberQuery}로 묻는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompanyAdminService {

    private final CompanyRepository companyRepository;
    private final MemberQuery memberQuery;
    private final SessionRevoker sessionRevoker;

    /**
     * 목록 (ON-12) — 정지된 회사도 함께 나온다. 정지 해제를 하려면 보여야 한다.
     *
     * <p>구성원 수는 회사마다 한 번씩 센다. 한 페이지가 최대 100행이라 그만큼의 count가
     * 나가는데, 회사 목록은 플랫폼 관리자만 여는 화면이라 그 비용을 감수한다. 줄이려면
     * 경계에 "회사 여럿의 수를 한 번에" 같은 메서드가 필요하고, 그건 member 소유 결정이다.
     */
    public PageResponse<CompanyResponse> list(Pageable pageable) {
        return PageResponse.from(companyRepository.findAll(pageable)
                .map(company -> CompanyResponse.of(
                        company, memberQuery.countByCompany(company.getId()))));
    }

    /**
     * 정지 (ON-08·09) — 상태를 넘기고 해당 회사 전 구성원의 refresh를 폐기한다.
     *
     * <p><b>폐기가 차단의 실체다.</b> 상태만 바꾸면 이미 발급된 access token이 수명만큼
     * 살아 있고, refresh로 계속 갱신되어 최대 14일간 정상 이용이 된다 — ON-09가 무력해진다.
     *
     * <p>같은 트랜잭션에서 돈다. 갈라지면 정지됐는데 세션이 남은 창이 생긴다.
     *
     * <p>이미 정지된 회사를 다시 정지하는 것은 막지 않는다. 05 §2에 그 전이가 없어 쓸 에러
     * 코드가 없고, 사유를 고쳐 쓰는 것이 관리자가 원할 법한 동작이다. 폐기는 멱등이다.
     */
    @Transactional
    public CompanyResponse suspend(UUID companyId, SuspendCompanyRequest request) {
        Company company = find(companyId);
        Instant now = Instant.now();

        company.suspend(request.reason().trim());
        sessionRevoker.revokeOnSuspension(companyId, now);

        return toResponse(company);
    }

    /**
     * 정지 해제 (ON-10) — 데이터는 그대로다.
     *
     * <p>세션을 되살리지 않는다. 폐기된 refresh 행은 되돌릴 수 없고, 되돌린다면 정지 중
     * 탈취된 토큰까지 살아난다. 구성원은 재로그인한다 (Q-27).
     *
     * <p>고객 열람 링크와 배치 알림은 손대지 않아도 자동 복구된다 — 정지 중에도 링크 상태를
     * 건드리지 않았고, 판정이 회사 상태를 그때그때 보기 때문이다 (Q-27).
     */
    @Transactional
    public CompanyResponse reactivate(UUID companyId) {
        Company company = find(companyId);

        company.reactivate();

        return toResponse(company);
    }

    private Company find(UUID companyId) {
        return companyRepository.findById(companyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private CompanyResponse toResponse(Company company) {
        return CompanyResponse.of(company, memberQuery.countByCompany(company.getId()));
    }
}
