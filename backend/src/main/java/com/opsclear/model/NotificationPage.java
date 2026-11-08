package com.opsclear.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * One page of a caller's notifications. {@code page} is zero-based; {@code totalElements} counts every
 * notification matching the filters (after the visibility filter), not just this page.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationPage {

    @Builder.Default
    private List<NotificationModel> items = new ArrayList<>();
    private int page;
    private int size;
    private long totalElements;
}
