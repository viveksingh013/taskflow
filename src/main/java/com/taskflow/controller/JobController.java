package com.taskflow.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.taskflow.dto.CancelJobResponse;
import com.taskflow.dto.CreateJobRequest;
import com.taskflow.dto.CreateJobResponse;
import com.taskflow.dto.JobResponse;
import com.taskflow.service.JobService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/jobs")
public class JobController {

	private final JobService jobService;

	public JobController(JobService jobService) {
		this.jobService = jobService;
	}

	@PostMapping
	public ResponseEntity<CreateJobResponse> createJob(@Valid @RequestBody CreateJobRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(jobService.createJob(request));
	}

	@GetMapping("/{id}")
	public JobResponse getJob(@PathVariable UUID id) {
		return jobService.getJob(id);
	}

	@GetMapping
	public List<JobResponse> listJobs() {
		return jobService.listJobs();
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<CancelJobResponse> cancelJob(@PathVariable UUID id) {
		return ResponseEntity.ok(jobService.cancelJob(id));
	}
}
