package com.opsclear.model;

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
public class NoteModel {

    private UUID id;
    private UUID jobId;
    private String jobName;
    private UUID authorId;
    private String content;
    private Instant createdAt;
}
