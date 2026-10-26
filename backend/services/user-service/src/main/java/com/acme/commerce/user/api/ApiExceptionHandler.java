package com.acme.commerce.user.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.converter.HttpMessageNotReadableException;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    @ExceptionHandler(ResponseStatusException.class) ProblemDetail domain(ResponseStatusException e) {
        return ProblemDetail.forStatusAndDetail(e.getStatusCode(), e.getReason() == null ? "Request failed" : e.getReason());
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    ProblemDetail invalid(Exception e) { return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid request body"); }
    @ExceptionHandler(Exception.class) ProblemDetail unexpected(Exception e) {
        if (e instanceof org.springframework.web.ErrorResponse error)
            return ProblemDetail.forStatusAndDetail(error.getStatusCode(), "Request could not be processed");
        if (e instanceof org.springframework.dao.DataAccessResourceFailureException)
            return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "Service temporarily unavailable");
        log.error("Request failed: {}", e.getClass().getSimpleName());
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected failure; use request ID when contacting support");
    }
}
