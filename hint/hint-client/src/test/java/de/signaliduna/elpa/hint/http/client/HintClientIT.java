package de.signaliduna.elpa.hint.http.client;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import de.signaliduna.elpa.hint.model.HintDto;
import de.signaliduna.elpa.hint.model.HintSearchRequest;
import de.signaliduna.elpa.jwtadapter.core.JwtAdapter;
import feign.FeignException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.wiremock.spring.EnableWireMock;
import tools.jackson.databind.json.JsonMapper;
import java.time.LocalDateTime;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ActiveProfiles("it")
@SpringBootTest(classes = {HintClient.class, HintClientIT.TestConfig.class},
	properties = "elpa.hint.client.url=http://localhost:${wiremock.server.port}")
@EnableWireMock()
@EnableHintClient
class HintClientIT {
	public static final String HINTS_ENDPOINT = "/api/hints";
	private static final String FAKE_JWT = "1234567";
	public static final String PROCESS_ID = "processId-value";
	public static final String PROCESS_VERSION = "processVersion-value";
	public static final String RESOURCE_ID = "resourceId-value";
	public static final HintDto HINT_DTO_TEST_DATA = HintDto.builder().hintSource("AppDetails").message("Test")
		.hintCategory(HintDto.Category.INFO).showToUser(true).processId(PROCESS_ID)
		.creationDate(LocalDateTime.of(2024, 5, 7, 12, 1, 0, 0))
		.processVersion(PROCESS_VERSION).resourceId(RESOURCE_ID).build();

	@MockitoBean
	JwtAdapter jwtAdapter;
	@Autowired
	JsonMapper jsonMapper;
	@Autowired
	HintClient hintClient;

	@BeforeEach
	void setup() {
		when(jwtAdapter.getJwt()).thenReturn(FAKE_JWT);
		WireMock.reset();
	}

	@Nested
	class getHintById {
		final String hintId = "myHintId";

		@Test
		void whenHintIsFound() {
			//given
			final var hintDtoJson = jsonMapper.writeValueAsString(HINT_DTO_TEST_DATA);
			stubFor(get(urlPathEqualTo(HINTS_ENDPOINT + "/" + hintId))
				.withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer " + FAKE_JWT))
				.willReturn(ResponseDefinitionBuilder.responseDefinition()
					.withStatus(HttpStatus.OK_200)
					.withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
					.withBody(hintDtoJson))
			);

			//when
			final ResponseEntity<HintDto> response = hintClient.getHintById(hintId);

			//then
			assertThat(response.getStatusCode().value()).isEqualTo(HttpStatus.OK_200);
			assertThat(response.getBody()).isEqualTo(HINT_DTO_TEST_DATA);
		}

		@Test
		void shouldThrowNotFoundWhenHintIsNotFound() {
			//given
			stubFor(get(urlPathEqualTo(HINTS_ENDPOINT + "/" + hintId))
				.withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer " + FAKE_JWT))
				.willReturn(ResponseDefinitionBuilder.responseDefinition()
					.withStatus(HttpStatus.NOT_FOUND_404))
			);

			//when / then
			assertThatThrownBy(() -> hintClient.getHintById(hintId))
				.isInstanceOf(FeignException.NotFound.class).hasMessageContaining("[404 Not Found] during [GET]");
		}

		@Test
		void shouldThrowUnauthorizedExceptionForUnauthorizedRequest() {
			//given
			stubFor(get(urlPathEqualTo(HINTS_ENDPOINT + "/" + hintId))
				.willReturn(ResponseDefinitionBuilder.responseDefinition()
					.withStatus(HttpStatus.UNAUTHORIZED_401))
			);

			//when
			assertThatThrownBy(() -> hintClient.getHintById(hintId)).isInstanceOf(FeignException.Unauthorized.class).message().contains("401 Unauthorized");
		}
	}

	@Nested
	class searchHints {

