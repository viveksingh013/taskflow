package com.taskflow.service;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.taskflow.dto.CancelJobResponse;
import com.taskflow.dto.CreateJobRequest;
import com.taskflow.dto.CreateJobResponse;
import com.taskflow.dto.JobResponse;
import com.taskflow.dto.TargetResponse;
import com.taskflow.entity.Job;
import com.taskflow.entity.JobStatus;
import com.taskflow.exception.InvalidJobStatusTransitionException;
import com.taskflow.exception.InvalidScheduleException;
import com.taskflow.exception.InvalidTargetException;
import com.taskflow.exception.JobNotFoundException;
import com.taskflow.repository.JobRepository;

@Service
public class JobService {

	private static final String JOB_SCHEDULED_MESSAGE = "Job scheduled successfully";
	private static final String JOB_CANCELLED_MESSAGE = "Job cancelled successfully";
	private static final Set<String> SUPPORTED_METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE");

	private final JobRepository jobRepository;

	public JobService(JobRepository jobRepository) {
		this.jobRepository = jobRepository;
	}

	@Transactional
	public CreateJobResponse createJob(CreateJobRequest request) {
		if (!request.getScheduledAt().isAfter(LocalDateTime.now())) {
			throw new InvalidScheduleException("scheduledAt must be in the future");
		}

		CreateJobRequest.TargetRequest target = request.getTarget();
		validateTarget(target);
		Job job = new Job(
				request.getName(),
				request.getScheduledAt(),
				target.getUrl(),
				target.getMethod(),
				target.getHeaders(),
				target.getBody()
		);
		Job saved = jobRepository.save(job);

		return new CreateJobResponse(saved.getId(), saved.getStatus().name(), JOB_SCHEDULED_MESSAGE);
	}

	@Transactional(readOnly = true)
	public JobResponse getJob(UUID id) {
		return toResponse(findJob(id));
	}

	@Transactional(readOnly = true)
	public List<JobResponse> listJobs() {
		return jobRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
				.stream()
				.map(this::toResponse)
				.toList();
	}

	@Transactional
	public CancelJobResponse cancelJob(UUID id) {
		Job job = findJob(id);
		if (job.getStatus() != JobStatus.SCHEDULED) {
			throw new InvalidJobStatusTransitionException(job.getStatus());
		}

		job.markCancelled();
		Job saved = jobRepository.save(job);
		return new CancelJobResponse(saved.getId(), saved.getStatus().name(), JOB_CANCELLED_MESSAGE);
	}

	private Job findJob(UUID id) {
		return jobRepository.findById(id).orElseThrow(() -> new JobNotFoundException(id));
	}

	private JobResponse toResponse(Job job) {
		return new JobResponse(
				job.getId(),
				job.getName(),
				job.getScheduledAt(),
				new TargetResponse(
						job.getTargetUrl(),
						job.getTargetMethod(),
						emptyIfNull(job.getHeaders()),
						emptyIfNull(job.getBody())
				),
				job.getStatus(),
				job.getCreatedAt(),
				job.getUpdatedAt(),
				job.getStartedAt(),
				job.getCompletedAt(),
				job.getLastError()
		);
	}

	private static void validateTarget(CreateJobRequest.TargetRequest target) {
		String method = target.getMethod().toUpperCase(Locale.ROOT);
		if (!SUPPORTED_METHODS.contains(method)) {
			throw new InvalidTargetException("target.method must be GET, POST, PUT, PATCH, or DELETE");
		}
		URI uri;
		try {
			uri = URI.create(target.getUrl());
		} catch (IllegalArgumentException exception) {
			throw new InvalidTargetException("target.url must be an HTTP or HTTPS URL");
		}
		String scheme = uri.getScheme();
		if (scheme == null
				|| !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
				|| uri.getHost() == null) {
			throw new InvalidTargetException("target.url must be an HTTP or HTTPS URL");
		}
	}

	private static <K, V> Map<K, V> emptyIfNull(Map<K, V> value) {
		return value == null ? new HashMap<>() : value;
	}
}
