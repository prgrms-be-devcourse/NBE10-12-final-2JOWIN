package com.twojo.approval.token;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

/**
 * {@link TokenGenerator} — 원문의 엔트로피·형식과 해시의 결정성·형식만 검증한다.
 * 의존성이 없어 실객체를 그대로 쓴다.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class TokenGeneratorTest {

    private final TokenGenerator tokenGenerator = new TokenGenerator();

    @Test
    @DisplayName("generate()는 매 호출마다 다른 원문을 반환한다")
    void generate는_매_호출마다_다른_값을_반환한다() {
        Set<String> tokens = new HashSet<>();
        IntStream.range(0, 1_000).forEach(i -> tokens.add(tokenGenerator.generate()));

        assertThat(tokens).hasSize(1_000);
    }

    @Test
    @DisplayName("generate() 결과는 패딩 없는 URL-safe Base64다")
    void generate_결과는_패딩_없는_URL_safe_Base64다() {
        String raw = tokenGenerator.generate();

        assertThat(raw).matches("[A-Za-z0-9_-]+");
        assertThat(raw).doesNotContain("=", "+", "/");
    }

    @Test
    @DisplayName("hash()는 같은 원문에 항상 같은 결과를 반환한다")
    void hash는_같은_입력에_같은_결과를_반환한다() {
        String raw = tokenGenerator.generate();

        assertThat(tokenGenerator.hash(raw)).isEqualTo(tokenGenerator.hash(raw));
    }

    @Test
    @DisplayName("hash() 결과는 64자 소문자 hex다")
    void hash_결과는_64자_소문자_hex다() {
        String hash = tokenGenerator.hash(tokenGenerator.generate());

        assertThat(hash).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("서로 다른 원문은 서로 다른 해시를 만든다")
    void 서로_다른_raw는_서로_다른_hash를_만든다() {
        String hashA = tokenGenerator.hash(tokenGenerator.generate());
        String hashB = tokenGenerator.hash(tokenGenerator.generate());

        assertThat(hashA).isNotEqualTo(hashB);
    }
}
