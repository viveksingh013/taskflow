package com.taskflow.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDateTime;
import java.util.Map;

public class CreateJobRequest {

	@NotBlank
	private String name;

	@NotNull
	private LocalDateTime scheduledAt;

	@NotNull
	@Valid
	private TargetRequest target;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public LocalDateTime getScheduledAt() {
		return scheduledAt;
	}

	public void setScheduledAt(LocalDateTime scheduledAt) {
		this.scheduledAt = scheduledAt;
	}

	public TargetRequest getTarget() {
		return target;
	}

	public void setTarget(TargetRequest target) {
		this.target = target;
	}

	public static class TargetRequest {

		@NotBlank
		@Pattern(regexp = "(?i)https?://.+", message = "must be an HTTP or HTTPS URL")
		private String url;

		@NotBlank
		@Pattern(regexp = "(?i)GET|POST|PUT|PATCH|DELETE", message = "must be GET, POST, PUT, PATCH, or DELETE")
		private String method;

		private Map<String, String> headers;

		private Map<String, Object> body;

		public String getUrl() {
			return url;
		}

		public void setUrl(String url) {
			this.url = url;
		}

		public String getMethod() {
			return method;
		}

		public void setMethod(String method) {
			this.method = method;
		}

		public Map<String, String> getHeaders() {
			return headers;
		}

		public void setHeaders(Map<String, String> headers) {
			this.headers = headers;
		}

		public Map<String, Object> getBody() {
			return body;
		}

		public void setBody(Map<String, Object> body) {
			this.body = body;
		}
	}
}