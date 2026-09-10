package com.taskflow.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskflow.dto.CancelJobResponse;
import com.taskflow.dto.CreateJobRequest;
import com.taskflow.dto.CreateJobResponse;
import com.taskflow.dto.JobResponse;
import com.taskflow.dto.TargetResponse;
import com.taskflow.entity.JobStatus;
import com.taskflow.exception.ApiExceptionHandler;
import com.taskflow.exception.InvalidJobStatusTransitionException;
import com.taskflow.exception.InvalidScheduleException;
import com.taskflow.exception.JobNotFoundException;
import com.taskflow.service.JobService;

class JobControllerTest {

	private final JobService jobService = mock(JobService.class);
	private final MockMvc mockMvc = buildMockMvc();
	private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

	@Test
	void returnsGeneratedIdAndScheduledStatus() throws Exception {
		UUID id = UUID.randomUUID();
		when(jobService.createJob(any(CreateJobRequest.class)))
				.thenReturn(new CreateJobResponse(id, "SCHEDULED", "Job scheduled successfully"));

		mockMvc.perform(post("/api/v1/jobs")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(validRequest())))
				.andExpect(status().isCreated())
				.andExpect(content().json("""
						{"id":"%s","status":"SCHEDULED","message":"Job scheduled successfully"}
						""".formatted(id)));
	}

	@Test
	void createValidationFailureReturns400() throws Exception {
		CreateJobRequest request = validRequest();
		request.setName(" ");

		mockMvc.perform(post("/api/v1/jobs")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.message").value("Request validation failed"))
				.andExpect(jsonPath("$.timestamp").exists());
	}

	@Test
	void pastScheduleReturns400() throws Exception {
		when(jobService.createJob(any(CreateJobRequest.class)))
				.thenThrow(new InvalidScheduleException("scheduledAt must be in the future"));

		mockMvc.perform(post("/api/v1/jobs")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(validRequest())))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("INVALID_SCHEDULE"))
				.andExpect(jsonPath("$.message").value("scheduledAt must be in the future"));
	}

	@Test
	void getExistingJobReturns200() throws Exception {
		UUID id = UUID.randomUUID();
		when(jobService.getJob(id)).thenReturn(jobResponse(id));

		mockMvc.perform(get("/api/v1/jobs/{id}", id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(id.toString()))
				.andExpect(jsonPath("$.name").value("Generate Daily Report"))
				.andExpect(jsonPath("$.status").value("SCHEDULED"))
				.andExpect(jsonPath("$.target.url").value("https://example.com/api/report"));
	}

	@Test
	void getUnknownJobReturns404() throws Exception {
		UUID id = UUID.randomUUID();
		when(jobService.getJob(id)).thenThrow(new JobNotFoundException(id));

		mockMvc.perform(get("/api/v1/jobs/{id}", id))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.error").value("JOB_NOT_FOUND"))
				.andExpect(jsonPath("$.message").value("Job not found: " + id));
	}

	@Test
	void listJobsReturns200() throws Exception {
		UUID id = UUID.randomUUID();
		when(jobService.listJobs()).thenReturn(List.of(jobResponse(id)));

		mockMvc.perform(get("/api/v1/jobs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(id.toString()))
				.andExpect(jsonPath("$[0].status").value("SCHEDULED"));
	}

	@Test
	void cancelScheduledJobReturns200() throws Exception {
		UUID id = UUID.randomUUID();
		when(jobService.cancelJob(id))
				.thenReturn(new CancelJobResponse(id, "CANCELLED", "Job cancelled successfully"));

		mockMvc.perform(delete("/api/v1/jobs/{id}", id))
				.andExpect(status().isOk())
				.andExpect(content().json("""
						{"id":"%s","status":"CANCELLED","message":"Job cancelled successfully"}
						""".formatted(id)));
	}

	@Test
	void cancelUnknownJobReturns404() throws Exception {
		UUID id = UUID.randomUUID();
		when(jobService.cancelJob(id)).thenThrow(new JobNotFoundException(id));

		mockMvc.perform(delete("/api/v1/jobs/{id}", id))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("JOB_NOT_FOUND"));
	}

	@Test
	void cancelInvalidStatusReturns409() throws Exception {
		UUID id = UUID.randomUUID();
		when(jobService.cancelJob(id)).thenThrow(new InvalidJobStatusTransitionException(JobStatus.COMPLETED));

		mockMvc.perform(delete("/api/v1/jobs/{id}", id))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("INVALID_STATUS_TRANSITION"))
				.andExpect(jsonPath("$.message").value("Cannot cancel job in COMPLETED status"));
	}

	private JobResponse jobResponse(UUID id) {
		return new JobResponse(
				id,
				"Generate Daily Report",
				LocalDateTime.of(2026, 9, 11, 18, 0),
				new TargetResponse("https://example.com/api/report", "POST", java.util.Map.of(), java.util.Map.of()),
				JobStatus.SCHEDULED,
				LocalDateTime.of(2026, 9, 10, 12, 0),
				LocalDateTime.of(2026, 9, 10, 12, 0),
				null,
				null,
				null
		);
	}

	private CreateJobRequest validRequest() {
		CreateJobRequest request = new CreateJobRequest();
		request.setName("Generate Daily Report");
		request.setScheduledAt(LocalDateTime.now().plusHours(1));
		CreateJobRequest.TargetRequest target = new CreateJobRequest.TargetRequest();
		target.setUrl("https://example.com/api/report");
		target.setMethod("POST");
		request.setTarget(target);
		return request;
	}

	private MockMvc buildMockMvc() {
		LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
		validator.afterPropertiesSet();
		return MockMvcBuilders.standaloneSetup(new JobController(jobService))
				.setValidator(validator)
				.setControllerAdvice(new ApiExceptionHandler())
				.build();
	}
}
