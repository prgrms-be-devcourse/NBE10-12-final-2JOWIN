package com.twojo.auth.token;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/**
 * 불투명 토큰 생성과 해시 — refresh · 재설정 · 초대 토큰이 같은 규약을 쓴다 (ERD token_hash).
 *
 * <p>DB에는 해시만 저장한다. 원문은 발급 시점 메모리와 쿠키·메일에만 존재한다 (14 §2-1).
 */
@Component
public class SecureTokenFactory {

    /** 256비트 — 추측 공격이 성립하지 않는 수준. */
    private static final int TOKEN_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();

    /** 원문 발급 — URL·쿠키에 그대로 실을 수 있게 URL-safe Base64. */
    public String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 조회용 해시 — SHA-256.
     * BCrypt를 쓰지 않는 이유는 토큰이 이미 고엔트로피라 솔트·스트레칭이 필요 없고,
     * 솔트가 섞이면 해시가 매번 달라져 token_hash로 조회하는 것 자체가 불가능해지기 때문이다.
     */
    public String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256은 모든 JVM이 제공한다", e);
        }
    }
}
