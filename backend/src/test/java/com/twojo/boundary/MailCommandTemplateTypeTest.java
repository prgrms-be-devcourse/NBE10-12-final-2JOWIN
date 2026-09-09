package com.twojo.boundary;

import static org.assertj.core.api.Assertions.assertThat;

import com.twojo.boundary.MailCommand.TemplateType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MailCommandTemplateTypeTest {

    @Test
    @DisplayName("QUOTE_REMIND의 ref_type은 QUOTE — refId가 토큰이 아닌 견적을 가리킨다")
    void quoteRemind_refType() {
        assertThat(TemplateType.QUOTE_REMIND.refType()).isEqualTo("QUOTE");
    }

    @Test
    @DisplayName("QUOTE_REMIND는 플랫폼 발송이 아니다 — companyId가 필수다")
    void quoteRemind_notPlatformIssued() {
        assertThat(TemplateType.QUOTE_REMIND.isPlatformIssued()).isFalse();
    }
}
