package com.taskflow.http;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.taskflow.entity.Job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class HttpJobExecutorTest {

	private HttpServer server;
	private String baseUrl;
	private final AtomicInteger hits = new AtomicInteger();
	private final AtomicReference<String> lastMethod = new AtomicReference<>();
	private final AtomicReference<String> lastBody = new AtomicReference<>();
	private int responseStatus = 200;

	@BeforeEach
	void startServer() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/ok", exchange -> {
			hits.incrementAndGet();
			lastMethod.set(exchange.getRequestMethod());
			lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			byte[] response = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
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
	void twoXxIsSuccessAndSendsJsonBody() {
		HttpJobExecutor executor = new HttpJobExecutor(RestClient.create(), new ObjectMapper());
		Job job = new Job(
				"report",
				LocalDateTime.now(),
				baseUrl + "/ok",
				"POST",
				Map.of("X-Test", "1"),
				Map.of("reportType", "DAILY")
		);

		HttpExecutionResult result = executor.execute(job);

		assertTrue(result.success());
		assertEquals(200, result.statusCode());
		assertEquals("POST", lastMethod.get());
		assertTrue(lastBody.get().contains("DAILY"));
		assertEquals(1, hits.get());
	}

	@Test
	void non2xxIsFailure() {
		responseStatus = 503;
		HttpJobExecutor executor = new HttpJobExecutor(RestClient.create(), new ObjectMapper());
		Job job = new Job("fail", LocalDateTime.now(), baseUrl + "/ok", "GET", Map.of(), Map.of());

		HttpExecutionResult result = executor.execute(job);

		assertFalse(result.success());
		assertEquals(503, result.statusCode());
		assertEquals("HTTP 503", result.errorMessage());
	}

	@Test
	void connectionErrorIsFailure() {
		server.stop(0);
		HttpJobExecutor executor = new HttpJobExecutor(RestClient.create(), new ObjectMapper());
		Job job = new Job("down", LocalDateTime.now(), baseUrl + "/ok", "GET", Map.of(), Map.of());

		HttpExecutionResult result = executor.execute(job);

		assertFalse(result.success());
		assertNull(result.statusCode());
		assertTrue(result.errorMessage() != null && !result.errorMessage().isBlank());
	}

	@Test
	void getDoesNotSendBody() {
		HttpJobExecutor executor = new HttpJobExecutor(RestClient.create(), new ObjectMapper());
		Job job = new Job(
				"get",
				LocalDateTime.now(),
				baseUrl + "/ok",
				"GET",
				Map.of(),
				Map.of("shouldNotSend", true)
		);

		executor.execute(job);

		assertEquals("GET", lastMethod.get());
		assertTrue(lastBody.get() == null || lastBody.get().isBlank());
	}
}
