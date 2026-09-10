package com.taskflow.http;

import java.net.http.HttpClient;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.RequestBodySpec;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskflow.config.TaskflowProperties;
import com.taskflow.entity.Job;

@Component
public class HttpJobExecutor {

	private static final Logger log = LoggerFactory.getLogger(HttpJobExecutor.class);
	private static final Set<String> SUPPORTED_METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE");
	private static final Set<HttpMethod> METHODS_WITH_OPTIONAL_BODY = Set.of(
			HttpMethod.POST,
			HttpMethod.PUT,
			HttpMethod.PATCH
	);

	private final RestClient restClient;
	private final ObjectMapper objectMapper;

	@Autowired
	public HttpJobExecutor(TaskflowProperties properties) {
		this(buildRestClient(properties), new ObjectMapper());
	}

	HttpJobExecutor(RestClient restClient, ObjectMapper objectMapper) {
		this.restClient = restClient;
		this.objectMapper = objectMapper;
	}

	public HttpExecutionResult execute(Job job) {
		String methodName = job.getTargetMethod() == null ? "" : job.getTargetMethod().toUpperCase(Locale.ROOT);
		if (!SUPPORTED_METHODS.contains(methodName)) {
			return HttpExecutionResult.failure(null, "Unsupported HTTP method: " + job.getTargetMethod());
		}

		HttpMethod method = HttpMethod.valueOf(methodName);
		try {
			RequestBodySpec spec = restClient.method(method)
					.uri(job.getTargetUrl())
					.headers(headers -> applyHeaders(headers, job.getHeaders()));
			String body = requestBody(method, job.getBody());
			if (body != null) {
				spec = spec.contentType(MediaType.APPLICATION_JSON).body(body);
			}
			return spec.exchange((request, response) -> {
				int statusCode = response.getStatusCode().value();
				if (response.getStatusCode().is2xxSuccessful()) {
					return HttpExecutionResult.success(statusCode);
				}
				return HttpExecutionResult.failure(statusCode, "HTTP " + statusCode);
			});
		} catch (Exception exception) {
			log.warn("HTTP execution error: jobId={} targetUrl={}", job.getId(), job.getTargetUrl());
			return HttpExecutionResult.failure(null, exception.getMessage());
		}
	}

	private static RestClient buildRestClient(TaskflowProperties properties) {
		HttpClient httpClient = HttpClient.newBuilder()
				.connectTimeout(properties.getHttp().getConnectTimeout())
				.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(properties.getHttp().getReadTimeout());
		return RestClient.builder()
				.requestFactory(requestFactory)
				.build();
	}

	private static void applyHeaders(org.springframework.http.HttpHeaders headers, Map<String, String> storedHeaders) {
		if (storedHeaders == null) {
			return;
		}
		storedHeaders.forEach(headers::add);
	}

	private String requestBody(HttpMethod method, Map<String, Object> body) {
		if (!METHODS_WITH_OPTIONAL_BODY.contains(method) || body == null || body.isEmpty()) {
			return null;
		}
		try {
			return objectMapper.writeValueAsString(body);
		} catch (JsonProcessingException exception) {
			throw new IllegalArgumentException("Unable to serialize job body", exception);
		}
	}
}
