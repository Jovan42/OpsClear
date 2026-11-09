package com.opsclear.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

/**
 * Data of the {@code read-state} SSE event (ADR-0051 §SSE stream), so the caller's other tabs update their badge and
 * list: {@code {ids: [...], read: true|false}} after marking notifications, or {@code {all: true}} after mark all read.
 * Unset fields are left out of the JSON.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReadStateEvent {

    private List<UUID> ids;
    private Boolean read;
    private Boolean all;

    public static ReadStateEvent of(List<UUID> ids, boolean read) {
        return ReadStateEvent.builder().ids(ids).read(read).build();
    }

    public static ReadStateEvent allRead() {
        return ReadStateEvent.builder().all(true).build();
    }
}
