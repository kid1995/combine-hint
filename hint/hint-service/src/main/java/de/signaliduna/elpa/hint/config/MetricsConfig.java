package de.signaliduna.elpa.hint.config;

import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

	/**
	 * Filter out Spring Security filter metrics.
	 * Alternative to deprecated YAML config:
	 * management.observations.spring.security.filterchains.enabled=false
	 */
	@Bean
	public MeterFilter securityFilterMeterFilter() {
		return MeterFilter.deny(id -> {
			String name = id.getName();

			// Deny all security-related metrics
			return name.contains("spring.security") ||
				name.contains("security.filterchain") ||
				name.contains("security.filter") ||
				name.contains("authentication") ||
				name.contains("authorization.filter");
		});
	}

	/**
	 * Filter Kafka metrics to only keep important ones.
	 */
	@Bean
	public MeterFilter kafkaMetricsFilter() {
		return MeterFilter.denyUnless(id -> {
			String name = id.getName();

			// Only keep essential Kafka metrics
			return name.equals("kafka.consumer.fetch.manager.records.consumed.rate") ||
				name.equals("kafka.consumer.fetch.manager.records.lag.max") ||
				name.equals("kafka.consumer.coordinator.commit.latency.avg") ||
				name.equals("kafka.consumer.coordinator.assigned.partitions") ||
				name.startsWith("spring.kafka.listener");
		});
	}

	/**
	 * Limit cardinality for HTTP metrics.
	 */
	@Bean
	public MeterFilter cardinalityLimitFilter() {
		return MeterFilter.maximumAllowableTags(
			"http.server.requests",
			"uri",
			100,
			MeterFilter.deny()
		);
	}

	/**
	 * Add common tags to all metrics.
	 */
	@Bean
	public MeterFilter commonTagsFilter(
		@Value("${SERVICE_NAME:hint-service}") String serviceName,
		@Value("${K8S_ENVIRONMENT_LABEL:dev}") String environment,
		@Value("${K8S_NAMESPACE_NAME:elpa-elpa4}") String namespace,
		@Value("${K8S_POD_NAME:unknown}") String podName) {
		return MeterFilter.commonTags(
			Tags.of(
				"application", serviceName,
				"environment", environment,
				"namespace", namespace,
				"pod", podName));
	}
}

