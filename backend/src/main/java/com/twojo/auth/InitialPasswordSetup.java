package com.twojo.auth;

import java.time.Instant;
import java.util.UUID;

/**
 * 최초 비밀번호 설정 링크 발급 — 가입 승인이 부르는 진입점 (ON-07 · Q-33·34).
 *
 * <p>{@link SessionRevoker}와 같은 자리에 둔다. 소비자가 onboarding 하나라 전 도메인이 보는
 * boundary에 올릴 이유가 없고, auth/package-info가 "타 모듈은 이 패키지 루트의 공개
 * 인터페이스만 사용한다"고 규정한다.
 *
 * <p><b>원문 토큰이 아니라 완성된 링크를 돌려준다.</b> {@code PasswordService}의 재설정
 * 경로는 원문 토큰이 메서드를 벗어나지 않게 지켜 왔다 — 그 규율을 여기서 깨지 않으려면
 * 링크 조립까지 auth 안에서 끝나야 한다. 베이스 URL도 auth 설정이라 밖으로 나갈 값이 아니다.
 *
 * <p><b>메일은 여기서 보내지 않는다.</b> 승인 통보는 {@code SIGNUP_APPROVED} 템플릿이고
 * 그 {@code refId}는 <b>신청서 id</b>다 (MailCommand 계약) — auth는 신청서를 모른다.
 * 링크는 auth가 만들고 발송은 onboarding이 한다.
 *
 * <p>이름을 onXxx로 두지 않은 이유는 {@link SessionRevoker}와 같다 — 이벤트가 아니라
 * 호출자의 트랜잭션에서 도는 동기 호출이다.
 */
public interface InitialPasswordSetup {

    /**
     * 7일짜리 설정 토큰을 발급하고 링크를 만든다 (Q-34).
     *
     * <p>재설정(AU-05)과 같은 테이블·같은 실행 엔드포인트를 쓴다. 갈리는 것은 수명뿐이고,
     * 그 차이는 발급 시점에 {@code expires_at}으로 이미 반영된다.
     *
     * <p>구성원당 활성 토큰은 하나다 — 이미 있으면 넘기고 새로 낸다. 승인은 한 번뿐이라
     * 정상 경로에서는 걸리지 않지만, 활성 1개 제약을 이 메서드가 스스로 지켜야 한다.
     *
     * @param memberId 방금 만들어진 기업 관리자 — 존재는 호출자가 보장한다
     * @return 메일 본문에 그대로 넣을 링크와 만료 시각
     */
    SetupLink issueLink(UUID memberId, Instant now);

    /** 메일 본문이 필요로 하는 두 값. 만료 시각은 안내 문구에 찍힌다. */
    record SetupLink(String url, Instant expiresAt) {}
}
