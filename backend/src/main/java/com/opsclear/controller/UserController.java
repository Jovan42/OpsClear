package com.opsclear.controller;

import com.opsclear.dto.UserResponse;
import com.opsclear.security.SecurityUtils;
import com.opsclear.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * Searches the caller's organisation members by name or email. {@code q} is the query; {@code email} is a
     * deprecated alias kept until the frontend sends {@code q} (removed in JOB-402). When both are sent,
     * {@code q} wins.
     */
    @GetMapping
    public ResponseEntity<List<UserResponse>> search(
            @RequestParam(required = false) String q,
            @Deprecated @RequestParam(required = false) String email,
            Authentication auth) {
        UUID callerId = SecurityUtils.resolveUserId(auth);
        String query = q != null ? q : email;
        List<UserResponse> results = userService.searchOrgMembers(query, callerId)
                .stream()
                .map(UserResponse::from)
                .toList();
        return ResponseEntity.ok(results);
    }
}
