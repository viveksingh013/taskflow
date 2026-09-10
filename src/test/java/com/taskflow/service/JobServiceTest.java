package com.taskflow.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import com.taskflow.dto.CancelJobResponse;
import com.taskflow.dto.CreateJobRequest;
import com.taskflow.dto.CreateJobResponse;
import com.taskflow.dto.JobResponse;
import com.taskflow.entity.Job;
import com.taskflow.entity.JobStatus;
import com.taskflow.exception.InvalidJobStatusTransitionException;
import com.taskflow.exception.InvalidScheduleException;
import com.taskflow.exception.InvalidTargetException;
import com.taskflow.exception.JobNotFoundException;
import com.taskflow.repository.JobRepository;

@ExtendWith(MockitoExtension.class)
class JobServiceTest {

	@Mock
	private JobRepository jobRepository;

	@Test
	void persistsFutureJobAsScheduled() {
		JobService jobService = new JobService(jobRepository);
		CreateJobRequest request = requestAt(LocalDateTime.now().plusHours(1));
		when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));

		CreateJobResponse result = jobService.createJob(request);

		assertNotNull(result.id());
		assertEquals(JobStatus.SCHEDULED.name(), result.status());
		assertEquals("Job scheduled successfully", result.message());
		verify(jobRepository).save(any(Job.class));
	}

	@Test
	void rejectsCurrentOrPastScheduledTime() {
		JobService jobService = new JobService(jobRepository);

		InvalidScheduleException exception = assertThrows(
				InvalidScheduleException.class,
				() -> jobService.createJob(requestAt(LocalDateTime.now()))
		);

		assertEquals("scheduledAt must be in the future", exception.getMessage());
	}

	@Test
	void rejectsNonHttpUrl() {
		JobService jobService = new JobService(jobRepository);
		CreateJobRequest request = requestAt(LocalDateTime.now().plusHours(1));
		request.getTarget().setUrl("file:///tmp/secret");

		assertThrows(InvalidTargetException.class, () -> jobService.createJob(request));
	}

	@Test
	void retrievesExistingJob() {
		JobService jobService = new JobService(jobRepository);
		Job job = scheduledJob();
		when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));

		JobResponse result = jobService.getJob(job.getId());

		assertEquals(job.getId(), result.id());
		assertEquals(job.getName(), result.name());
		assertEquals(JobStatus.SCHEDULED, result.status());
		assertEquals(job.getTargetUrl(), result.target().url());
		assertEquals(job.getTargetMethod(), result.target().method());
	}

	@Test
	void getUnknownJobThrowsNotFound() {
		JobService jobService = new JobService(jobRepository);
		UUID missingId = UUID.randomUUID();
		when(jobRepository.findById(missingId)).thenReturn(Optional.empty());

		JobNotFoundException exception = assertThrows(
				JobNotFoundException.class,
				() -> jobService.getJob(missingId)
		);

		assertEquals("Job not found: " + missingId, exception.getMessage());
	}

	@Test
	void listsJobs() {
		JobService jobService = new JobService(jobRepository);
		Job job = scheduledJob();
		when(jobRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))).thenReturn(List.of(job));

		List<JobResponse> result = jobService.listJobs();

		assertEquals(1, result.size());
		assertEquals(job.getId(), result.getFirst().id());
	}

	@Test
	void cancelsScheduledJob() {
		JobService jobService = new JobService(jobRepository);
		Job job = scheduledJob();
		when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
		when(jobRepository.save(job)).thenReturn(job);

		CancelJobResponse result = jobService.cancelJob(job.getId());

		assertEquals(JobStatus.CANCELLED, job.getStatus());
		assertEquals(job.getId(), result.id());
		assertEquals(JobStatus.CANCELLED.name(), result.status());
		assertEquals("Job cancelled successfully", result.message());
	}

	@Test
	void cancelUnknownJobThrowsNotFound() {
		JobService jobService = new JobService(jobRepository);
		UUID missingId = UUID.randomUUID();
		when(jobRepository.findById(missingId)).thenReturn(Optional.empty());

		assertThrows(JobNotFoundException.class, () -> jobService.cancelJob(missingId));
	}

	@Test
	void rejectsCancelWhenJobIsNotScheduled() {
		JobService jobService = new JobService(jobRepository);
		Job job = scheduledJob();
		job.setStatus(JobStatus.COMPLETED);
		when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));

		InvalidJobStatusTransitionException exception = assertThrows(
				InvalidJobStatusTransitionException.class,
				() -> jobService.cancelJob(job.getId())
		);

		assertTrue(exception.getMessage().contains("COMPLETED"));
		assertEquals(JobStatus.COMPLETED, job.getStatus());
	}

	private Job scheduledJob() {
		return new Job(
				"Generate Daily Report",
				LocalDateTime.now().plusHours(1),
				"https://example.com/api/report",
				"POST",
				Map.of("Content-Type", "application/json"),
				Map.of("reportType", "DAILY")
		);
	}

	private CreateJobRequest requestAt(LocalDateTime scheduledAt) {
		CreateJobRequest request = new CreateJobRequest();
		request.setName("Generate Daily Report");
		request.setScheduledAt(scheduledAt);

		CreateJobRequest.TargetRequest target = new CreateJobRequest.TargetRequest();
		target.setUrl("https://example.com/api/report");
		target.setMethod("POST");
		target.setHeaders(Map.of("Content-Type", "application/json"));
		target.setBody(Map.of("reportType", "DAILY"));
		request.setTarget(target);
		return request;
	}
}
