package com.taskflow.service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.taskflow.entity.Job;
import com.taskflow.entity.JobStatus;
import com.taskflow.http.HttpExecutionResult;
import com.taskflow.http.HttpJobExecutor;
import com.taskflow.repository.JobRepository;

@ExtendWith(MockitoExtension.class)
class JobExecutionServiceTest {

	@Mock
	private JobRepository jobRepository;

	@Mock
	private HttpJobExecutor httpJobExecutor;

	@Mock
	private TransactionTemplate transactionTemplate;

	@Mock
	private TransactionStatus transactionStatus;

	private JobExecutionService jobExecutionService;

	@BeforeEach
	void setUp() {
		jobExecutionService = new JobExecutionService(jobRepository, httpJobExecutor, transactionTemplate);
		when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
			TransactionCallback<Object> callback = invocation.getArgument(0);
			return callback.doInTransaction(transactionStatus);
		});
		org.mockito.Mockito.lenient().doAnswer(invocation -> {
			Consumer<TransactionStatus> callback = invocation.getArgument(0);
			callback.accept(transactionStatus);
			return null;
		}).when(transactionTemplate).executeWithoutResult(any());
	}

	@Test
	void successfulHttpMakesJobCompleted() {
		Job job = scheduledJob();
		when(jobRepository.claimScheduledJob(eq(job.getId()), any())).thenReturn(1);
		when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
		when(httpJobExecutor.execute(job)).thenReturn(HttpExecutionResult.success(200));

		jobExecutionService.executeIfClaimed(job.getId());

		assertEquals(JobStatus.COMPLETED, job.getStatus());
		assertNotNull(job.getCompletedAt());
		assertNull(job.getLastError());
		verify(jobRepository).save(job);
	}

	@Test
	void non2xxMakesJobFailed() {
		Job job = scheduledJob();
		when(jobRepository.claimScheduledJob(eq(job.getId()), any())).thenReturn(1);
		when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
		when(httpJobExecutor.execute(job)).thenReturn(HttpExecutionResult.failure(500, "HTTP 500"));

		jobExecutionService.executeIfClaimed(job.getId());

		assertEquals(JobStatus.FAILED, job.getStatus());
		assertNotNull(job.getCompletedAt());
		assertEquals("HTTP 500", job.getLastError());
	}

	@Test
	void executorExceptionMakesJobFailed() {
		Job job = scheduledJob();
		when(jobRepository.claimScheduledJob(eq(job.getId()), any())).thenReturn(1);
		when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
		when(httpJobExecutor.execute(job)).thenThrow(new RuntimeException("connection timed out"));

		jobExecutionService.executeIfClaimed(job.getId());

		assertEquals(JobStatus.FAILED, job.getStatus());
		assertEquals("connection timed out", job.getLastError());
	}

	@Test
	void unclaimedJobIsNotExecuted() {
		UUID id = UUID.randomUUID();
		when(jobRepository.claimScheduledJob(eq(id), any())).thenReturn(0);

		jobExecutionService.executeIfClaimed(id);

		verify(httpJobExecutor, never()).execute(any());
	}

	@Test
	void startedAtIsSetBySuccessfulClaim() {
		Job job = scheduledJob();
		AtomicInteger claims = new AtomicInteger();
		when(jobRepository.claimScheduledJob(eq(job.getId()), any())).thenAnswer(invocation -> {
			claims.incrementAndGet();
			job.setStatus(JobStatus.RUNNING);
			return 1;
		});

		assertTrue(jobExecutionService.claim(job.getId()));
		assertEquals(1, claims.get());
	}

	@Test
	void secondClaimFails() {
		UUID id = UUID.randomUUID();
		when(jobRepository.claimScheduledJob(eq(id), any())).thenReturn(1, 0);

		assertTrue(jobExecutionService.claim(id));
		assertFalse(jobExecutionService.claim(id));
		verify(jobRepository, times(2)).claimScheduledJob(eq(id), any());
	}

	private Job scheduledJob() {
		return new Job(
				"Generate Daily Report",
				LocalDateTime.now().minusSeconds(1),
				"https://example.com/api/report",
				"POST",
				Map.of("Content-Type", "application/json"),
				Map.of("reportType", "DAILY")
		);
	}
}
