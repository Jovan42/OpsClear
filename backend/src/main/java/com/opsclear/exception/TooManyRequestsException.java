package com.opsclear.exception;

import lombok.Getter;

// A domain rate limit, e.g. the invite resend cooldown (ADR-0059). Mapped to 429 + Retry-After.
@Getter
public class TooManyRequestsException extends RuntimeException {

    private final long retryAfterSeconds;

    public TooManyRequestsException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
