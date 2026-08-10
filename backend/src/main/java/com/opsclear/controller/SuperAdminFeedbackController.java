package com.opsclear.controller;

import com.opsclear.aop.RequiresSuperUser;
import com.opsclear.dto.FeedbackSubmissionResponse;
import com.opsclear.service.FeedbackService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/super-admin/feedback")
@RequiredArgsConstructor
public class SuperAdminFeedbackController {

    private final FeedbackService feedbackService;

    @RequiresSuperUser
    @GetMapping
    public ResponseEntity<List<FeedbackSubmissionResponse>> listAll() {
        List<FeedbackSubmissionResponse> submissions = feedbackService.listAll()
                .stream()
                .map(FeedbackSubmissionResponse::from)
                .toList();
        return ResponseEntity.ok(submissions);
    }

    @RequiresSuperUser
    @PatchMapping("/{id}/decline")
    public ResponseEntity<Void> decline(@PathVariable UUID id) {
        feedbackService.decline(id);
        return ResponseEntity.noContent().build();
    }
}
