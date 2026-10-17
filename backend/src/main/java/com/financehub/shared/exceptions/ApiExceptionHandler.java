package com.financehub.shared.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ApiFailure.class)
    ProblemDetail applicationFailure(ApiFailure exception, jakarta.servlet.http.HttpServletResponse response) {
        if (exception.status() == HttpStatus.TOO_MANY_REQUESTS) response.setHeader("Retry-After", "60");
        var problem = ProblemDetail.forStatusAndDetail(exception.status(), exception.getMessage());
        problem.setProperty("code", exception.code());
        return problem;
    }

    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,
        org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
        jakarta.validation.ConstraintViolationException.class,
        org.springframework.web.method.annotation.HandlerMethodValidationException.class})
    ProblemDetail malformedRequest(Exception exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid request");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException exception) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed");
        problem.setProperty("violations", exception.getBindingResult().getFieldErrors().stream()
            .map(e -> new Violation(e.getField(), e.getDefaultMessage())).toList());
        return problem;
    }

    @ExceptionHandler({IllegalArgumentException.class, java.time.DateTimeException.class, ArithmeticException.class})
    ProblemDetail invalidOperation(RuntimeException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, "Operation violates domain rules");
    }

    private record Violation(String field, String message) {}
}
