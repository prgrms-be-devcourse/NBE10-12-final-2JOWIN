package com.twojo.global.error;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * 예외 → ErrorResponse 변환의 단일 지점 (docs/11-work-breakdown.md §1.3).
 * <ul>
 *   <li>BusinessException → ErrorCode의 HTTP 상태·문구 그대로</li>
 *   <li>Bean Validation 실패 → 400 VALIDATION_FAILED + fieldErrors</li>
 *   <li>그 밖의 예외 → 500 INTERNAL_ERROR</li>
 * </ul>
 *
 * <p>ResponseEntityExceptionHandler를 상속하는 이유는 세 번째 줄 때문이다. Exception을 잡는
 * 핸들러가 있으면 Spring 표준 웹 예외(깨진 JSON·미지원 메서드)까지 여기로 끌려와 전부 500이 된다.
 * 부모가 그것들을 먼저 처리하므로 아래 폴백은 진짜 예상 밖의 예외만 받는다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
        ErrorCode code = e.getErrorCode();
        return ResponseEntity.status(code.getStatus()).body(ErrorResponse.of(code));
    }

    /**
     * 여기까지 온 예외는 우리가 예상하지 못한 것이다. 사용자에게는 원인을 알리지 않고 로그에만 남긴다 —
     * 예외 종류나 스택이 응답에 실리면 내부 구조가 샌다.
     *
     * <p>이 핸들러가 없으면 예외가 서블릿까지 올라가 /error로 포워딩되고, 그 요청이 Security 필터에
     * 다시 걸려 500이 403으로 바뀐다. 그러면 모니터링이 서버 오류를 5xx로 집계하지 못한다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("처리되지 않은 예외", e);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getStatus())
                .body(ErrorResponse.of(ErrorCode.INTERNAL_ERROR));
    }

    /** 부모가 400으로 내보내는 자리를 우리 ErrorResponse 포맷으로 바꾼다. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        var fieldErrors = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ErrorResponse.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.getStatus())
                .body(ErrorResponse.of(ErrorCode.VALIDATION_FAILED, fieldErrors));
    }
}
