package com.opsclear.exception;

// The caller's organisation is LOCKED (trial over, no subscription) and the lock-out switch is on (ADR-0067).
// Mapped to 402 OrgLocked.
public class OrgLockedException extends RuntimeException {

    public OrgLockedException(String message) {
        super(message);
    }
}
