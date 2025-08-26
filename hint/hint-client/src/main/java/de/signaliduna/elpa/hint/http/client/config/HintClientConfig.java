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
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;
import org.springframework.cloud.openfeign.FeignClientsConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;


@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackages = "de.signaliduna.elpa.hint.http.client")
@ConfigurationPropertiesScan(basePackages = "de.signaliduna.elpa.hint.http.client.config.properties")
@Import(FeignClientsConfiguration.class)
@ImportAutoConfiguration({FeignAutoConfiguration.class, HttpMessageConvertersAutoConfiguration.class})
@EnableJwtAdapter
public class HintClientConfig {

	private final HintClientProperties hintClientProperties;

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
	public HintClient hintClient(Encoder encoder, Decoder decoder, Contract contract, JwtInterceptor jwtInterceptor, MeterRegistry registry) {
		return Feign.builder()
			.addCapability(new MicrometerCapability(registry))
			.encoder(encoder)
			.decoder(decoder)
			.contract(contract)
			.logger(new Slf4jLogger(HintClient.class))
			.logLevel(hintClientProperties.getLogLevel())
			.options(new Request.Options(
					hintClientProperties.getConnectionTimeout(),
					hintClientProperties.getReadTimeout(),
					hintClientProperties.isFollowRedirects()
				)
			).requestInterceptor(jwtInterceptor)
			.target(HintClient.class, hintClientProperties.getUrl());

	}
}
