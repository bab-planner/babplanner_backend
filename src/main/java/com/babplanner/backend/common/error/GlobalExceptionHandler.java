package com.babplanner.backend.common.error;

import com.babplanner.backend.common.api.ApiError;
import com.babplanner.backend.common.api.ApiResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 모든 오류 응답을 OpenAPI ErrorEnvelope 하나로 통일한다.
 *
 * <p>스택 트레이스, JPA/SQL 예외 메시지, 검증 대상 값은 응답에 넣지 않는다.
 * 검증 오류는 어떤 필드가 문제인지까지만 알려준다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApiException(ApiException exception) {
        return respond(exception.errorCode(), exception.details());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> fields = new TreeMap<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            // 필드 이름과 제약 위반 메시지만 담는다. 입력 값 자체는 담지 않는다.
            fields.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("fields", fields);
        return respond(ErrorCode.VALIDATION_ERROR, details);
    }

    /** 필수 query parameter 누락. 예: GET /menus 에 date가 없는 경우. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParameter(
            MissingServletRequestParameterException exception) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("fields", Map.of(exception.getParameterName(), "필수 값입니다."));
        return respond(ErrorCode.VALIDATION_ERROR, details);
    }

    /**
     * query/path 값의 타입이나 enum이 계약과 다른 경우.
     *
     * <p>예: mealType=BRUNCH, date=not-a-date. 어떤 파라미터가 문제인지만
     * 알려주고 입력 값 자체는 응답에 담지 않는다.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("fields", Map.of(exception.getName(), "값의 형식이 올바르지 않습니다."));
        return respond(ErrorCode.VALIDATION_ERROR, details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException exception) {
        log.debug("요청 본문을 읽을 수 없음", exception);
        return respond(ErrorCode.VALIDATION_ERROR, null);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException exception) {
        return respond(ErrorCode.UNAUTHORIZED, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException exception) {
        return respond(ErrorCode.FORBIDDEN, null);
    }

    /**
     * 매핑되지 않은 경로.
     *
     * <p>Spring 6.1부터 정적 리소스 조회 실패가 NoResourceFoundException으로
     * 올라온다. 이걸 잡지 않으면 없는 URL이 500으로 응답된다.
     */
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ApiResponse<Void>> handleNoHandler(Exception exception) {
        return respond(ErrorCode.NOT_FOUND, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception exception) {
        // 원인은 서버 로그에만 남기고 응답에는 일반 문구만 내보낸다.
        log.error("처리되지 않은 예외", exception);
        return respond(ErrorCode.INTERNAL_ERROR, null);
    }

    private ResponseEntity<ApiResponse<Void>> respond(ErrorCode code, Object details) {
        HttpStatus status = code.status();
        ApiError error = new ApiError(code.name(), code.message(), details);
        return ResponseEntity.status(status).body(ApiResponse.failure(error));
    }
}
