package com.twojo.approval.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.twojo.approval.entity.QuoteViewToken;
import com.twojo.approval.repository.QuoteViewTokenRepository;
import com.twojo.approval.token.TokenGenerator;
import com.twojo.boundary.CustomerQuery;
import com.twojo.boundary.MailCommand;
import com.twojo.boundary.QuoteQuery;
import com.twojo.boundary.ViewTokenCommand;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * {@link ViewTokenCommandImpl} — 계약 구현이 협력자(QuoteQuery·CustomerQuery·MailCommand)를 올바른
 * 순서·인자로 부르는지 검증한다. DB 제약·트랜잭션은 목으로 재현 불가하므로 호출 순서(flush→save)와
 * 렌더 결과(링크·유효기간·정규화)를 단언해 고정한다. 전이 규칙 자체는 {@code QuoteViewTokenTest}가 커버한다.
 *
 * <p>{@code issue()} — 신규 발급·재발송·DRAFT 미참조·예외 전파·해시 왕복.
 * <p>{@code expire()} — ACTIVE 위임·사유 매핑·무동작 멱등.
 */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ViewTokenCommandImplTest {

    private static final UUID QUOTE_ID = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final UUID CONTACT_ID = UUID.fromString("b0000000-0000-4000-8000-000000000001");
    private static final UUID COMPANY_ID = UUID.fromString("c0000000-0000-4000-8000-000000000001");
    /** save()가 persist하며 채우는 id — 목이 심어 반환한다. issue()가 이 값을 refId로 넘긴다. */
    private static final UUID SAVED_TOKEN_ID = UUID.fromString("d0000000-0000-4000-8000-000000000001");
    private static final LocalDate VALID_UNTIL = LocalDate.of(2026, 9, 2);
    private static final Instant EXPIRES_AT_KST_2359 = Instant.parse("2026-09-02T14:59:59Z"); // 23:59:59 Asia/Seoul
    private static final String QUOTE_NO = "Q-2608-014";
    private static final String BASE_URL = "http://localhost:5173";

    @Mock
    private QuoteViewTokenRepository quoteViewTokenRepository;
    @Mock
    private QuoteQuery quoteQuery;
    @Mock
    private CustomerQuery customerQuery;
    @Mock
    private MailCommand mailCommand;

    private ViewTokenCommandImpl viewTokenCommand;

    @BeforeEach
    void setUp() {
        // TokenGenerator는 의존성이 없어 실객체 — @InjectMocks는 String baseUrl을 못 채워 생성자에서 NPE.
        viewTokenCommand = new ViewTokenCommandImpl(
                quoteViewTokenRepository, quoteQuery, customerQuery, new TokenGenerator(), mailCommand, BASE_URL);
        // 실 PG면 persist가 id를 채우지만 목은 인메모리 — issue()가 save 반환값의 getId()를 refId로 쓰므로 심어 반환.
        lenient().when(quoteViewTokenRepository.save(any(QuoteViewToken.class)))
                .thenAnswer(inv -> {
                    QuoteViewToken token = inv.getArgument(0);
                    ReflectionTestUtils.setField(token, "id", SAVED_TOKEN_ID);
                    return token;
                });
    }

    private static QuoteViewToken activeToken() {
        return QuoteViewToken.issue(QUOTE_ID, UUID.randomUUID(), "hash", Instant.now().plusSeconds(3600));
    }

    private static QuoteQuery.PublicQuoteView view() {
        return viewWithStatus("DRAFT");
    }

    private static QuoteQuery.PublicQuoteView viewWithStatus(String status) {
        return new QuoteQuery.PublicQuoteView(QUOTE_ID, QUOTE_NO, status, "EXCLUSIVE",
                "설치는 납품일로부터 3일 이내", VALID_UNTIL, 3_050_000L, 305_000L, 3_355_000L,
                List.of(), UUID.randomUUID(), COMPANY_ID);
    }

    private static CustomerQuery.ContactSummary contact() {
        return contact("박지훈", "jihun@hanbit.co.kr");
    }

    private static CustomerQuery.ContactSummary contact(String name, String email) {
        return new CustomerQuery.ContactSummary(CONTACT_ID, name, "부장", email);
    }

    private void givenHappyPath() {
        given(quoteQuery.getPublicView(QUOTE_ID)).willReturn(view());
        given(customerQuery.getContact(CONTACT_ID)).willReturn(contact());
        given(quoteViewTokenRepository.findActiveByQuoteId(QUOTE_ID)).willReturn(Optional.empty());
    }

    private QuoteViewToken captureSaved() {
        ArgumentCaptor<QuoteViewToken> captor = ArgumentCaptor.forClass(QuoteViewToken.class);
        verify(quoteViewTokenRepository).save(captor.capture());
        return captor.getValue();
    }

    private String captureBody() {
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(mailCommand).schedule(any(), any(), any(), any(), any(), body.capture());
        return body.getValue();
    }

    // ─────────────────────────────── issue() — 신규 발급 ───────────────────────────────

    @Test
    @DisplayName("신규 발급 — 해시(64자 hex)·expiresAt(당일 23:59:59 KST)·수신인·ACTIVE로 토큰을 저장하고 flush는 부르지 않는다")
    void 신규발급_토큰을_저장한다() {
        givenHappyPath();

        viewTokenCommand.issue(QUOTE_ID, CONTACT_ID);

        QuoteViewToken saved = captureSaved();
        assertThat(saved.getTokenHash()).matches("[0-9a-f]{64}");
        assertThat(saved.getExpiresAt()).isEqualTo(EXPIRES_AT_KST_2359);
        assertThat(saved.getQuoteId()).isEqualTo(QUOTE_ID);
        assertThat(saved.getRecipientContactId()).isEqualTo(CONTACT_ID);
        assertThat(saved.getStatus()).isEqualTo(QuoteViewToken.Status.ACTIVE);
        verify(quoteViewTokenRepository, never()).flush();
    }

    @Test
    @DisplayName("신규 발급 — QUOTE_SENT·companyId·정규화 이메일·refId=발급된 토큰 id로 예약하고, 제목엔 견적번호, 본문엔 링크와 유효기간이 있다")
    void 신규발급_메일을_예약한다() {
        given(quoteQuery.getPublicView(QUOTE_ID)).willReturn(view());
        given(customerQuery.getContact(CONTACT_ID)).willReturn(contact("박지훈", "jihun@hanbit.co.kr"));
        given(quoteViewTokenRepository.findActiveByQuoteId(QUOTE_ID)).willReturn(Optional.empty());

        viewTokenCommand.issue(QUOTE_ID, CONTACT_ID);

        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(mailCommand).schedule(
                eq(MailCommand.TemplateType.QUOTE_SENT), eq(COMPANY_ID), eq("jihun@hanbit.co.kr"),
                eq(SAVED_TOKEN_ID), subject.capture(), body.capture());
        assertThat(subject.getValue()).contains(QUOTE_NO);
        assertThat(body.getValue())
                .contains(BASE_URL + "/q/")
                .contains("유효기간: 2026-09-02");
    }

    @Test
    @DisplayName("DRAFT 전제 — PublicQuoteView.status가 null이어도 발급이 성공한다 (status를 읽지 않는다)")
    void DRAFT_status를_읽지_않는다() {
        given(quoteQuery.getPublicView(QUOTE_ID)).willReturn(viewWithStatus(null));
        given(customerQuery.getContact(CONTACT_ID)).willReturn(contact());
        given(quoteViewTokenRepository.findActiveByQuoteId(QUOTE_ID)).willReturn(Optional.empty());

        assertThatCode(() -> viewTokenCommand.issue(QUOTE_ID, CONTACT_ID)).doesNotThrowAnyException();
        verify(quoteViewTokenRepository).save(any(QuoteViewToken.class));
    }

    @Test
    @DisplayName("메일 링크의 원문을 다시 해시하면 저장된 tokenHash와 일치한다 — 저장은 해시, URL은 원문")
    void 링크_원문의_해시가_저장된_tokenHash와_일치한다() {
        givenHappyPath();

        viewTokenCommand.issue(QUOTE_ID, CONTACT_ID);

        String raw = extractRawToken(captureBody());
        assertThat(raw).matches("[A-Za-z0-9_-]+");
        assertThat(captureSaved().getTokenHash())
                .isEqualTo(new TokenGenerator().hash(raw))
                .isNotEqualTo(raw);
    }

    @Test
    @DisplayName("수신 이메일을 trim + 소문자로 정규화해 예약한다 (멱등 키 안정)")
    void 수신_이메일을_정규화한다() {
        given(quoteQuery.getPublicView(QUOTE_ID)).willReturn(view());
        given(customerQuery.getContact(CONTACT_ID)).willReturn(contact("박지훈", "  Jihun@Hanbit.CO.KR  "));
        given(quoteViewTokenRepository.findActiveByQuoteId(QUOTE_ID)).willReturn(Optional.empty());

        viewTokenCommand.issue(QUOTE_ID, CONTACT_ID);

        verify(mailCommand).schedule(any(), any(), eq("jihun@hanbit.co.kr"), any(), any(), any());
    }

    @Test
    @DisplayName("연락처 이름이 null이면 본문이 \"고객\"으로 시작한다 (계약 반환값 방어)")
    void 이름이_null이면_고객으로_대체한다() {
        given(quoteQuery.getPublicView(QUOTE_ID)).willReturn(view());
        given(customerQuery.getContact(CONTACT_ID)).willReturn(contact(null, "jihun@hanbit.co.kr"));
        given(quoteViewTokenRepository.findActiveByQuoteId(QUOTE_ID)).willReturn(Optional.empty());

        viewTokenCommand.issue(QUOTE_ID, CONTACT_ID);

        assertThat(captureBody()).startsWith("고객님,");
    }

    @Test
    @DisplayName("base-url 끝 슬래시를 제거해 링크가 \"//q/\"가 되지 않는다")
    void baseUrl_끝_슬래시를_정규화한다() {
        ViewTokenCommandImpl withSlash = new ViewTokenCommandImpl(
                quoteViewTokenRepository, quoteQuery, customerQuery, new TokenGenerator(), mailCommand,
                "http://localhost:5173/");
        given(quoteQuery.getPublicView(QUOTE_ID)).willReturn(view());
        given(customerQuery.getContact(CONTACT_ID)).willReturn(contact());
        given(quoteViewTokenRepository.findActiveByQuoteId(QUOTE_ID)).willReturn(Optional.empty());

        withSlash.issue(QUOTE_ID, CONTACT_ID);

        assertThat(captureBody())
                .contains("http://localhost:5173/q/")
                .doesNotContain("//q/");
    }

    // ─────────────────────────────── issue() — 재발송 ───────────────────────────────

    @Test
    @DisplayName("재발송 — 같은 견적에 ACTIVE 링크가 있으면 RESENT 사유로 만료시킨다")
    void 재발송_기존_링크를_RESENT로_만료한다() {
        QuoteViewToken existing = activeToken();
        given(quoteQuery.getPublicView(QUOTE_ID)).willReturn(view());
        given(customerQuery.getContact(CONTACT_ID)).willReturn(contact());
        given(quoteViewTokenRepository.findActiveByQuoteId(QUOTE_ID)).willReturn(Optional.of(existing));

        viewTokenCommand.issue(QUOTE_ID, CONTACT_ID);

        assertThat(existing.getStatus()).isEqualTo(QuoteViewToken.Status.EXPIRED);
        assertThat(existing.getExpiredReason()).isEqualTo(QuoteViewToken.ExpiredReason.RESENT);
    }

    @Test
    @DisplayName("재발송 — 만료 flush를 새 토큰 save보다 먼저 호출한다 (부분 유니크 회피)")
    void 재발송_flush가_save보다_먼저다() {
        given(quoteQuery.getPublicView(QUOTE_ID)).willReturn(view());
        given(customerQuery.getContact(CONTACT_ID)).willReturn(contact());
        given(quoteViewTokenRepository.findActiveByQuoteId(QUOTE_ID)).willReturn(Optional.of(activeToken()));

        viewTokenCommand.issue(QUOTE_ID, CONTACT_ID);

        InOrder inOrder = inOrder(quoteViewTokenRepository);
        inOrder.verify(quoteViewTokenRepository).flush();
        inOrder.verify(quoteViewTokenRepository).save(any(QuoteViewToken.class));
    }

    @Test
    @DisplayName("재발송 — 메일 예약은 정확히 1회다")
    void 재발송_메일_예약은_1회다() {
        given(quoteQuery.getPublicView(QUOTE_ID)).willReturn(view());
        given(customerQuery.getContact(CONTACT_ID)).willReturn(contact());
        given(quoteViewTokenRepository.findActiveByQuoteId(QUOTE_ID)).willReturn(Optional.of(activeToken()));

        viewTokenCommand.issue(QUOTE_ID, CONTACT_ID);

        verify(mailCommand, times(1)).schedule(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("재발송 — 새 토큰의 수신인은 호출 인자값이다 (수신인 변경 AP-13)")
    void 재발송_새_토큰의_수신인은_새_인자값이다() {
        QuoteViewToken existing = QuoteViewToken.issue(
                QUOTE_ID, UUID.fromString("b0000000-0000-4000-8000-0000000000ff"), "oldhash", EXPIRES_AT_KST_2359);
        given(quoteQuery.getPublicView(QUOTE_ID)).willReturn(view());
        given(customerQuery.getContact(CONTACT_ID)).willReturn(contact());
        given(quoteViewTokenRepository.findActiveByQuoteId(QUOTE_ID)).willReturn(Optional.of(existing));

        viewTokenCommand.issue(QUOTE_ID, CONTACT_ID);

        assertThat(captureSaved().getRecipientContactId()).isEqualTo(CONTACT_ID);
    }

    @Test
    @DisplayName("재발송 — 새 토큰의 expiresAt은 validUntil로 재계산되어 연장되지 않는다 (전이표 §7 잔여 유효기간 상속)")
    void 재발송_expiresAt은_연장되지_않는다() {
        QuoteViewToken existing = QuoteViewToken.issue(QUOTE_ID, CONTACT_ID, "oldhash", EXPIRES_AT_KST_2359);
        given(quoteQuery.getPublicView(QUOTE_ID)).willReturn(view());
        given(customerQuery.getContact(CONTACT_ID)).willReturn(contact());
        given(quoteViewTokenRepository.findActiveByQuoteId(QUOTE_ID)).willReturn(Optional.of(existing));

        viewTokenCommand.issue(QUOTE_ID, CONTACT_ID);

        assertThat(captureSaved().getExpiresAt()).isEqualTo(existing.getExpiresAt());
    }

    // ─────────────────────────────── issue() — 예외 전파 ───────────────────────────────

    @Test
    @DisplayName("getPublicView가 던지면 그대로 전파하고 토큰·연락처·메일에 손대지 않는다")
    void getPublicView_예외는_부수효과_없이_전파된다() {
        given(quoteQuery.getPublicView(QUOTE_ID)).willThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        assertThatThrownBy(() -> viewTokenCommand.issue(QUOTE_ID, CONTACT_ID))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(quoteViewTokenRepository, customerQuery, mailCommand);
    }

    @Test
    @DisplayName("getContact가 던지면 전파하고, 그 시점까지 토큰 저장소를 부르지 않는다 (getContact를 저장 앞에 두는 순서 고정)")
    void getContact_예외_전_토큰_저장소는_호출되지_않는다() {
        given(quoteQuery.getPublicView(QUOTE_ID)).willReturn(view());
        given(customerQuery.getContact(CONTACT_ID)).willThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        assertThatThrownBy(() -> viewTokenCommand.issue(QUOTE_ID, CONTACT_ID))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(quoteViewTokenRepository);
        verify(mailCommand, never()).schedule(any(), any(), any(), any(), any(), any());
    }

    // ─────────────────────────────── expire() ───────────────────────────────

    @Test
    @DisplayName("ACTIVE 링크가 있으면 EXPIRED로 전이하고 사유를 기록한다")
    void ACTIVE_링크를_EXPIRED로_전이한다() {
        QuoteViewToken token = activeToken();
        given(quoteViewTokenRepository.findActiveByQuoteId(QUOTE_ID)).willReturn(Optional.of(token));

        viewTokenCommand.expire(QUOTE_ID, ViewTokenCommand.ExpiredReason.WITHDRAWN);

        assertThat(token.getStatus()).isEqualTo(QuoteViewToken.Status.EXPIRED);
        assertThat(token.getExpiredReason()).isEqualTo(QuoteViewToken.ExpiredReason.WITHDRAWN);
    }

    @Test
    @DisplayName("계약 사유를 이름이 같은 엔티티 사유로 매핑한다 — 하드코딩이 아니다")
    void 계약_사유를_엔티티_사유로_매핑한다() {
        QuoteViewToken token = activeToken();
        given(quoteViewTokenRepository.findActiveByQuoteId(QUOTE_ID)).willReturn(Optional.of(token));

        viewTokenCommand.expire(QUOTE_ID, ViewTokenCommand.ExpiredReason.DEAL_LOST);

        assertThat(token.getExpiredReason()).isEqualTo(QuoteViewToken.ExpiredReason.DEAL_LOST);
    }

    @Test
    @DisplayName("ACTIVE 링크가 없으면 예외 없이 무동작한다 (멱등)")
    void ACTIVE_링크가_없으면_무동작한다() {
        given(quoteViewTokenRepository.findActiveByQuoteId(QUOTE_ID)).willReturn(Optional.empty());

        assertThatCode(() -> viewTokenCommand.expire(QUOTE_ID, ViewTokenCommand.ExpiredReason.TIME))
                .doesNotThrowAnyException();
    }

    private static String extractRawToken(String body) {
        String prefix = BASE_URL + "/q/";
        return body.lines()
                .filter(line -> line.startsWith(prefix))
                .map(line -> line.substring(prefix.length()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("본문에 링크 줄이 없다: " + body));
    }
}
