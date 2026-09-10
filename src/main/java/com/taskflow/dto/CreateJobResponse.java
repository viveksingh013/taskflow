package com.taskflow.dto;

import java.util.UUID;

public record CreateJobResponse(UUID id, String status, String message) {
}