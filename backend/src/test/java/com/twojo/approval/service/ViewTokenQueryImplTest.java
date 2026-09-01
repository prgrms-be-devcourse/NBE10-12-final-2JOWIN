package com.twojo.approval.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.twojo.approval.repository.QuoteViewTokenRepository;
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
 * {@link ViewTokenQueryImpl} — CU-14 판정을 리포지토리에 위임하는지만 검증한다.
 * 판정 로직이 없으므로 리포지토리 결과가 그대로 전달되는지가 전부다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ViewTokenQueryImplTest {

    @Mock
    private QuoteViewTokenRepository quoteViewTokenRepository;

    @InjectMocks
    private ViewTokenQueryImpl viewTokenQuery;

    @Test
    @DisplayName("수신인 지정 이력이 있으면 리포지토리 결과를 그대로 true로 반환한다")
    void 발송_이력이_있으면_true를_반환한다() {
        UUID contactId = UUID.randomUUID();
        given(quoteViewTokenRepository.existsByRecipientContactId(contactId)).willReturn(true);

        assertThat(viewTokenQuery.existsForContact(contactId)).isTrue();
    }

    @Test
    @DisplayName("수신인 지정 이력이 없으면 false를 반환한다")
    void 발송_이력이_없으면_false를_반환한다() {
        UUID contactId = UUID.randomUUID();
        given(quoteViewTokenRepository.existsByRecipientContactId(contactId)).willReturn(false);

        assertThat(viewTokenQuery.existsForContact(contactId)).isFalse();
    }
}
