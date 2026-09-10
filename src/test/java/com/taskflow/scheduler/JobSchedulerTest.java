package com.taskflow.scheduler;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.taskflow.entity.Job;
import com.taskflow.entity.JobStatus;
import com.taskflow.repository.JobRepository;
import com.taskflow.service.JobExecutionService;

@ExtendWith(MockitoExtension.class)
class JobSchedulerTest {

	@Mock
	private JobRepository jobRepository;

	@Mock
	private JobExecutionService jobExecutionService;

	@Test
	void doesNothingWhenNoDueJobs() {
		when(jobRepository.findByStatusAndScheduledAtLessThanEqual(eq(JobStatus.SCHEDULED), any()))
				.thenReturn(List.of());

		new JobScheduler(jobRepository, jobExecutionService).processDueJobs();

		verify(jobExecutionService, never()).executeIfClaimed(any());
	}

	@Test
	void continuesAfterIndividualJobFailure() {
		Job first = job("first");
		Job second = job("second");
		when(jobRepository.findByStatusAndScheduledAtLessThanEqual(eq(JobStatus.SCHEDULED), any()))
				.thenReturn(List.of(first, second));
		doThrow(new RuntimeException("boom")).when(jobExecutionService).executeIfClaimed(first.getId());

		new JobScheduler(jobRepository, jobExecutionService).processDueJobs();

		verify(jobExecutionService).executeIfClaimed(first.getId());
		verify(jobExecutionService).executeIfClaimed(second.getId());
	}

	@Test
	void executesEachDiscoveredJob() {
		Job job = job("due");
		when(jobRepository.findByStatusAndScheduledAtLessThanEqual(eq(JobStatus.SCHEDULED), any()))
				.thenReturn(List.of(job));
		AtomicInteger executions = new AtomicInteger();
		org.mockito.Mockito.doAnswer(invocation -> {
			executions.incrementAndGet();
			return null;
		}).when(jobExecutionService).executeIfClaimed(job.getId());

		new JobScheduler(jobRepository, jobExecutionService).processDueJobs();

		assertEquals(1, executions.get());
	}

	private Job job(String name) {
		return new Job(name, LocalDateTime.now().minusSeconds(1), "https://example.com", "GET", Map.of(), Map.of());
	}
}
