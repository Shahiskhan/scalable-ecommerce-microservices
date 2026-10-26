package com.acme.product.api;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@RestControllerAdvice
public class ApiErrors {
    private static final Logger log = LoggerFactory.getLogger(ApiErrors.class);
    @ExceptionHandler(DuplicateKeyException.class)
    ProblemDetail duplicate() { return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "SKU already exists"); }
    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class, HandlerMethodValidationException.class})
    ProblemDetail invalid() { return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid request"); }
    @ExceptionHandler({DataAccessException.class, RedisConnectionFailureException.class})
    ProblemDetail unavailable(RuntimeException error) {
        log.warn("dependency_unavailable errorType={}", error.getClass().getSimpleName());
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "Required storage temporarily unavailable");
    }
}
