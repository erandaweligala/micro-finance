package com.mfin.common.error;

import com.mfin.loan.engine.LoanCalculationException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.List;

/**
 * Translates every exception into the platform's single {@link ApiError} shape.
 *
 * <p>Two rules matter here for a financial system: unexpected failures are logged in full but
 * returned as an opaque 500 (no stack traces, SQL, or entity names on the wire), and every
 * response carries the trace id so support can find the request without the customer quoting
 * anything sensitive.</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiExceptions.ApiException.class)
    public ResponseEntity<ApiError> handleApiException(ApiExceptions.ApiException ex,
                                                       HttpServletRequest request) {
        // Expected, business-level outcomes: logged at INFO without a stack trace.
        log.info("{} on {}: {}", ex.getCode(), request.getRequestURI(), ex.getMessage());
        return build(ex.getStatus(), ex.getCode(), ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleBodyValidation(MethodArgumentNotValidException ex,
                                                         HttpServletRequest request) {
        List<ApiError.FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(this::toViolation)
                .toList();
        ApiError error = ApiError.of(HttpStatus.BAD_REQUEST.value(), ErrorCodes.VALIDATION_FAILED,
                        "The request failed validation", request.getRequestURI(), traceId())
                .withErrors(violations);
        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleParameterValidation(ConstraintViolationException ex,
                                                              HttpServletRequest request) {
        List<ApiError.FieldViolation> violations = ex.getConstraintViolations().stream()
                .map(this::toViolation)
                .toList();
        ApiError error = ApiError.of(HttpStatus.BAD_REQUEST.value(), ErrorCodes.VALIDATION_FAILED,
                        "The request failed validation", request.getRequestURI(), traceId())
                .withErrors(violations);
        return ResponseEntity.badRequest().body(error);
    }

    /** Invalid loan terms surface as a field-level 422 rather than a generic failure. */
    @ExceptionHandler(LoanCalculationException.class)
    public ResponseEntity<ApiError> handleLoanCalculation(LoanCalculationException ex,
                                                          HttpServletRequest request) {
        ApiError error = ApiError.of(HttpStatus.UNPROCESSABLE_ENTITY.value(),
                        ErrorCodes.LOAN_CALCULATION_INVALID, ex.getMessage(),
                        request.getRequestURI(), traceId())
                .withErrors(List.of(new ApiError.FieldViolation(ex.field(), ex.getMessage(), null)));
        return ResponseEntity.unprocessableEntity().body(error);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiError> handleMalformed(Exception ex, HttpServletRequest request) {
        // The parser's message can echo payload fragments, so it is logged but not returned.
        log.debug("Malformed request on {}: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, ErrorCodes.MALFORMED_REQUEST,
                "The request could not be parsed", request);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimisticLock(OptimisticLockingFailureException ex,
                                                          HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ErrorCodes.CONCURRENT_MODIFICATION,
                "The record was modified by another request, please reload and retry", request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleIntegrity(DataIntegrityViolationException ex,
                                                     HttpServletRequest request) {
        log.warn("Integrity violation on {}", request.getRequestURI(), ex);
        return build(HttpStatus.CONFLICT, ErrorCodes.CONFLICT,
                "The request conflicts with existing data", request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex,
                                                        HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, ErrorCodes.ACCESS_DENIED,
                "You are not permitted to perform this operation", request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthentication(AuthenticationException ex,
                                                          HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, ErrorCodes.UNAUTHENTICATED,
                "Authentication is required", request);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiError> handleNoHandler(NoHandlerFoundException ex,
                                                     HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ErrorCodes.RESOURCE_NOT_FOUND,
                "No endpoint matches this request", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        // Full detail to the log (which is access-controlled), an opaque body to the caller.
        log.error("Unhandled exception on {} [trace={}]", request.getRequestURI(), traceId(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCodes.INTERNAL_ERROR,
                "An unexpected error occurred. Quote the trace id when contacting support.", request);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String code, String message,
                                           HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(ApiError.of(status.value(), code, message, request.getRequestURI(), traceId()));
    }

    private ApiError.FieldViolation toViolation(FieldError error) {
        return new ApiError.FieldViolation(error.getField(), error.getDefaultMessage(),
                sanitise(error.getField(), error.getRejectedValue()));
    }

    private ApiError.FieldViolation toViolation(ConstraintViolation<?> violation) {
        String field = violation.getPropertyPath().toString();
        return new ApiError.FieldViolation(field, violation.getMessage(),
                sanitise(field, violation.getInvalidValue()));
    }

    /** Never echo a rejected secret or identity document back to the client or into a log. */
    private Object sanitise(String field, Object rejectedValue) {
        if (field == null || rejectedValue == null) {
            return null;
        }
        String lower = field.toLowerCase();
        boolean sensitive = lower.contains("password") || lower.contains("secret")
                || lower.contains("token") || lower.contains("pin")
                || lower.contains("nationalid") || lower.contains("documentnumber");
        return sensitive ? "[redacted]" : rejectedValue;
    }

    private String traceId() {
        return org.slf4j.MDC.get("traceId");
    }
}
