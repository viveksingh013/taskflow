package com.taskflow.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "taskflow")
public class TaskflowProperties {

	private final Scheduler scheduler = new Scheduler();
	private final Http http = new Http();

	public Scheduler getScheduler() {
		return scheduler;
	}

	public Http getHttp() {
		return http;
	}

	public static class Scheduler {

		private boolean enabled = true;
		private long fixedDelay = 1000;

		public boolean isEnabled() {
			return enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public long getFixedDelay() {
			return fixedDelay;
		}

		public void setFixedDelay(long fixedDelay) {
			this.fixedDelay = fixedDelay;
		}
	}

	public static class Http {

		private Duration connectTimeout = Duration.ofSeconds(2);
		private Duration readTimeout = Duration.ofSeconds(5);

		public Duration getConnectTimeout() {
			return connectTimeout;
		}

		public void setConnectTimeout(Duration connectTimeout) {
			this.connectTimeout = connectTimeout;
		}

		public Duration getReadTimeout() {
			return readTimeout;
		}

		public void setReadTimeout(Duration readTimeout) {
			this.readTimeout = readTimeout;
		}
	}
}
