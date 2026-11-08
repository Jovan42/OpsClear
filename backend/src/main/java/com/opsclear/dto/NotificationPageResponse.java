package com.opsclear.dto;

import com.opsclear.model.NotificationPage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** One page of notifications. {@code page} is zero-based. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationPageResponse {

    private List<NotificationResponse> items;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;

    public static NotificationPageResponse from(NotificationPage page) {
        return NotificationPageResponse.builder()
                .items(page.getItems().stream().map(NotificationResponse::from).toList())
                .page(page.getPage())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages((int) ((page.getTotalElements() + page.getSize() - 1) / page.getSize()))
                .build();
    }
}
