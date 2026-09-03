package com.twojo.boundary;

import java.util.UUID;

/**
 * 시스템 메일 예약 계약 — 구현: D(notification 모듈).
 * approval(열람 링크 발급)·auth(비밀번호 재설정)·onboarding(가입 승인 통보)이 호출한다.
 *
 * <p><b>왜 계약 하나로 모으나</b> — {@code email_log}는 notification 모듈 소유인데, 메일을 예약하는 흐름은
 * approval({@code ViewTokenCommand.issue})·auth(AU-05 재설정 요청)에 있다. 모듈이 서로의 내부
 * (entity·repository)를 직접 참조하지 않도록 예약 통로를 여기 하나로 모은다 — 현재 boundary 계약이 전부
 * 이 방식이고(docs/11-work-breakdown.md §7.2), 직접 참조로 가면 유일한 예외가 된다. auth→member 쓰기를
 * 모으는 {@code MemberCommand}와 동형이다.
 *
 * <p><b>호출 규약</b> (시그니처에 안 드러나지만 구현이 지킨다):
 * <ul>
 *   <li><b>호출자 트랜잭션에 합류</b>한다 — {@code REQUIRES_NEW} 금지. 호출자(C의 발송·A의 재설정)가
 *       롤백하면 예약 행도 함께 사라져야 "본문(링크)에 대응하는 실체 없는 메일" 같은 고아 레코드가 안 생긴다(Q-40).</li>
 *   <li><b>동기</b>다 — 이 호출이 끝나면 {@code email_log} SCHEDULED 행이 커밋 대기 상태로 존재한다.
 *       실제 발송만 커밋 후 비동기(AFTER_COMMIT)다.</li>
 *   <li>{@code body}는 <b>렌더 완료본</b>이다 — 열람 링크(원문 토큰 포함)·유효기간까지 호출자가 이미 박아
 *       넣은 문자열을 그대로 받는다. 원문 토큰은 저장·로그에 남기지 않으므로(docs/14-tech-stack.md §2-1·§7.3)
 *       계약은 {@code body}를 {@code email_log}에 쓰지 않는다 — 발송 시점에만 쓰고 버린다.</li>
 * </ul>
 *
 * <p><b>호출 1회 = {@code email_log} 행 1건.</b> {@code refId}가 발송할 때마다 새로 발급되는
 * 토큰 행의 id라(QUOTE_SENT는 발송된 {@code quote_view_token}, PASSWORD_RESET는
 * {@code password_reset_token} — 아래 {@code schedule}), 멱등 키
 * {@code (type, refId, recipientEmail)}는 정상 흐름에서 겹치지 않는다. 재발송은 이전 토큰이
 * 만료되고 새 토큰·새 링크가 나가는 흐름이므로, 예약도 기존 행을 덮지 않고 새 행을 만든다.
 *
 * <p>{@code UNIQUE(template_type, ref_id, recipient_email)}는 그 위의 백스톱이다 — 같은 대기 행이
 * AFTER_COMMIT 이벤트와 SCHEDULED 재처리 배치 양쪽에서 INSERT되는 경합만 DB에서 막는다. 이중
 * <b>발송</b> 방어는 디스패처가 행의 {@code status}로 한다({@code SCHEDULED}가 아니면 스킵).
 *
 * <p>{@code email_log} 상태 전이는 앞으로만 간다 — SENT는 뒤집지 않는다(엔티티 {@code markSent}·
 * {@code markFailed}). "같은 수신인에게 재발송하면 SENT 행을 되돌려야 하나"라는 물음은 {@code refId}를
 * 발송 단위로 유일하게 두면서 사라졌다(재발송 = 늘 새 행). {@code email_log} 전이 절은 메일 파이프라인
 * 이슈에서 docs/05에 신설한다.
 *
 * <p><b>{@code companyId} null 규칙</b> — NT-13(가입 승인·반려 통보) 계열만 null이다({@code email_log}
 * DDL 주석: "플랫폼 발송(NT-13)은 null"). 그 외(견적 발송·재설정)는 값 필수 — 재설정 메일도 이미 가입된
 * 구성원에게만 나가므로 소속 회사가 있다.
 *
 * <p>{@code schedule} 시그니처와 {@code TemplateType} 상수의 <b>이름 변경·삭제</b>는 소유자(E) + 소비자(D·A)
 * 합의가 필요하다. {@code TemplateType} <b>값 추가는 D 단독</b>으로 한다 — D가 notification 소유이고,
 * 추가는 기존 소비자에 영향이 없다.
 */
