package com.opsclear.controller;

import com.opsclear.dto.UserResponse;
import com.opsclear.service.UserService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Validated
public class UserController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<List<UserResponse>> search(
            @RequestParam
            @NotBlank(message = "email prefix must not be blank")
            @Size(min = 2, message = "email prefix must be at least 2 characters")
            String email) {
        List<UserResponse> results = userService.searchByEmail(email)
                .stream()
                .map(UserResponse::from)
                .toList();
        return ResponseEntity.ok(results);
    }
}
