package com.taskflow.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.taskflow.entity.Job;
import com.taskflow.entity.JobStatus;

public interface JobRepository extends JpaRepository<Job, UUID> {

	List<Job> findByStatusAndScheduledAtLessThanEqual(JobStatus status, LocalDateTime scheduledAt);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			update Job j
			set j.status = com.taskflow.entity.JobStatus.RUNNING,
			    j.startedAt = :now,
			    j.updatedAt = :now
			where j.id = :id
			  and j.status = com.taskflow.entity.JobStatus.SCHEDULED
			""")
	int claimScheduledJob(@Param("id") UUID id, @Param("now") LocalDateTime now);
}
