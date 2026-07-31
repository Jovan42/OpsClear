package com.opsclear.controller;

import com.opsclear.aop.RequiresSuperUser;
import com.opsclear.dto.FeedbackSubmissionResponse;
import com.opsclear.service.FeedbackService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
}
