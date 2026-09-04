package com.twojo.auth.jwt;

import com.twojo.auth.entity.ActorType;
import com.twojo.boundary.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * access token 발급·검증 — HS256 단일 키 (14 §3-4).
 *
 * <p>수명 15분은 Q-32 확정값이라 상수로 둔다 — 설정으로 빼면 문서에 없는 유연성이 생긴다.
 */
@Component
public final class JwtProvider {

    /** Q-32 — 차단이 실제로 걸리기까지의 최대 노출 창. */
    public static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(15);

    private static final String CLAIM_ACTOR = "actor";
    private static final String CLAIM_COMPANY_ID = "companyId";
    private static final String CLAIM_ROLE = "role";

    private final SecretKey key;

    public JwtProvider(@Value("${jwt.secret}") String secret) {
        // HS256은 256비트(32바이트) 이상을 요구한다 — 짧으면 여기서 기동이 실패한다
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 구성원 access token 발급. 필터가 AccessContext를 만들 때 필요한 값만 담는다 (11 §1.4).
     * payload는 Base64라 누구나 열어볼 수 있으므로 비밀은 넣지 않는다.
     */
    public String issue(UUID memberId, UUID companyId, Role role, Instant now) {
        return builder(memberId, ActorType.MEMBER, now)
                .claim(CLAIM_COMPANY_ID, companyId.toString())
                .claim(CLAIM_ROLE, role.name())
                .compact();
    }

    /**
     * 관리자 access token 발급 (AU-08).
     *
     * <p>회사도 역할도 담지 않는다 — 관리자는 회사에 속하지 않고 역할이 하나뿐이라
     * 담을 값 자체가 없다. 안 쓰는 값을 넣으면 payload를 열어보는 쪽에 정보만 준다.
     */
    public String issueForPlatformAdmin(UUID platformAdminId, Instant now) {
        return builder(platformAdminId, ActorType.PLATFORM_ADMIN, now).compact();
    }

    /**
     * 두 발급이 공유하는 부분.
     *
     * <p>actor를 여기에 두면 발급 경로가 늘어도 빠뜨릴 수 없다.
     * 빠진 토큰은 어느 경로에서도 통과하지 못한다.
     */
    private JwtBuilder builder(UUID subject, ActorType actor, Instant now) {
        return Jwts.builder()
                .subject(subject.toString())
                .claim(CLAIM_ACTOR, actor.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ACCESS_TOKEN_TTL)))
                .signWith(key);
    }

    /**
     * 서명과 만료를 검증하고 claim을 돌려준다.
     * 실패는 JwtException으로 전파한다 — 401 변환은 인증 필터가 맡는다 (09 구현 위치).
     */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // claim 이름이 이 클래스 밖으로 새지 않도록 읽기도 여기서 제공한다

    /** 토큰이 가리키는 주체 — 구성원 토큰이면 구성원 id, 관리자 토큰이면 관리자 id다. */
    public static UUID subjectOf(Claims claims) {
        return UUID.fromString(claims.getSubject());
    }

    /**
     * 이 토큰이 그 종류의 것인가 — 구성원 경로와 관리자 경로가 서로의 토큰을 거부하는 근거다.
     * claim이 아예 없는 토큰은 어느 쪽에도 해당하지 않아 false가 된다.
     */
    public static boolean isActor(Claims claims, ActorType expected) {
        return expected.name().equals(claims.get(CLAIM_ACTOR, String.class));
    }

    public static UUID companyIdOf(Claims claims) {
        return UUID.fromString(claims.get(CLAIM_COMPANY_ID, String.class));
    }

    public static Role roleOf(Claims claims) {
        return Role.valueOf(claims.get(CLAIM_ROLE, String.class));
    }
}
