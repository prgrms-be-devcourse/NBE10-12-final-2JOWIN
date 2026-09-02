package com.twojo.global.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 에러 코드·HTTP 상태·사용자 안내 문구의 단일 원본.
 * <p>정본: docs/07-api-spec.md 부록 "에러별 사용자 안내 문구" — 문구를 여기서 새로 쓰지 않는다.
 * <p>404 계열은 SC-09에 따라 전부 동일 문구다 — 존재·권한을 구별해서 말하지 않는다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // ── 공통
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "입력값을 확인해 주세요."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 대상을 찾을 수 없습니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "이 작업을 수행할 권한이 없습니다."),                       // Q-43 — 역할 위반 전용

    // ── A 인증
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    LOGIN_LOCKED(HttpStatus.TOO_MANY_REQUESTS, "로그인 시도가 너무 많습니다. 10분 후 다시 시도해 주세요."),
    REFRESH_TOKEN_NOT_ACTIVE(HttpStatus.UNAUTHORIZED, "세션이 만료되었습니다. 다시 로그인해 주세요."),  // AU-12
    RESET_TOKEN_NOT_ACTIVE(HttpStatus.CONFLICT, "이 재설정 링크는 더 이상 유효하지 않습니다. 재설정을 다시 요청해 주세요."),
    CURRENT_PASSWORD_MISMATCH(HttpStatus.UNPROCESSABLE_ENTITY, "현재 비밀번호가 올바르지 않습니다."),  // AU-04, 07 v1.6.5

    // ── A 온보딩
    EMAIL_ALREADY_MEMBER(HttpStatus.UNPROCESSABLE_ENTITY, "이미 사용 중인 이메일입니다."),
    APPLICATION_ALREADY_PENDING(HttpStatus.CONFLICT, "이미 검토 중인 신청이 있습니다."),
    APPLICATION_ALREADY_DECIDED(HttpStatus.CONFLICT, "이미 처리된 신청입니다."),
    COMPANY_BUSINESS_NO_DUPLICATED(HttpStatus.CONFLICT, "이미 가입된 회사입니다."),

    // ── A 구성원·초대
    INVITATION_NOT_PENDING(HttpStatus.CONFLICT, "이 초대는 더 이상 유효하지 않습니다. 관리자에게 재발송을 요청해 주세요."),
    LAST_ADMIN_PROTECTED(HttpStatus.UNPROCESSABLE_ENTITY, "회사에는 최소 한 명의 관리자가 필요합니다."),
    MEMBER_INACTIVE_TRANSFER_REQUIRED(HttpStatus.UNPROCESSABLE_ENTITY, "담당 중인 Deal이 있습니다. 이관받을 구성원을 지정해 주세요."),

    // ── B 고객사
    CUSTOMER_HAS_ACTIVE_DEALS(HttpStatus.CONFLICT, "진행 중인 Deal이 있어 삭제할 수 없습니다."),
    PRIMARY_CONTACT_REQUIRED(HttpStatus.UNPROCESSABLE_ENTITY, "대표 담당자는 삭제할 수 없습니다. 먼저 다른 담당자를 대표로 지정해 주세요."),
    CONTACT_HAS_QUOTES(HttpStatus.CONFLICT, "견적 발송 이력이 있어 삭제할 수 없습니다."),

    // ── B 상품
    PRODUCT_NAME_DUPLICATED(HttpStatus.CONFLICT, "이미 등록된 상품명입니다. 판매 중지된 상품이라면 판매 재개를 이용하세요."),
    PRODUCT_DISCONTINUED(HttpStatus.CONFLICT, "판매 중지된 상품은 견적에 추가할 수 없습니다."),

    // ── B 활동 이력
    ACTIVITY_NOT_AUTHOR(HttpStatus.NOT_FOUND, "요청한 대상을 찾을 수 없습니다."),               // SC-09 — 404 문구 통일

    // ── C Deal
    DEAL_WON_REQUIRES_ORDER(HttpStatus.CONFLICT, "성사는 승인된 견적을 주문으로 전환할 때 자동으로 처리됩니다."),
    DEAL_ALREADY_WON(HttpStatus.CONFLICT, "성사된 Deal은 단계를 변경할 수 없습니다."),
    DEAL_HAS_QUOTES(HttpStatus.CONFLICT, "견적이 연결된 Deal은 삭제할 수 없습니다."),

    // ── C 견적
    QUOTE_NOT_DRAFT(HttpStatus.CONFLICT, "작성 중인 견적만 수정·발송할 수 있습니다."),
    QUOTE_EMPTY_ITEMS(HttpStatus.CONFLICT, "견적 항목을 1개 이상 추가해 주세요."),
    QUOTE_NOT_WITHDRAWABLE(HttpStatus.CONFLICT, "이 상태의 견적은 회수할 수 없습니다."),
    QUOTE_DEAL_CLOSED(HttpStatus.CONFLICT, "종결된 Deal에는 견적을 작성할 수 없습니다. 새 Deal을 만들어 진행해 주세요."),
    CONTACT_NOT_IN_CUSTOMER(HttpStatus.CONFLICT, "이 Deal의 고객사에 소속된 담당자만 수신인으로 지정할 수 있습니다."),
    STALE_VERSION(HttpStatus.CONFLICT, "다른 사용자가 먼저 수정했습니다. 새로고침 후 다시 시도해 주세요."),

    // ── C 주문
    QUOTE_NOT_APPROVED(HttpStatus.CONFLICT, "승인된 견적만 주문으로 전환할 수 있습니다."),
    QUOTE_ALREADY_CONVERTED(HttpStatus.CONFLICT, "이미 주문으로 전환된 견적입니다."),

    // ── D 고객 열람·승인
    LINK_EXPIRED(HttpStatus.GONE, "만료된 링크입니다. 담당자에게 재발송을 요청해 주세요."),      // AP-05
    LINK_ALREADY_RESPONDED(HttpStatus.CONFLICT, "이미 응답이 완료된 견적입니다."),               // AP-11
    COMPANY_SUSPENDED(HttpStatus.CONFLICT, "현재 이 견적에는 응답할 수 없습니다. 담당자에게 문의해 주세요.");  // SC-10, Q-27

    private final HttpStatus status;
    private final String message;
}
