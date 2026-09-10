package com.taskflow.http;

public record HttpExecutionResult(boolean success, Integer statusCode, String errorMessage) {

	public static HttpExecutionResult success(int statusCode) {
		return new HttpExecutionResult(true, statusCode, null);
	}

	public static HttpExecutionResult failure(Integer statusCode, String errorMessage) {
		return new HttpExecutionResult(false, statusCode, errorMessage);
	}
}
