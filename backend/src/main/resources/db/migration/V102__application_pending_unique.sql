-- 같은 이메일의 검토 대기 신청은 하나뿐이다 (05 §1 "막히는 것" · APPLICATION_ALREADY_PENDING).
--
-- 접수 경로의 사전 검사만으로는 동시 요청 둘이 함께 통과한다. 인증 없이 누구나 부르는
-- 엔드포인트라 그 창이 실제로 열려 있다. 초대가 uk_invitation_pending 으로 막아 둔 것과
-- 같은 상황인데 신청 쪽만 비어 있었다 (PR #108 1차 리뷰).
--
-- 부분 인덱스라 종결된 행은 제약을 받지 않는다 — 반려 후 재신청이 Q-15 의 결론이고,
-- 승인된 행이 그 이메일을 영구히 막아서도 안 된다.
--
-- lower(email) 로 거는 것은 조회(findByEmailLowerAndStatus)와 같은 식이어야 하기 때문이다.
-- 한쪽만 대소문자를 무시하면 사전 검사가 막은 것을 인덱스가 통과시킨다.
CREATE UNIQUE INDEX uk_application_pending
    ON application (lower(email)) WHERE status = 'PENDING';
