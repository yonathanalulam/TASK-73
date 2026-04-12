package com.dojostay.common.exception;

import org.springframework.http.HttpStatus;

public class AccountLockedException extends BusinessException {

    public AccountLockedException(String message) {
        super("ACCOUNT_LOCKED", message, HttpStatus.LOCKED);
    }
}
