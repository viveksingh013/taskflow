package com.taskflow.exception;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.taskflow.dto.ErrorResponse;

@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
		return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed");
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErrorResponse> handleUnreadableMessage(HttpMessageNotReadableException exception) {
		return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed");
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
		return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed");
	}

	@ExceptionHandler(InvalidScheduleException.class)
	public ResponseEntity<ErrorResponse> handleInvalidSchedule(InvalidScheduleException exception) {
		return error(HttpStatus.BAD_REQUEST, "INVALID_SCHEDULE", exception.getMessage());
	}

	@ExceptionHandler(InvalidTargetException.class)
	public ResponseEntity<ErrorResponse> handleInvalidTarget(InvalidTargetException exception) {
		return error(HttpStatus.BAD_REQUEST, "INVALID_TARGET", exception.getMessage());
	}

	@ExceptionHandler(JobNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleJobNotFound(JobNotFoundException exception) {
		return error(HttpStatus.NOT_FOUND, "JOB_NOT_FOUND", exception.getMessage());
	}

	@ExceptionHandler(InvalidJobStatusTransitionException.class)
	public ResponseEntity<ErrorResponse> handleInvalidTransition(InvalidJobStatusTransitionException exception) {
		return error(HttpStatus.CONFLICT, "INVALID_STATUS_TRANSITION", exception.getMessage());
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleUnexpected(Exception exception) {
		return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred");
	}

	private ResponseEntity<ErrorResponse> error(HttpStatus status, String error, String message) {
		ErrorResponse body = new ErrorResponse(status.value(), error, message, Instant.now());
		return ResponseEntity.status(status).body(body);
	}
}
