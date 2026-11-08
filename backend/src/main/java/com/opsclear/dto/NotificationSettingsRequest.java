package com.opsclear.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Replaces the caller's muted notification types. Names are validated by NotificationSettingsService, so an
 * unknown type gets a specific 400 message instead of a generic malformed-body error.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationSettingsRequest {

    @NotNull(message = "muted is required")
    private List<String> muted;
}
