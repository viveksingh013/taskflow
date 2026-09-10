package com.taskflow;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskflow.dto.CreateJobRequest;
import com.taskflow.entity.Job;
import com.taskflow.entity.JobStatus;
import com.taskflow.repository.JobRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class JobApiIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JobRepository jobRepository;

	private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

	@Test
	void createPersistsScheduledJobWithGeneratedUuid() throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/jobs")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(validRequest())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("SCHEDULED"))
				.andExpect(jsonPath("$.id").exists())
				.andReturn();

		UUID id = UUID.fromString(readJson(result).get("id").asText());
		Job persisted = jobRepository.findById(id).orElseThrow();

		assertEquals("Generate Daily Report", persisted.getName());
		assertEquals(JobStatus.SCHEDULED, persisted.getStatus());
		assertEquals("https://example.com/api/report", persisted.getTargetUrl());
		assertEquals("POST", persisted.getTargetMethod());
		assertNotNull(persisted.getCreatedAt());
		assertNotNull(persisted.getUpdatedAt());
		assertEquals("application/json", persisted.getHeaders().get("Content-Type"));
		assertEquals("DAILY", persisted.getBody().get("reportType"));
	}

	@Test
	void createRejectsPastScheduledAt() throws Exception {
		CreateJobRequest request = validRequest();
		request.setScheduledAt(LocalDateTime.now().minusMinutes(1));

		mockMvc.perform(post("/api/v1/jobs")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("INVALID_SCHEDULE"))
				.andExpect(jsonPath("$.message").value("scheduledAt must be in the future"))
				.andExpect(jsonPath("$.timestamp").exists());
	}

	@Test
	void createRejectsMissingRequiredFields() throws Exception {
		mockMvc.perform(post("/api/v1/jobs")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"","scheduledAt":null,"target":{"url":"","method":""}}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.message").value("Request validation failed"));
	}

	@Test
	void getReturnsPersistedJob() throws Exception {
		UUID id = createJob();

		mockMvc.perform(get("/api/v1/jobs/{id}", id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(id.toString()))
				.andExpect(jsonPath("$.name").value("Generate Daily Report"))
				.andExpect(jsonPath("$.status").value("SCHEDULED"))
				.andExpect(jsonPath("$.target.url").value("https://example.com/api/report"))
				.andExpect(jsonPath("$.target.method").value("POST"))
				.andExpect(jsonPath("$.createdAt").exists())
				.andExpect(jsonPath("$.updatedAt").exists())
				.andExpect(jsonPath("$.startedAt").isEmpty())
				.andExpect(jsonPath("$.completedAt").isEmpty())
				.andExpect(jsonPath("$.lastError").isEmpty());
	}

	@Test
	void createRejectsNonHttpUrl() throws Exception {
		CreateJobRequest request = validRequest();
		request.getTarget().setUrl("file:///tmp/secret");

		mockMvc.perform(post("/api/v1/jobs")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
	}

	@Test
	void createRejectsUnsupportedMethod() throws Exception {
		CreateJobRequest request = validRequest();
		request.getTarget().setMethod("FOO");

		mockMvc.perform(post("/api/v1/jobs")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
	}

	@Test
	void getUnknownUuidReturns404() throws Exception {
		UUID missingId = UUID.randomUUID();

		mockMvc.perform(get("/api/v1/jobs/{id}", missingId))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("JOB_NOT_FOUND"))
				.andExpect(jsonPath("$.message").value("Job not found: " + missingId));
	}

	@Test
	void listReturnsCreatedJobs() throws Exception {
		UUID id = createJob();

		mockMvc.perform(get("/api/v1/jobs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(id.toString()))
				.andExpect(jsonPath("$[0].status").value("SCHEDULED"));
	}

	@Test
	void cancelScheduledJobUpdatesStatus() throws Exception {
		UUID id = createJob();

		mockMvc.perform(delete("/api/v1/jobs/{id}", id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(id.toString()))
				.andExpect(jsonPath("$.status").value("CANCELLED"));

		assertEquals(JobStatus.CANCELLED, jobRepository.findById(id).orElseThrow().getStatus());
		assertTrue(jobRepository.existsById(id));
	}

	@Test
	void cancelUnknownUuidReturns404() throws Exception {
		UUID missingId = UUID.randomUUID();

		mockMvc.perform(delete("/api/v1/jobs/{id}", missingId))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("JOB_NOT_FOUND"));
	}

	@Test
	void cancelCompletedJobIsRejected() throws Exception {
		Job job = new Job(
				"Already done",
				LocalDateTime.now().plusHours(2),
				"https://example.com/done",
				"GET",
				Map.of(),
				Map.of()
		);
		job.setStatus(JobStatus.COMPLETED);
		jobRepository.saveAndFlush(job);

		mockMvc.perform(delete("/api/v1/jobs/{id}", job.getId()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("INVALID_STATUS_TRANSITION"))
				.andExpect(jsonPath("$.message").value("Cannot cancel job in COMPLETED status"));

		assertEquals(JobStatus.COMPLETED, jobRepository.findById(job.getId()).orElseThrow().getStatus());
	}

	private UUID createJob() throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/jobs")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(validRequest())))
				.andExpect(status().isCreated())
				.andReturn();
		return UUID.fromString(readJson(result).get("id").asText());
	}

	private CreateJobRequest validRequest() {
		CreateJobRequest request = new CreateJobRequest();
		request.setName("Generate Daily Report");
		request.setScheduledAt(LocalDateTime.now().plusHours(1));

		CreateJobRequest.TargetRequest target = new CreateJobRequest.TargetRequest();
		target.setUrl("https://example.com/api/report");
		target.setMethod("POST");
		target.setHeaders(Map.of("Content-Type", "application/json"));
		target.setBody(Map.of("reportType", "DAILY"));
		request.setTarget(target);
		return request;
	}

	private JsonNode readJson(MvcResult result) throws Exception {
		return objectMapper.readTree(result.getResponse().getContentAsString());
	}
}
