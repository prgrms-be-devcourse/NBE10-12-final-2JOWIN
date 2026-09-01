package com.twojo.approval.token;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/**
 * 열람 링크 토큰 — 원문 생성과 조회용 해시 (SC-07~09, ERD {@code quote_view_token.token_hash}).
 *
 * <p>DB에는 해시만 저장한다. 원문은 발급 시점 메모리와 안내 메일에만 존재한다 (docs/14 §2-1).
 * 열람 요청은 URL의 원문을 다시 해시해 {@code token_hash}로 행을 찾는다.
 *
 * <p>A의 {@code com.twojo.auth.token.SecureTokenFactory}와 같은 규약이지만, Spring Modulith가
 * 그 클래스를 auth 모듈 안으로 가려 approval에서 import할 수 없어 같은 스펙으로 approval 전용
 * 생성기를 둔다 (스펙 2026-08-31 팀 합의 — SHA-256 · 256비트 랜덤 · salt/secret 없음).
 * 공용 팩토리로의 통합은 별도 작업(E 소유)이다.
 *
 * <p>{@code public}이지만 approval 모듈 내부용이다 — {@code approval.service}의 {@code issue()}가
 * 이 타입을 직접 주입받아야 하므로 패키지 밖에서 보여야 한다. 모듈 밖(auth·quote 등) 접근은
 * {@code approval.token}이 서브패키지라 Modulith가 그대로 차단한다.
 */
@Component
public class TokenGenerator {

    /** 256비트 — 추측 공격이 성립하지 않는 수준. */
    private static final int TOKEN_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();

    /** 원문 발급 — 링크 URL에 그대로 실을 수 있게 패딩 없는 URL-safe Base64. */
    String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 조회용 해시 — SHA-256 hex.
     *
     * <p>BCrypt를 쓰지 않는 이유는 토큰이 이미 고엔트로피라 솔트·스트레칭이 필요 없고,
     * 솔트가 섞이면 해시가 매번 달라져 {@code token_hash}로 조회하는 것 자체가 불가능해지기 때문이다.
     * 발급 시 저장할 값과 열람 시 조회할 값을 같은 메서드로 만들어 둘이 어긋나지 않게 한다.
     */
    String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256은 모든 JVM이 제공한다", e);
        }
    }
}
