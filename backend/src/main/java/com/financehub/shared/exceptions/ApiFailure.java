package com.financehub.shared.exceptions;

import org.springframework.http.HttpStatus;

public class ApiFailure extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    public ApiFailure(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
    public HttpStatus status() { return status; }
    public String code() { return code; }
    public static ApiFailure unauthenticated() {
        return new ApiFailure(HttpStatus.UNAUTHORIZED, "authentication_failed", "Authentication failed");
    }
    public static ApiFailure notFound() {
        return new ApiFailure(HttpStatus.NOT_FOUND, "resource_not_found", "Resource not found");
    }
}
