package com.opsclear.controller;

import com.opsclear.dto.CreateNoteRequest;
import com.opsclear.dto.NoteResponse;
import com.opsclear.dto.NotesByJobResponse;
import com.opsclear.model.NoteModel;
import com.opsclear.service.NoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import com.opsclear.security.SecurityUtils;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class NoteController {

    private final NoteService noteService;

    @PostMapping("/api/projects/{projectId}/jobs/{jobId}/notes")
    public ResponseEntity<NoteResponse> create(
            @PathVariable UUID projectId,
            @PathVariable UUID jobId,
            @Valid @RequestBody CreateNoteRequest request,
            Authentication auth) {
        UUID callerId = SecurityUtils.resolveUserId(auth);
        NoteModel note = noteService.create(projectId, jobId, request.getContent(), callerId);
        return ResponseEntity.status(201).body(NoteResponse.from(note));
    }

    @GetMapping("/api/projects/{projectId}/jobs/{jobId}/notes")
    public ResponseEntity<List<NoteResponse>> listByJob(
            @PathVariable UUID projectId,
            @PathVariable UUID jobId,
            Authentication auth) {
        UUID callerId = SecurityUtils.resolveUserId(auth);
        List<NoteResponse> notes = noteService.listByJob(projectId, jobId, callerId)
                .stream()
                .map(NoteResponse::from)
                .toList();
        return ResponseEntity.ok(notes);
    }

    @GetMapping("/api/projects/{projectId}/notes")
    public ResponseEntity<List<NotesByJobResponse>> listByProject(
            @PathVariable UUID projectId,
            Authentication auth) {
        UUID callerId = SecurityUtils.resolveUserId(auth);
        List<NoteModel> flat = noteService.listByProject(projectId, callerId);
        return ResponseEntity.ok(groupByJob(flat));
    }

    private List<NotesByJobResponse> groupByJob(List<NoteModel> notes) {
        Map<UUID, NotesByJobResponse> grouped = new LinkedHashMap<>();
        for (NoteModel note : notes) {
            grouped.computeIfAbsent(note.getJobId(), id -> NotesByJobResponse.builder()
                            .jobId(id)
                            .jobName(note.getJobName())
                            .notes(new ArrayList<>())
                            .build())
                    .getNotes().add(NoteResponse.from(note));
        }
        return new ArrayList<>(grouped.values());
    }
}
