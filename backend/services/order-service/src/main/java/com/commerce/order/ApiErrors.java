package com.commerce.order;

import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
class ApiErrors {
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ProblemDetail> expected(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).body(ProblemDetail.forStatusAndDetail(error.getStatusCode(),error.getReason()==null?"Request rejected":error.getReason()));
    }
    @ExceptionHandler({MethodArgumentNotValidException.class,org.springframework.http.converter.HttpMessageNotReadableException.class})
    ResponseEntity<ProblemDetail> invalid(Exception error) {
        return ResponseEntity.badRequest().body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,"Invalid request"));
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception error) {
        LoggerFactory.getLogger(ApiErrors.class).error("Request failed: {}",error.getClass().getSimpleName());
        return ResponseEntity.status(503).body(ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,"Service temporarily unavailable"));
    }
}
