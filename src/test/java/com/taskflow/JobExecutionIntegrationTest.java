package com.taskflow;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpServer;
import com.taskflow.entity.Job;
import com.taskflow.entity.JobStatus;
import com.taskflow.repository.JobRepository;
import com.taskflow.scheduler.JobScheduler;
import com.taskflow.service.JobExecutionService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class JobExecutionIntegrationTest {

	@Autowired
	private JobRepository jobRepository;

	@Autowired
	private JobExecutionService jobExecutionService;

	private HttpServer server;
	private String baseUrl;
	private final AtomicInteger hits = new AtomicInteger();
	private int responseStatus = 200;

	@BeforeEach
	void startServer() throws IOException {
		hits.set(0);
		responseStatus = 200;
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/target", exchange -> {
			hits.incrementAndGet();
			exchange.getRequestBody().readAllBytes();
			byte[] response = "ok".getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(responseStatus, response.length);
			try (OutputStream outputStream = exchange.getResponseBody()) {
				outputStream.write(response);
			}
		});
		server.start();
		baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
	}

	@AfterEach
	void stopServer() {
		server.stop(0);
	}

	@Test
	void dueJobCompletesAndRecordsTimestamps() {
		Job job = persistDueJob("/target", JobStatus.SCHEDULED);

		jobExecutionService.executeIfClaimed(job.getId());

		Job persisted = jobRepository.findById(job.getId()).orElseThrow();
		assertEquals(JobStatus.COMPLETED, persisted.getStatus());
		assertNotNull(persisted.getStartedAt());
		assertNotNull(persisted.getCompletedAt());
		assertNull(persisted.getLastError());
		assertEquals(1, hits.get());
	}

	@Test
	void failingEndpointMarksJobFailed() {
		responseStatus = 500;
		Job job = persistDueJob("/target", JobStatus.SCHEDULED);

		jobExecutionService.executeIfClaimed(job.getId());

		Job persisted = jobRepository.findById(job.getId()).orElseThrow();
		assertEquals(JobStatus.FAILED, persisted.getStatus());
		assertNotNull(persisted.getStartedAt());
		assertNotNull(persisted.getCompletedAt());
		assertEquals("HTTP 500", persisted.getLastError());
	}

	@Test
	void cancelledJobIsNeverExecuted() {
		Job job = persistDueJob("/target", JobStatus.CANCELLED);

		jobExecutionService.executeIfClaimed(job.getId());

		assertEquals(JobStatus.CANCELLED, jobRepository.findById(job.getId()).orElseThrow().getStatus());
		assertEquals(0, hits.get());
	}

	@Test
	void schedulerProcessesDueJob() {
		Job job = persistDueJob("/target", JobStatus.SCHEDULED);

		new JobScheduler(jobRepository, jobExecutionService).processDueJobs();

		assertEquals(JobStatus.COMPLETED, jobRepository.findById(job.getId()).orElseThrow().getStatus());
		assertEquals(1, hits.get());
	}

	private Job persistDueJob(String path, JobStatus status) {
		Job job = new Job(
				"local-target",
				LocalDateTime.now().minusSeconds(2),
				baseUrl + path,
				"POST",
				Map.of(),
				Map.of("source", "test")
		);
		job.setStatus(status);
		return jobRepository.saveAndFlush(job);
	}
}
