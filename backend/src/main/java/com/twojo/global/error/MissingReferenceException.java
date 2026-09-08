package com.twojo.global.error;

import java.util.UUID;

/**
 * FK가 존재를 보장하는 행을 찾지 못했다 — 데이터 무결성 이상.
 *
 * <p>{@link ErrorCode#RESOURCE_NOT_FOUND}와 구별하려고 만든 타입이다. 그쪽은 "범위 밖인지
 * 없는지 구별하지 않는다"는 SC-09의 404이고, 이쪽은 있어야 할 것이 없는 상태라 500이다.
 * 한 코드가 둘을 겸하면 경계 메서드의 404가 그것을 부르는 다른 도메인의 응답으로 새어,
 * 멀쩡한 리소스를 "없거나 권한 없음"으로 보이게 만든다.
 *
 * <p>{@code GlobalExceptionHandler}의 폴백이 500 {@code INTERNAL_ERROR}로 바꾸며 스택을 남긴다 —
 * 사용자에게 원인을 알리지 않고 로그로만 추적한다.
 *
 * <p>{@code IllegalStateException}을 상속하되 타입을 따로 두는 이유는 인증 필터 때문이다.
 * 필터는 이 예외만 골라 잡아 401을 유지해야 하는데(SC-09), 바닥 타입을 잡으면 그 try 블록의
 * 다른 프로그래밍 오류까지 미인증으로 삼켜진다.
 */
public class MissingReferenceException extends IllegalStateException {

    /**
     * @param entity 없는 행이 속한 테이블 — 로그에서 어느 FK가 깨졌는지 가리키는 값이다
     * @param id     조회에 쓴 키. 비밀이 아니라 로그에 남겨도 된다
     */
    public MissingReferenceException(String entity, UUID id) {
        super("%s 행이 없다 — FK가 보장하는 자리다: %s".formatted(entity, id));
    }
}
