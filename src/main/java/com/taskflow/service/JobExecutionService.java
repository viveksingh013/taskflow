package com.taskflow.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.taskflow.entity.Job;
import com.taskflow.http.HttpExecutionResult;
import com.taskflow.http.HttpJobExecutor;
import com.taskflow.repository.JobRepository;

@Service
public class JobExecutionService {

	private static final Logger log = LoggerFactory.getLogger(JobExecutionService.class);

	private final JobRepository jobRepository;
	private final HttpJobExecutor httpJobExecutor;
	private final TransactionTemplate transactionTemplate;

	public JobExecutionService(
			JobRepository jobRepository,
			HttpJobExecutor httpJobExecutor,
			TransactionTemplate transactionTemplate
	) {
		this.jobRepository = jobRepository;
		this.httpJobExecutor = httpJobExecutor;
		this.transactionTemplate = transactionTemplate;
	}

	public void executeIfClaimed(UUID jobId) {
		if (!claim(jobId)) {
			return;
		}

		Job job = jobRepository.findById(jobId).orElse(null);
		if (job == null) {
			log.warn("Claimed job no longer exists: jobId={}", jobId);
			return;
		}

		log.info("Execution started: jobId={} targetUrl={}", jobId, job.getTargetUrl());
		HttpExecutionResult result;
		try {
			result = httpJobExecutor.execute(job);
		} catch (Exception exception) {
			log.error("Execution failed: jobId={} targetUrl={}", jobId, job.getTargetUrl(), exception);
			recordResult(jobId, HttpExecutionResult.failure(null, exception.getMessage()));
			return;
		}
		recordResult(jobId, result);
	}

	public boolean claim(UUID jobId) {
		Boolean claimed = transactionTemplate.execute(status ->
				jobRepository.claimScheduledJob(jobId, LocalDateTime.now()) == 1
		);
		if (Boolean.TRUE.equals(claimed)) {
			log.info("Job claimed: jobId={}", jobId);
			return true;
		}
		return false;
	}

	private void recordResult(UUID jobId, HttpExecutionResult result) {
		transactionTemplate.executeWithoutResult(status -> {
			Job job = jobRepository.findById(jobId).orElse(null);
			if (job == null) {
				return;
			}
			if (result.success()) {
				job.markCompleted();
				log.info("Execution succeeded: jobId={} httpStatus={}", jobId, result.statusCode());
			} else {
				job.markFailed(formatError(result));
				log.warn(
						"Execution failed: jobId={} httpStatus={} error={}",
						jobId,
						result.statusCode(),
						result.errorMessage()
				);
			}
			jobRepository.save(job);
		});
	}

	private static String formatError(HttpExecutionResult result) {
		if (result.errorMessage() != null && !result.errorMessage().isBlank()) {
			return result.errorMessage();
		}
		if (result.statusCode() != null) {
			return "HTTP " + result.statusCode();
		}
		return "Job execution failed";
	}
}
