/**
 * 에러 코드 → 사용자 문구 (docs/07-api-spec.md 부록 = ErrorCode enum message 원본).
 *
 * 이 파일은 손으로 쓰지 않는다. backend/src/main/java/com/twojo/global/error/ErrorCode.java에서
 * 그대로 옮긴 것이고, 프론트에서 문구를 새로 쓰지 않는다 (12-frontend-plan.md §6.3-3).
 * 백엔드 enum이 바뀌면 이 파일도 함께 고친다 — 어긋나면 화면에 다른 말이 뜬다.
 *
 * 404 계열은 SC-09에 따라 전부 동일 문구다 — 존재·권한을 구별해서 말하지 않는다.
 */
export const ERROR_MESSAGES = {
  VALIDATION_FAILED: '입력값을 확인해 주세요.',
  RESOURCE_NOT_FOUND: '요청한 대상을 찾을 수 없습니다.',
  FORBIDDEN: '이 작업을 수행할 권한이 없습니다.',
  LOGIN_FAILED: '이메일 또는 비밀번호가 올바르지 않습니다.',
  LOGIN_LOCKED: '로그인 시도가 너무 많습니다. 10분 후 다시 시도해 주세요.',
  REFRESH_TOKEN_NOT_ACTIVE: '세션이 만료되었습니다. 다시 로그인해 주세요.',
  RESET_TOKEN_NOT_ACTIVE: '이 재설정 링크는 더 이상 유효하지 않습니다. 재설정을 다시 요청해 주세요.',
  EMAIL_ALREADY_MEMBER: '이미 사용 중인 이메일입니다.',
  APPLICATION_ALREADY_PENDING: '이미 검토 중인 신청이 있습니다.',
  APPLICATION_ALREADY_DECIDED: '이미 처리된 신청입니다.',
  COMPANY_BUSINESS_NO_DUPLICATED: '이미 가입된 회사입니다.',
  INVITATION_NOT_PENDING: '이 초대는 더 이상 유효하지 않습니다. 관리자에게 재발송을 요청해 주세요.',
  LAST_ADMIN_PROTECTED: '회사에는 최소 한 명의 관리자가 필요합니다.',
  MEMBER_INACTIVE_TRANSFER_REQUIRED: '담당 중인 Deal이 있습니다. 이관받을 구성원을 지정해 주세요.',
  CUSTOMER_HAS_ACTIVE_DEALS: '진행 중인 Deal이 있어 삭제할 수 없습니다.',
  PRIMARY_CONTACT_REQUIRED: '대표 담당자는 삭제할 수 없습니다. 먼저 다른 담당자를 대표로 지정해 주세요.',
  CONTACT_HAS_QUOTES: '견적 발송 이력이 있어 삭제할 수 없습니다.',
  PRODUCT_NAME_DUPLICATED: '이미 등록된 상품명입니다. 판매 중지된 상품이라면 판매 재개를 이용하세요.',
  PRODUCT_DISCONTINUED: '판매 중지된 상품은 견적에 추가할 수 없습니다.',
  ACTIVITY_NOT_AUTHOR: '요청한 대상을 찾을 수 없습니다.',
  DEAL_WON_REQUIRES_ORDER: '성사는 승인된 견적을 주문으로 전환할 때 자동으로 처리됩니다.',
  DEAL_ALREADY_WON: '성사된 Deal은 단계를 변경할 수 없습니다.',
  DEAL_HAS_QUOTES: '견적이 연결된 Deal은 삭제할 수 없습니다.',
  QUOTE_NOT_DRAFT: '작성 중인 견적만 수정·발송할 수 있습니다.',
  QUOTE_EMPTY_ITEMS: '견적 항목을 1개 이상 추가해 주세요.',
  QUOTE_NOT_WITHDRAWABLE: '이 상태의 견적은 회수할 수 없습니다.',
  QUOTE_DEAL_CLOSED: '종결된 Deal에는 견적을 작성할 수 없습니다. 새 Deal을 만들어 진행해 주세요.',
  CONTACT_NOT_IN_CUSTOMER: '이 Deal의 고객사에 소속된 담당자만 수신인으로 지정할 수 있습니다.',
  STALE_VERSION: '다른 사용자가 먼저 수정했습니다. 새로고침 후 다시 시도해 주세요.',
  QUOTE_NOT_APPROVED: '승인된 견적만 주문으로 전환할 수 있습니다.',
  QUOTE_ALREADY_CONVERTED: '이미 주문으로 전환된 견적입니다.',
  LINK_EXPIRED: '만료된 링크입니다. 담당자에게 재발송을 요청해 주세요.',
  LINK_ALREADY_RESPONDED: '이미 응답이 완료된 견적입니다.',
  COMPANY_SUSPENDED: '현재 이 견적에는 응답할 수 없습니다. 담당자에게 문의해 주세요.',
  INTERNAL_ERROR: '일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.',
} as const

export type ErrorCode = keyof typeof ERROR_MESSAGES

/** 알 수 없는 코드·네트워크 오류의 최후 문구 — 화면이 빈 채로 남지 않게 한다 */
export const FALLBACK_MESSAGE = ERROR_MESSAGES.INTERNAL_ERROR

export function messageOf(code: string | undefined): string {
  return (code && ERROR_MESSAGES[code as ErrorCode]) || FALLBACK_MESSAGE
}
