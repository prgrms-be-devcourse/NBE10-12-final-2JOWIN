package com.twojo.onboarding.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.twojo.onboarding.repository.CompanyRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CompanyQueryServiceTest {

    @Mock private CompanyRepository companyRepository;
    @InjectMocks private CompanyQueryService companyQueryService;

    /**
     * 이 값을 묻는 자리는 전부 인증 경로다 — 판정할 수 없을 때 "정지 아님"으로 답하면 인증이 열린다.
     * 라이브러리 동작이 아니라 우리가 고른 기본값이라 고정해 둔다.
     */
    @Test
    void 없는_회사는_정지된_것으로_간주된다() {
        // given — 회사 행이 없다 (데이터 이상 · 잘못된 id)
        UUID 없는_회사 = UUID.randomUUID();
        given(companyRepository.findById(없는_회사)).willReturn(Optional.empty());

        // then — 여는 쪽이 아니라 막는 쪽으로 접는다
        assertThat(companyQueryService.isSuspended(없는_회사)).isTrue();
    }
}
