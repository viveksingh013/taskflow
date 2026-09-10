package com.taskflow.dto;

import java.util.UUID;

public record CancelJobResponse(UUID id, String status, String message) {
}
