package com.opsclear.dto;

import com.opsclear.model.NoteModel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NoteResponse {

    private UUID id;
    private UUID jobId;
    private UUID authorId;
    private String content;
    private Instant createdAt;

    public static NoteResponse from(NoteModel model) {
        return NoteResponse.builder()
                .id(model.getId())
                .jobId(model.getJobId())
                .authorId(model.getAuthorId())
                .content(model.getContent())
                .createdAt(model.getCreatedAt())
                .build();
    }
}
