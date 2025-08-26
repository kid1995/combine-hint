package de.signaliduna.elpa.hint.http.client.config.properties;

import feign.Logger;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = HintClientProperties.PREFIX)
public class HintClientProperties {
	public static final String PREFIX = "elpa.hint.client";

	@NotBlank
	private String url;
	private Duration connectionTimeout = Duration.ofMillis(5000);
	private Duration readTimeout = Duration.ofMillis(5000);
	private boolean followRedirects = false;
	private Logger.Level logLevel = Logger.Level.BASIC;

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public Duration getConnectionTimeout() {
		return connectionTimeout;
	}

	public void setConnectionTimeout(Duration connectionTimeout) {
		this.connectionTimeout = connectionTimeout;
	}

	public Duration getReadTimeout() {
		return readTimeout;
	}

	public void setReadTimeout(Duration readTimeout) {
		this.readTimeout = readTimeout;
	}

	public boolean isFollowRedirects() {
		return followRedirects;
	}

	public void setFollowRedirects(boolean followRedirects) {
		this.followRedirects = followRedirects;
	}

	public Logger.Level getLogLevel() {
		return logLevel;
	}

	public void setLogLevel(Logger.Level logLevel) {
		this.logLevel = logLevel;
	}

}
