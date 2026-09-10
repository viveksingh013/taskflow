package com.taskflow.dto;

import java.util.Map;

public record TargetResponse(
		String url,
		String method,
		Map<String, String> headers,
		Map<String, Object> body
) {
}
