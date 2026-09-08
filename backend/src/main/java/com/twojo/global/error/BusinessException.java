package com.twojo.global.error;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Getter;

/**
 * 도메인 규칙 위반의 공통 예외 — ErrorCode 하나가 곧 응답이 된다.
 * <p>상태 전이표(docs/05-state-transitions.md)의 "막히는 것" = 이 예외 하나로 던진다.
 *
 * <p>입력 검증 실패(400 {@code VALIDATION_FAILED})만 예외적으로 필드 정보를 함께 나른다 —
 * 07 부록이 그 코드에 "fieldErrors 참조"라고 적어 두기 때문이다. {@link #invalidField} 참조.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    /** 비어 있으면 응답의 fieldErrors도 빈 배열이다 — 대부분의 규칙 위반이 이쪽이다. */
    private final List<ErrorResponse.FieldError> fieldErrors;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, List.of());
    }

    public BusinessException(ErrorCode errorCode, List<ErrorResponse.FieldError> fieldErrors) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.fieldErrors = List.copyOf(fieldErrors);
    }

    /**
     * 필드 하나가 틀렸을 때 — 400 {@code VALIDATION_FAILED} + 그 필드.
     *
     * <p>서비스가 직접 파싱하는 자리(role · type)는 Bean Validation을 거치지 않는다.
     * 이걸 쓰지 않으면 같은 400인데 "무엇이 틀렸는지"만 응답에서 사라져,
     * 프론트가 어느 입력을 고쳐야 하는지 알 수 없다.
     *
     * <p><b>reason에 사용자가 보낸 값을 그대로 싣지 않는다.</b> 응답에 되돌려주면
     * 반사형 XSS 표면이 되고, 어차피 보낸 쪽은 자기가 무엇을 보냈는지 안다.
     * 대신 <b>허용되는 값</b>을 적는다 — 그쪽은 08 §A가 이미 공개한 계약값이다.
     */
    public static BusinessException invalidField(String field, String reason) {
        return new BusinessException(ErrorCode.VALIDATION_FAILED,
                List.of(new ErrorResponse.FieldError(field, reason)));
    }

    /**
     * 값이 enum 상수가 아닐 때 — 허용 목록을 사유로 만든다.
     *
     * <p>문구를 손으로 적으면 상수가 늘어날 때 같이 늘지 않는다. 여기서 만들면
     * 역할이 하나 추가되는 순간 모든 응답 문구가 따라 바뀐다.
     */
    public static <E extends Enum<E>> BusinessException invalidEnumField(
            String field, Class<E> type) {
        String allowed = Arrays.stream(type.getEnumConstants())
                .map(Enum::name)
                .collect(Collectors.joining(" · "));

        return invalidField(field, allowed + " 중 하나여야 합니다");
    }
}