public interface MailCommand {

    /**
     * 메일 예약 — {@code email_log}에 SCHEDULED 행을 새로 만들고 발송 이벤트를 같은 트랜잭션에서 발행한다.
     *
     * <p>{@code ref_type}은 파라미터가 아니다 — {@link TemplateType#refType()}가 정한다. 호출자가
     * {@code type}과 따로 넘기면 불일치 쌍({@code QUOTE_SENT} + {@code "PASSWORD_RESET_TOKEN"})을 만들
     * 여지가 생겨서 뺐다.
     *
     * <p>{@code refId}는 이 발송이 배달하는 <b>토큰 행</b>의 id다 — {@code QUOTE_SENT}면 방금 발급된
     * {@code quote_view_token} id, {@code PASSWORD_RESET}면 {@code password_reset_token} id.
     * 발송마다 새로 발급되므로 {@code email_log} 행도 발송마다 하나씩 생긴다.
     * 멱등 키 {@code (type, refId, recipientEmail)}의 일부다.
     *
     * <p>{@code recipientEmail}은 정규화된 값(trim·소문자)이어야 한다 — 멱등 키의 일부이자
     * {@code email_log.recipient_email}에 그대로 저장돼 NT-12 수신자 판정·집계에 쓰인다. 정규화는 호출자 책임이다.
     *
     * <p>{@code subject}·{@code body}는 렌더 완료본이다. {@code body}(링크·원문 토큰 포함)는
     * {@code email_log}에 저장하지 않는다(docs/14-tech-stack.md §2-1·§7.3).
     */
    void schedule(TemplateType type, UUID companyId, String recipientEmail,
                  UUID refId, String subject, String body);

    /**
     * 메일 종류 — {@code email_log.template_type} 값이자, 대기 행 이중 INSERT를 막는 백스톱 제약
     * {@code UNIQUE(template_type, ref_id, recipient_email)}의 일부.
     *
     * <p><b>이 enum을 계약에 두고 엔티티({@code EmailLog})가 직접 참조한다</b> —
     * {@code ViewTokenCommand.ExpiredReason}이 계약 enum ↔ 엔티티 enum 2개로 갈라져 브리지되는 것과 다르다.
     * 이유: {@code expired_reason}은 DB CHECK와 1:1이라 엔티티를 boundary 무의존으로 두려고 갈랐지만,
     * {@code template_type}은 {@code VARCHAR(30)} CHECK 없음 — 값을 늘려도 마이그레이션이 없어 계약 enum
     * 하나를 공유해도 엔티티가 계약에 묶이는 비용이 그만큼 작다. approval·auth도 호출자로 같은 값 집합을 써서
     * 한 곳에 두는 편이 낫다.
     *
     * <p>{@link #refType()}는 {@code email_log.ref_type}에 그대로 들어가는 문자열이다 — {@code refId}가
     * 가리키는 대상의 거친 분류이지 정확한 테이블명은 아니다({@code QUOTE_SENT}의 {@code "QUOTE"} ↔
     * {@code refId}는 {@code quote_view_token} id).
     */
    enum TemplateType {

        /** NT-02 견적 발송 안내 — 고객사 담당자 수신 (approval, {@code ViewTokenCommand.issue}) */
        QUOTE_SENT("QUOTE"),

        /** NT-13 가입 승인 통보 — 신청자 수신 (onboarding, ON-07). Q-33: 승인 메일에 비밀번호 설정 링크 */
        SIGNUP_APPROVED("APPLICATION"),

        /** NT-14 비밀번호 재설정 안내 — 기존 구성원 수신 (auth, AU-05) */
        PASSWORD_RESET("PASSWORD_RESET_TOKEN");

        private final String refType;

        TemplateType(String refType) {
            this.refType = refType;
        }

        /** {@code email_log.ref_type}에 저장되는 값. */
        public String refType() {
            return refType;
        }
    }
}
