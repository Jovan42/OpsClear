package com.opsclear.dto;

/** Whether new organisations can be created ({@code opsclear.signup.open}, ADR-0066). */
public record SignupStatusResponse(boolean open) {
}
