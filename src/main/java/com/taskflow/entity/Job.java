package com.taskflow.entity;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "jobs")
public class Job {

	@Id
	@Column(nullable = false, updatable = false)
	private UUID id = UUID.randomUUID();

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private LocalDateTime scheduledAt;

	@Column(nullable = false)
	private String targetUrl;

	@Column(nullable = false)
	private String targetMethod;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "jsonb")
	private Map<String, String> headers;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "jsonb")
	private Map<String, Object> body;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private JobStatus status;

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(nullable = false)
	private LocalDateTime updatedAt;

	private LocalDateTime startedAt;

	private LocalDateTime completedAt;

	@Column(length = 2000)
	private String lastError;

	protected Job() {
	}

	public Job(String name, LocalDateTime scheduledAt, String targetUrl, String targetMethod,
			Map<String, String> headers, Map<String, Object> body) {
		this.name = name;
		this.scheduledAt = scheduledAt;
		this.targetUrl = targetUrl;
		this.targetMethod = targetMethod == null ? null : targetMethod.toUpperCase(Locale.ROOT);
		this.headers = copyHeaders(headers);
		this.body = copyBody(body);
		this.status = JobStatus.SCHEDULED;
	}

	public void markCancelled() {
		this.status = JobStatus.CANCELLED;
	}

	public void markCompleted() {
		this.status = JobStatus.COMPLETED;
		this.completedAt = LocalDateTime.now();
		this.lastError = null;
	}

	public void markFailed(String error) {
		this.status = JobStatus.FAILED;
		this.completedAt = LocalDateTime.now();
		this.lastError = truncateError(error);
	}

	public void setStatus(JobStatus status) {
		this.status = status;
	}

	@PrePersist
	void onCreate() {
		LocalDateTime now = LocalDateTime.now();
		createdAt = now;
		updatedAt = now;
		if (status == null) {
			status = JobStatus.SCHEDULED;
		}
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = LocalDateTime.now();
	}

	public UUID getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public LocalDateTime getScheduledAt() {
		return scheduledAt;
	}

	public String getTargetUrl() {
		return targetUrl;
	}

	public String getTargetMethod() {
		return targetMethod;
	}

	public Map<String, String> getHeaders() {
		return headers;
	}

	public Map<String, Object> getBody() {
		return body;
	}

	public JobStatus getStatus() {
		return status;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}

	public LocalDateTime getStartedAt() {
		return startedAt;
	}

	public LocalDateTime getCompletedAt() {
		return completedAt;
	}

	public String getLastError() {
		return lastError;
	}

	private static String truncateError(String error) {
		if (error == null) {
			return "Job execution failed";
		}
		return error.length() <= 2000 ? error : error.substring(0, 2000);
	}

	private static Map<String, String> copyHeaders(Map<String, String> headers) {
		return headers == null ? new HashMap<>() : new HashMap<>(headers);
	}

	private static Map<String, Object> copyBody(Map<String, Object> body) {
		return body == null ? new HashMap<>() : new HashMap<>(body);
	}
}