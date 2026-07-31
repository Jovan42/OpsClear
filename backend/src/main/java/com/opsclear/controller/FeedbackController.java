package com.opsclear.controller;

import com.opsclear.dto.FeedbackSubmissionResponse;
import com.opsclear.dto.SubmitFeedbackRequest;
import com.opsclear.security.SecurityUtils;
import com.opsclear.service.FeedbackService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;

    @PostMapping
    public ResponseEntity<FeedbackSubmissionResponse> submit(
            @Valid @RequestBody SubmitFeedbackRequest request,
            Authentication auth) {
        UUID callerId = SecurityUtils.resolveUserId(auth);
        FeedbackSubmissionResponse response =
                FeedbackSubmissionResponse.from(feedbackService.submit(callerId, request));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/mine")
    public ResponseEntity<List<FeedbackSubmissionResponse>> listMine(Authentication auth) {
        UUID callerId = SecurityUtils.resolveUserId(auth);
        List<FeedbackSubmissionResponse> submissions = feedbackService.listMine(callerId)
                .stream()
                .map(FeedbackSubmissionResponse::from)
                .toList();
        return ResponseEntity.ok(submissions);
    }
}
