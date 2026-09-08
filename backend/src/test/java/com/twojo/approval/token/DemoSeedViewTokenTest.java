package com.twojo.approval.token;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 데모 열람 링크({@code R__demo_seed.sql})의 원문 토큰·{@code token_hash}·만료일이 어긋나지 않게 고정한다 (#199).
 *
 * <p>원문 토큰은 {@code frontend/src/mocks/fixtures.ts}의 {@code viewTokens[].rawToken}과 같은 값이다.
 * 해시는 하드코딩하지 않고 {@link TokenGenerator}로 매번 계산한다 — 알고리즘이 바뀌면 시드를 다시
 * 생성하라는 신호가 된다.
 *
 * <p>단순히 "해시 hex가 파일 어딘가 존재"만 보지 않는다 (E 리뷰, #200). 행마다
 * {@code token_hash}·{@code expires_at}가 <b>같은 줄에서 쌍</b>으로 맞는지, INSERT와 UPDATE가
 * <b>서로 일치</b>하는지, 견적의 {@code valid_until}이 토큰 만료일과 <b>같은 날짜</b>인지(Q-17)까지 확인한다 —
 * 두 행의 해시가 뒤바뀌거나 한쪽 날짜만 변경되는 경우를 잡는다.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class DemoSeedViewTokenTest {

    private static final String SEED_RESOURCE = "db/seed/R__demo_seed.sql";

    /** 토큰 만료 시각 (KST). 날짜 부분이 견적 {@code valid_until}과 같아야 한다 (Q-17). */
    private static final String EXPIRES_AT = "2026-09-30 23:59:59+09";
    private static final String VALID_UNTIL = "2026-09-30";

    private final TokenGenerator tokenGenerator = new TokenGenerator();

    /** 데모 링크 한 행 — 토큰·견적 id, 견적번호, 원문 토큰 */
    record DemoRow(String label, String tokenId, String quoteId, String quoteNo, String rawToken) {}

    static Stream<DemoRow> demoRows() {
        return Stream.of(
                new DemoRow("Q-2608-011 (SENT)", "7a000000-0000-4000-8000-000000000011",
                        "6a000000-0000-4000-8000-000000000011", "Q-2608-011", "demo-sungwon-11"),
                new DemoRow("Q-2608-014 (VIEWED)", "7a000000-0000-4000-8000-000000000014",
                        "6a000000-0000-4000-8000-000000000014", "Q-2608-014", "demo-dodam-14"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("demoRows")
    @DisplayName("데모 링크 행의 원문-해시-만료일이 INSERT·UPDATE·견적에서 함께 정합한다")
    void 데모_링크_행이_정합한다(DemoRow row) throws IOException {
        String seed = readSeed();
        String hash = tokenGenerator.hash(row.rawToken());

        assertThat(seed)
                .as("%s: quote_view_token INSERT 한 줄에 token_hash+expires_at 쌍이 없다 (해시 스왑 or 날짜 어긋남)", row.label())
                .containsPattern("\\('" + Pattern.quote(row.tokenId()) + "'[^\\n]*'"
                        + Pattern.quote(hash) + "'[^\\n]*'" + Pattern.quote(EXPIRES_AT) + "'");

        assertThat(seed)
                .as("%s: UPDATE quote_view_token의 token_hash+expires_at이 INSERT와 어긋난다", row.label())
                .containsPattern("UPDATE quote_view_token SET token_hash = '" + Pattern.quote(hash)
                        + "', expires_at = '" + Pattern.quote(EXPIRES_AT) + "'\\s+WHERE id = '"
                        + Pattern.quote(row.tokenId()) + "';");

        assertThat(seed)
                .as("%s: quote INSERT 한 줄의 valid_until이 토큰 만료일과 다르다", row.label())
                .containsPattern("\\('" + Pattern.quote(row.quoteId()) + "'[^\\n]*'"
                        + Pattern.quote(row.quoteNo()) + "'[^\\n]*'" + Pattern.quote(VALID_UNTIL) + "'");

        assertThat(seed)
                .as("%s: UPDATE quote 문에 이 견적의 valid_until 갱신이 없다", row.label())
                .containsPattern("UPDATE quote SET valid_until = '" + Pattern.quote(VALID_UNTIL)
                        + "'\\s+WHERE id IN \\([^)]*'" + Pattern.quote(row.quoteId()) + "'[^)]*\\);");
    }

    @Test
    @DisplayName("토큰 만료 시각의 날짜 부분이 견적 유효기간과 같다 (Q-17)")
    void 만료_시각과_유효기간_날짜가_같다() {
        assertThat(EXPIRES_AT).startsWith(VALID_UNTIL + " ");
    }

    @Test
    @DisplayName("데모 링크 두 행에는 자리표시자 token_hash가 남아 있지 않다")
    void 데모_링크_행에는_자리표시자가_없다() throws IOException {
        String seed = readSeed();

        assertThat(seed).doesNotContain("seed-vtoken-hash-0011", "seed-vtoken-hash-0014");
    }

    private static String readSeed() throws IOException {
        InputStream in = DemoSeedViewTokenTest.class.getClassLoader().getResourceAsStream(SEED_RESOURCE);
        if (in == null) {
            throw new IllegalStateException("시드 리소스를 찾을 수 없다: " + SEED_RESOURCE);
        }
        try (in) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
