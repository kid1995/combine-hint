package de.signaliduna.elpa.hint.http.client.config;

import de.signaliduna.elpa.hint.http.client.HintClient;
import de.signaliduna.elpa.hint.http.client.config.properties.HintClientProperties;
import de.signaliduna.elpa.jwtadapter.core.JwtAdapter;
import de.signaliduna.elpa.jwtadapter.core.JwtInterceptor;
import feign.Contract;
import feign.Logger;
import feign.codec.Decoder;
import feign.codec.Encoder;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("HintClientConfig Test")
class HintClientConfigTest {

	@Mock
	private Encoder encoder;

	@Mock
	private Decoder decoder;

	@Mock
	private Contract contract;

	@Mock
	private JwtAdapter jwtAdapter;

	@Mock
	private JwtInterceptor jwtInterceptor;

	@Mock
	private ObjectProvider<MeterRegistry> meterRegistryProvider;

	private HintClientConfig classUnderTest;

	@BeforeEach
	void setUp() {
		HintClientProperties hintClientProperties = new HintClientProperties();
		hintClientProperties.setUrl("http://localhost:8080");
		hintClientProperties.setConnectionTimeout(Duration.ofMillis(5000));
		hintClientProperties.setReadTimeout(Duration.ofMillis(5000));
		hintClientProperties.setFollowRedirects(false);
		hintClientProperties.setLogLevel(Logger.Level.BASIC);

		classUnderTest = new HintClientConfig(hintClientProperties);
	}

	@Nested
	@DisplayName("jwtInterceptor")
	class JwtInterceptorTests {

		@Test
		@DisplayName("should create JwtInterceptor bean")
		void shouldCreateJwtInterceptorBean() {
			JwtInterceptor result = classUnderTest.jwtInterceptor(jwtAdapter);

			assertThat(result).isNotNull();
		}
	}

	@Nested
	@DisplayName("hintClient")
	class HintClientTests {

		@Test
		@DisplayName("should create HintClient with MeterRegistry when available")
		void shouldCreateHintClientWithMeterRegistry() {
			MeterRegistry meterRegistry = new SimpleMeterRegistry();
			when(meterRegistryProvider.getIfAvailable()).thenReturn(meterRegistry);

			HintClient result = classUnderTest.hintClient(
				encoder,
				decoder,
				contract,
				jwtInterceptor,
				meterRegistryProvider
			);

			assertThat(result).isNotNull();
		}

		@Test
		@DisplayName("should create HintClient without MeterRegistry when not available")
		void shouldCreateHintClientWithoutMeterRegistry() {
			when(meterRegistryProvider.getIfAvailable()).thenReturn(null);

			HintClient result = classUnderTest.hintClient(
				encoder,
				decoder,
				contract,
				jwtInterceptor,
				meterRegistryProvider
			);

			assertThat(result).isNotNull();
		}

	}
}

