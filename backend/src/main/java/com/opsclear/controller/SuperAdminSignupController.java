package com.opsclear.controller;

import com.opsclear.aop.RequiresSuperUser;
import com.opsclear.dto.EmailSignupResponse;
import com.opsclear.service.EmailSignupService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/super-admin/signups")
@RequiredArgsConstructor
public class SuperAdminSignupController {

    private static final MediaType TEXT_CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final EmailSignupService emailSignupService;

    // `list` is optional here so a missing one gets the service's 400 instead of an unhandled 500
    @RequiresSuperUser
    @GetMapping
    public ResponseEntity<List<EmailSignupResponse>> list(@RequestParam(required = false) String list) {
        List<EmailSignupResponse> signups = emailSignupService.list(list)
                .stream()
                .map(EmailSignupResponse::from)
                .toList();
        return ResponseEntity.ok(signups);
    }

    @RequiresSuperUser
    @GetMapping("/export")
    public ResponseEntity<String> export(@RequestParam(required = false) String list) {
        // The service rejects an unknown list, so `list` is a known wire value by the time it's in the filename
        String csv = emailSignupService.exportCsv(list);
        String filename = list + "-" + LocalDate.now(ZoneOffset.UTC) + ".csv";
        return ResponseEntity.ok()
                .contentType(TEXT_CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(csv);
    }

    @RequiresSuperUser
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        emailSignupService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