		@Test
		void happyPath_byProcessId() {
			//given
			final var hintDtoListJsonString = jsonMapper.writeValueAsString(List.of(HINT_DTO_TEST_DATA));
			assertThat(hintDtoListJsonString).isEqualTo("""
				[{"hintSource":"AppDetails","message":"Test","hintCategory":"INFO","showToUser":true,"processId":"processId-value","creationDate":"2024-05-07T12:01:00","processVersion":"processVersion-value","resourceId":"resourceId-value"}]
				""".stripTrailing());
			stubFor(get(urlPathEqualTo(HINTS_ENDPOINT))
				.withQueryParam("processId", equalTo(PROCESS_ID))
				.withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer " + FAKE_JWT))
				.willReturn(ResponseDefinitionBuilder.responseDefinition()
					.withStatus(HttpStatus.OK_200)
					.withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
					.withBody(hintDtoListJsonString))
			);

			//when
			final ResponseEntity<List<HintDto>> response = hintClient.searchHints(HintSearchRequest.builder().processId(PROCESS_ID).build());

			//then
			assertThat(response.getStatusCode().value()).isEqualTo(HttpStatus.OK_200);
			assertThat(response.getBody()).isEqualTo(List.of(HINT_DTO_TEST_DATA));
		}

		@Test
		void happyPath_searchContainingAllOptionalFilterParams() {
			//given
			final var hintDtoListJsonString = jsonMapper.writeValueAsString(List.of(HINT_DTO_TEST_DATA));
			stubFor(get(urlPathEqualTo(HINTS_ENDPOINT))
				.withQueryParam("hintSource", equalTo("hintSource-value"))
				.withQueryParam("hintTextOriginal", equalTo("hintTextOriginal-value"))
				.withQueryParam("hintCategory", equalTo("hintCategory-value"))
				.withQueryParam("showToUser", equalTo("true"))
				.withQueryParam("processId", equalTo(PROCESS_ID))
				.withQueryParam("processVersion", equalTo(PROCESS_VERSION))
				.withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer " + FAKE_JWT))
				.willReturn(ResponseDefinitionBuilder.responseDefinition()
					.withStatus(HttpStatus.OK_200)
					.withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
					.withBody(hintDtoListJsonString))
			);

			//when
			final ResponseEntity<List<HintDto>> response = hintClient.searchHints(HintSearchRequest.builder()
				.hintSource("hintSource-value")
				.hintTextOriginal("hintTextOriginal-value")
				.hintCategory("hintCategory-value")
				.showToUser(true)
				.processId(PROCESS_ID)
				.processVersion(PROCESS_VERSION)
				.build());

			//then
			assertThat(response.getStatusCode().value()).isEqualTo(HttpStatus.OK_200);
			assertThat(response.getBody()).isEqualTo(List.of(HINT_DTO_TEST_DATA));
		}

		@Test
		void shouldThrowUnauthorizedExceptionForUnauthorizedRequest() {
			//given
			stubFor(get(urlPathEqualTo(HINTS_ENDPOINT))
				.withQueryParam("processId", equalTo(PROCESS_ID))
				.willReturn(ResponseDefinitionBuilder.responseDefinition()
					.withStatus(HttpStatus.UNAUTHORIZED_401))
			);

			//when
			assertThatThrownBy(() ->
				hintClient.searchHints(HintSearchRequest.builder().processId(PROCESS_ID).build())
			).isInstanceOf(FeignException.Unauthorized.class).message().contains("401 Unauthorized");
		}
	}

	@Nested
	class saveHints {

		@Test
		void happyPath() {
			//given
			final var hintDtoListJsonString = jsonMapper.writeValueAsString(List.of(HINT_DTO_TEST_DATA));
			stubFor(post(urlPathEqualTo(HINTS_ENDPOINT))
				.withRequestBody(equalToJson(hintDtoListJsonString))
				.withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer " + FAKE_JWT))
				.withHeader(HttpHeaders.CONTENT_TYPE, equalTo(MediaType.APPLICATION_JSON_VALUE))
				.willReturn(ResponseDefinitionBuilder.responseDefinition()
					.withStatus(HttpStatus.CREATED_201)
				)
			);

			//when
			final ResponseEntity<Void> response = hintClient.saveHints(List.of(HINT_DTO_TEST_DATA));

			//then
			assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.CREATED);
		}
	}

	@TestConfiguration
	public static class TestConfig {
		@Bean
		public JsonMapper jsonMapper() {
			return JsonMapper.builder().findAndAddModules().build();
		}
		@Bean
		public MeterRegistry meterRegistry() {
			return new SimpleMeterRegistry();
		}
	}
}
