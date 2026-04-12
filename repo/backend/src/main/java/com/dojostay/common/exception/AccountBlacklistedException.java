package com.dojostay.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a login attempt targets an account that has been blacklisted by
 * the credential-review / risk-control flow. Distinct from
 * {@link AccountLockedException} so clients and audit logs can tell a
 * temporary lockout from a permanent blacklist — the error code
 * {@code ACCOUNT_BLACKLISTED} is how the mobile/web client knows to show the
 * "contact your org admin" screen instead of the retry-later screen.
 */
public class AccountBlacklistedException extends BusinessException {

    public AccountBlacklistedException(String message) {
        super("ACCOUNT_BLACKLISTED", message, HttpStatus.LOCKED);
    }
}
