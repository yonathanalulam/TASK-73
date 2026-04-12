package com.dojostay.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Base for any expected business-rule failure that should round-trip to the client as a
 * structured ApiError rather than a 500.
 */
public class BusinessException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public BusinessException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
