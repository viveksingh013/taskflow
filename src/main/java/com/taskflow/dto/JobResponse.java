package com.taskflow.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.taskflow.entity.JobStatus;

public record JobResponse(
		UUID id,
		String name,
		LocalDateTime scheduledAt,
		TargetResponse target,
		JobStatus status,
		LocalDateTime createdAt,
		LocalDateTime updatedAt,
		LocalDateTime startedAt,
		LocalDateTime completedAt,
		String lastError
) {
}
