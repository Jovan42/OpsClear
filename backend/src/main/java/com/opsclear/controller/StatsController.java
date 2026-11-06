package com.opsclear.controller;

import com.opsclear.aop.AddonCode;
import com.opsclear.aop.RequiresAddon;
import com.opsclear.dto.StatsResponse;
import com.opsclear.exception.BadRequestException;
import com.opsclear.exception.ErrorMessages;
import com.opsclear.security.SecurityUtils;
import com.opsclear.service.FriendlyIdResolver;
import com.opsclear.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Project job completion velocity stats (ADR-0050), gated by the DASHBOARD add-on. {@code weeks} is validated by
 * StatsService (4, 12 or 26, default 12); {@code tz} is parsed here (IANA zone, default UTC). Both are checked
 * before the membership guards, so an invalid value is a 400 even for a caller who isn't a project member.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;
    private final FriendlyIdResolver friendlyIdResolver;

    @RequiresAddon(AddonCode.DASHBOARD)
    @GetMapping
    public ResponseEntity<StatsResponse> get(
            @PathVariable String projectId,
            @RequestParam(required = false) Integer weeks,
            @RequestParam(required = false) String tz,
            @RequestParam(required = false) UUID memberId,
            Authentication auth) {
        UUID callerId = SecurityUtils.resolveUserId(auth);
        UUID pid = friendlyIdResolver.resolveProject(projectId, callerId);
        ZoneId zone = parseZone(tz);
        return ResponseEntity.ok(StatsResponse.from(
                statsService.getProjectStats(pid, callerId, memberId, weeks, zone)));
    }

    // null means "not sent": StatsService then defaults to UTC.
    private static ZoneId parseZone(String tz) {
        if (tz == null) {
            return null;
        }
        try {
            return ZoneId.of(tz);
        } catch (DateTimeException e) {
            throw new BadRequestException(ErrorMessages.Stats.INVALID_TIMEZONE);
        }
    }
}
