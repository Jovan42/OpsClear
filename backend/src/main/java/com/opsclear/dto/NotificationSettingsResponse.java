package com.opsclear.dto;

import com.opsclear.model.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationSettingsResponse {

    private List<NotificationType> muted;

    /** Muted types in declaration order of {@link NotificationType}. */
    public static NotificationSettingsResponse from(Set<NotificationType> muted) {
        return NotificationSettingsResponse.builder()
                .muted(muted.stream().sorted().toList())
                .build();
    }
}
