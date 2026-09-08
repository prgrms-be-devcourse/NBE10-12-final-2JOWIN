package com.twojo.approval.token;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

/**
 * 데모 열람 링크({@code R__demo_seed.sql})의 원문 토큰과 시드 {@code token_hash}가 어긋나지 않게 고정한다 (#199).
 *
 * <p>원문 토큰은 {@code frontend/src/mocks/fixtures.ts}의 {@code viewTokens[].rawToken}과 같은 값이다.
 * 원문 상수·시드 {@code token_hash}·{@link TokenGenerator} 해시 알고리즘 중 하나만 바뀌면
 * 아래 단언이 실패해 CI가 잡는다. 해시를 여기 하드코딩하지 않고 매번 계산한다 —
 * 알고리즘이 바뀌면 시드를 다시 생성하라는 신호가 된다.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class DemoSeedViewTokenTest {

    private static final String SEED_RESOURCE = "db/seed/R__demo_seed.sql";

    /** Q-2608-011 (SENT) — E2E 게이트 관통 링크 */
    private static final String DEMO_RAW_Q011 = "demo-sungwon-11";
    /** Q-2608-014 (VIEWED) — S-01 메인 시나리오 링크 */
    private static final String DEMO_RAW_Q014 = "demo-dodam-14";

    private final TokenGenerator tokenGenerator = new TokenGenerator();

    @Test
    @DisplayName("데모 원문 토큰의 SHA-256 해시가 시드 token_hash와 일치한다")
    void 데모_원문_토큰의_해시가_시드에_들어_있다() throws IOException {
        String seed = readSeed();

        assertThat(seed)
                .as("Q-2608-011 데모 링크 해시가 시드에 없다 - 원문(%s) 또는 token_hash 한쪽이 어긋났다", DEMO_RAW_Q011)
                .contains(tokenGenerator.hash(DEMO_RAW_Q011));
        assertThat(seed)
                .as("Q-2608-014 데모 링크 해시가 시드에 없다 - 원문(%s) 또는 token_hash 한쪽이 어긋났다", DEMO_RAW_Q014)
                .contains(tokenGenerator.hash(DEMO_RAW_Q014));
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
