package com.opsclear.controller;

import com.opsclear.dto.PreviewCronRequest;
import com.opsclear.dto.PreviewCronResponse;
import com.opsclear.exception.BadRequestException;
import com.opsclear.exception.ErrorMessages;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/schedules/preview")
@RequiredArgsConstructor
public class SchedulePreviewController {

    @PostMapping
    public ResponseEntity<PreviewCronResponse> preview(
            @Valid @RequestBody PreviewCronRequest request,
            Authentication auth) {
        ZoneId zone;
        try {
            zone = ZoneId.of(request.getTimezone());
        } catch (Exception e) {
            throw new BadRequestException(
                    ErrorMessages.RecurringSchedule.INVALID_TIMEZONE + request.getTimezone());
        }

        CronExpression cron;
        try {
            cron = CronExpression.parse(request.getCronExpression());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(ErrorMessages.RecurringSchedule.INVALID_CRON + e.getMessage());
        }

        List<Instant> nextRuns = new ArrayList<>();
        ZonedDateTime current = ZonedDateTime.now(zone);
        for (int i = 0; i < 5; i++) {
            current = cron.next(current);
            if (current == null) {
                break;
            }
            nextRuns.add(current.toInstant());
        }

        return ResponseEntity.ok(PreviewCronResponse.builder().nextRuns(nextRuns).build());
    }
}
