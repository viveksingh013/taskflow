package com.taskflow.exception;

import com.taskflow.entity.JobStatus;

public class InvalidJobStatusTransitionException extends RuntimeException {

	public InvalidJobStatusTransitionException(JobStatus status) {
		super("Cannot cancel job in " + status + " status");
	}
}
