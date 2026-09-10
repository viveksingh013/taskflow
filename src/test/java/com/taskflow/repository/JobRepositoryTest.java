package com.taskflow.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.taskflow.entity.Job;
import com.taskflow.entity.JobStatus;

@SpringBootTest
@Transactional
class JobRepositoryTest {

	@Autowired
	private JobRepository jobRepository;

	@Autowired
	private TransactionTemplate transactionTemplate;

	@Test
	void futureJobRemainsUnselected() {
		Job future = job("future", LocalDateTime.now().plusHours(1), JobStatus.SCHEDULED);
		jobRepository.saveAndFlush(future);

		List<Job> due = jobRepository.findByStatusAndScheduledAtLessThanEqual(
				JobStatus.SCHEDULED,
				LocalDateTime.now()
		);

		assertTrue(due.stream().noneMatch(job -> job.getId().equals(future.getId())));
	}

	@Test
	void dueScheduledJobIsDiscovered() {
		Job due = job("due", LocalDateTime.now().minusSeconds(5), JobStatus.SCHEDULED);
		jobRepository.saveAndFlush(due);

		List<Job> found = jobRepository.findByStatusAndScheduledAtLessThanEqual(
				JobStatus.SCHEDULED,
				LocalDateTime.now()
		);

		assertTrue(found.stream().anyMatch(job -> job.getId().equals(due.getId())));
	}

	@Test
	void nonDueStatusesAreNotSelected() {
		Job cancelled = job("cancelled", LocalDateTime.now().minusMinutes(1), JobStatus.CANCELLED);
		jobRepository.saveAndFlush(cancelled);

		List<Job> found = jobRepository.findByStatusAndScheduledAtLessThanEqual(
				JobStatus.SCHEDULED,
				LocalDateTime.now()
		);

		assertTrue(found.stream().noneMatch(job -> job.getId().equals(cancelled.getId())));
	}

	@Test
	void scheduledJobCanBeClaimedOnce() {
		Job job = jobRepository.saveAndFlush(job("claim-once", LocalDateTime.now().minusSeconds(1), JobStatus.SCHEDULED));

		int first = claim(job);
		int second = claim(job);

		assertEquals(1, first);
		assertEquals(0, second);
		assertEquals(JobStatus.RUNNING, jobRepository.findById(job.getId()).orElseThrow().getStatus());
	}

	@Test
	void runningJobCannotBeClaimed() {
		assertEquals(0, claim(jobRepository.saveAndFlush(job("running", LocalDateTime.now().minusSeconds(1), JobStatus.RUNNING))));
	}

	@Test
	void cancelledJobCannotBeClaimed() {
		assertEquals(0, claim(jobRepository.saveAndFlush(job("cancelled", LocalDateTime.now().minusSeconds(1), JobStatus.CANCELLED))));
	}

	@Test
	void completedJobCannotBeClaimed() {
		assertEquals(0, claim(jobRepository.saveAndFlush(job("completed", LocalDateTime.now().minusSeconds(1), JobStatus.COMPLETED))));
	}

	@Test
	void failedJobCannotBeClaimed() {
		assertEquals(0, claim(jobRepository.saveAndFlush(job("failed", LocalDateTime.now().minusSeconds(1), JobStatus.FAILED))));
	}

	private int claim(Job job) {
		return transactionTemplate.execute(status ->
				jobRepository.claimScheduledJob(job.getId(), LocalDateTime.now())
		);
	}

	private Job job(String name, LocalDateTime scheduledAt, JobStatus status) {
		Job job = new Job(name, scheduledAt, "https://example.com/jobs", "POST", Map.of(), Map.of());
		job.setStatus(status);
		return job;
	}
}
