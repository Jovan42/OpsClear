package com.opsclear.event;

import java.util.UUID;

/** A note was added to a job (ADR-0051 {@code NOTE_ADDED}). {@code actorId} is the note's author. */
public record NoteAddedEvent(UUID noteId, UUID projectId, UUID jobId, UUID actorId) {
}
