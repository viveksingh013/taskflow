package com.taskflow.scheduler;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.taskflow.entity.Job;
import com.taskflow.entity.JobStatus;
import com.taskflow.repository.JobRepository;
import com.taskflow.service.JobExecutionService;

@Component
@ConditionalOnProperty(prefix = "taskflow.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class JobScheduler {

	private static final Logger log = LoggerFactory.getLogger(JobScheduler.class);

	private final JobRepository jobRepository;
	private final JobExecutionService jobExecutionService;

	public JobScheduler(JobRepository jobRepository, JobExecutionService jobExecutionService) {
		this.jobRepository = jobRepository;
		this.jobExecutionService = jobExecutionService;
	}

	@Scheduled(fixedDelayString = "${taskflow.scheduler.fixed-delay:1000}")
	public void processDueJobs() {
		List<Job> dueJobs = jobRepository.findByStatusAndScheduledAtLessThanEqual(
				JobStatus.SCHEDULED,
				LocalDateTime.now()
		);
		if (dueJobs.isEmpty()) {
			return;
		}

		log.info("Jobs discovered: count={}", dueJobs.size());
		for (Job job : dueJobs) {
			try {
				jobExecutionService.executeIfClaimed(job.getId());
			} catch (Exception exception) {
				log.error("Scheduler skipped remaining work for jobId={}", job.getId(), exception);
			}
		}
	}
}
