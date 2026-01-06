package de.signaliduna.elpa.hint.http.client.config;

import de.signaliduna.elpa.hint.http.client.HintClient;
import de.signaliduna.elpa.hint.http.client.config.properties.HintClientProperties;
import de.signaliduna.elpa.jwtadapter.EnableJwtAdapter;
import de.signaliduna.elpa.jwtadapter.core.JwtAdapter;
import de.signaliduna.elpa.jwtadapter.core.JwtInterceptor;
import feign.Contract;
import feign.Feign;
import feign.Request;
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.micrometer.MicrometerCapability;
import feign.slf4j.Slf4jLogger;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;
import org.springframework.cloud.openfeign.FeignClientsConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.util.concurrent.TimeUnit;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackages = "de.signaliduna.elpa.hint.http.client")
@ConfigurationPropertiesScan(basePackages = "de.signaliduna.elpa.hint.http.client.config.properties")
@Import(FeignClientsConfiguration.class)
@ImportAutoConfiguration({FeignAutoConfiguration.class, HttpMessageConvertersAutoConfiguration.class})
@EnableJwtAdapter
public class HintClientConfig {

	private final HintClientProperties hintClientProperties;

	private static final Logger log = LoggerFactory.getLogger(HintClientConfig.class);

	public HintClientConfig(HintClientProperties hintClientProperties) {
		this.hintClientProperties = hintClientProperties;
	}

	@Bean
	@ConditionalOnMissingBean
	public JwtInterceptor jwtInterceptor(JwtAdapter jwtAdapter) {
		return new JwtInterceptor(jwtAdapter);
	}

	@Bean
	@ConditionalOnMissingBean
	public HintClient hintClient(Encoder encoder, Decoder decoder, Contract contract, JwtInterceptor jwtInterceptor, ObjectProvider<MeterRegistry> meterRegistryProvider) {

		Feign.Builder builder =  Feign.builder()
			.encoder(encoder)
			.decoder(decoder)
			.contract(contract)
			.logger(new Slf4jLogger(HintClient.class))
			.logLevel(hintClientProperties.getLogLevel())
			.options(new Request.Options(
					hintClientProperties.getConnectionTimeout().toMillis(),
					TimeUnit.MILLISECONDS,
					hintClientProperties.getReadTimeout().toMillis(),
					TimeUnit.MILLISECONDS,
					hintClientProperties.isFollowRedirects()
				)
			).requestInterceptor(jwtInterceptor);

		MeterRegistry meterRegistry = meterRegistryProvider.getIfAvailable();

		if(meterRegistry == null) {
			log.warn("No MeterRegistry for HintClient found - Metric will be disable");
		} else {
			builder.addCapability(new MicrometerCapability(meterRegistry));
		}
		return builder.target(HintClient.class, hintClientProperties.getUrl());
	}
}
