package de.signaliduna.elpa.hint.adapter.http.api;

import com.c4_soft.springaddons.security.oauth2.test.annotations.WithMockAuthentication;
import com.c4_soft.springaddons.security.oauth2.test.webmvc.AutoConfigureAddonsWebmvcResourceServerSecurity;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.signaliduna.elpa.hint.adapter.database.HintRepository;
import de.signaliduna.elpa.hint.core.HintService;
import de.signaliduna.elpa.hint.model.HintDto;
import de.signaliduna.elpa.hint.config.WebSecurityConfig;
import de.signaliduna.elpa.hint.model.HintParams;
import de.signaliduna.elpa.hint.util.AbstractSingletonContainerTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.Resource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = HintApi.class, properties = "authorization.users=S12345")
@MockitoBean(types = HintRepository.class)
@AutoConfigureAddonsWebmvcResourceServerSecurity
@Import({WebSecurityConfig.class})
class HintApiIT extends AbstractSingletonContainerTest {
	public static final String AUTHORIZED_USER = "S12345";

	@Autowired
	MockMvc mockMvc;
	@Autowired
	WebSecurityConfig webSecurityConfig;
	@Autowired
	ObjectMapper objectMapper;
	@Value("classpath:HintDtos.json")
	private Resource hintDtosJson;
	@Value("classpath:HintDto.json")
	private Resource hintDtoJson;
	@MockitoBean
	private HintService hintService;

	@Test
	void getHintsWithoutJWT() throws Exception {
		mockMvc.perform(get("/hints"))
			.andExpect(status().isUnauthorized())
			.andReturn();
	}

	@Test
	@WithMockAuthentication(name = "not " + AUTHORIZED_USER)
	void getHintsWithoutWrongSubject() throws Exception {
		mockMvc.perform(get("/hints"))
			.andExpect(status().isForbidden())
			.andReturn();
	}

	@Test
	@WithMockAuthentication(name = AUTHORIZED_USER)
	void getHints() throws Exception {
		List<HintDto> hints = List.of(
			new HintDto("ELISA", "Test1",  HintDto.Category.BLOCKER, true,
				 "E1234", getTestLocalDateTime(), "1", "resourceId-1"),
			new HintDto("ELISA", "Test2",  HintDto.Category.INFO, false,
				 "E5678", getTestLocalDateTime(), "1", "resourceId-1"),
			new HintDto("ELISA", "Test2", HintDto.Category.INFO, false,
				 "E5678", getTestLocalDateTime(), "1", "resourceId-1")
		);
		Map<HintParams, Object> queryParams = Map.of();
		when(hintService.getHints(queryParams)).thenReturn(hints);

		MvcResult mvcResult = mockMvc.perform(get("/hints"))
			.andExpect(status().isOk())
			.andReturn();

		assertThat(mvcResult.getResponse().getContentAsString()).isEqualTo(hintDtosJson.getContentAsString(StandardCharsets.UTF_8).replaceAll("\\s+", ""));
		verify(hintService).getHints(queryParams);
	}

	@Test
	@WithMockAuthentication(name = AUTHORIZED_USER)
	void getHintsByQuery() throws Exception {
		List<HintDto> hints = List.of(
			new HintDto("ELISA", "Test1",  HintDto.Category.BLOCKER, true,
				 "E1234", getTestLocalDateTime(), "1", "resourceId-1"),
			new HintDto("ELISA", "Test2",  HintDto.Category.INFO, false,
				 "E5678", getTestLocalDateTime(), "1", "resourceId-1"),
			new HintDto("ELISA", "Test2",  HintDto.Category.INFO, false,
				 "E5678", getTestLocalDateTime(), "1", "resourceId-1")
		);
		Map<HintParams, Object> queryParams = Map.of(
			HintParams.HINT_SOURCE, "ELISA",
			HintParams.RESOURCE_ID, "resourceId-1"
		);
		when(hintService.getHints(queryParams)).thenReturn(hints);

		MvcResult mvcResult = mockMvc.perform(get("/hints")
				.queryParam("hintSource", "ELISA")
					.queryParam("resourceId", "resourceId-1")
				)
			.andExpect(status().isOk())
			.andReturn();

		assertThat(mvcResult.getResponse().getContentAsString()).isEqualTo(hintDtosJson.getContentAsString(StandardCharsets.UTF_8).replaceAll("\\s+", ""));
		verify(hintService).getHints(queryParams);
	}

	@Test
	@WithMockAuthentication(name = AUTHORIZED_USER)
	void getHintsNoMatchToQuery() throws Exception {
		Map<HintParams, Object> queryParams = Map.of(
			HintParams.HINT_SOURCE, "ELISA",
			HintParams.HINT_TEXT_ORIGINAL, "Test1",
			HintParams.HINT_CATEGORY, "INFO",
			HintParams.SHOW_TO_USER, true,
			HintParams.PROCESS_ID, "E12345678");
		when(hintService.getHints(queryParams)).thenReturn(List.of());

		MvcResult mvcResult = mockMvc.perform(get("/hints")
				.queryParam("hintSource", "ELISA")
				.queryParam("hintTextOriginal", "Test1")
				.queryParam("hintCategory", "INFO")
				.queryParam("showToUser", "true")
				.queryParam("processId", "E12345678"))
			.andExpect(status().isOk())
			.andReturn();

		assertThat(mvcResult.getResponse().getContentAsString()).isEqualTo("[]");
		verify(hintService).getHints(queryParams);
	}

	@Test
	@WithMockAuthentication(name = AUTHORIZED_USER)
	void getHintById() throws Exception {
		final HintDto hint = new HintDto("ELISA", "Test",  HintDto.Category.BLOCKER, true,
			 "E1234", getTestLocalDateTime(), "1", "resourceId-1");
		when(hintService.getHintById(123L)).thenReturn(Optional.of(hint));

		MvcResult mvcResult = mockMvc.perform(get("/hints/123"))
			.andExpect(status().isOk())
			.andReturn();

		assertThat(mvcResult.getResponse().getContentAsString()).isEqualTo(getHintDtoJsonContentAsString());
	}

	@Test
	@WithMockAuthentication(name = AUTHORIZED_USER)
	void getHintByEmptyId() throws Exception {
		mockMvc.perform(get("/hints/"))
			.andExpect(status().isNotFound())
			.andReturn();
	}

	@Test
	@WithMockAuthentication(name = AUTHORIZED_USER)
	void getHintByIdNotFound() throws Exception {
		when(hintService.getHintById(123L)).thenReturn(Optional.empty());

		MvcResult mvcResult = mockMvc.perform(get("/hints/123"))
			.andExpect(status().isNotFound())
			.andReturn();

		assertThat(mvcResult.getResponse().getContentAsString()).isEmpty();
	}

	@Test
	@WithMockAuthentication(name = AUTHORIZED_USER)
	void saveHints() throws Exception {
		// Dieser Test wird wenn die Validierung wieder aktiv ist zu einem Fehler führen.
		// In dem Fall einen zusätzlichen Test schreiben, welches die Validierung prüft
		final String hintDtosJsonContentAsString = hintDtosJson.getContentAsString(StandardCharsets.UTF_8).replaceAll("\\s+", "");
		MvcResult mvcResult = mockMvc.perform(post("/hints")
				.content(hintDtosJsonContentAsString)
				.contentType("application/json"))
			.andExpect(status().isCreated())
			.andReturn();

		assertThat(mvcResult.getResponse().getContentAsString()).isEqualTo(hintDtosJsonContentAsString);
		List<HintDto> expectedHintDtos = List.of(
			new HintDto("ELISA", "Test1",  HintDto.Category.BLOCKER, true
				,  "E1234", getTestLocalDateTime(), "1", "resourceId-1"),
			new HintDto("ELISA", "Test2",  HintDto.Category.INFO, false,
				 "E5678", getTestLocalDateTime(), "1", "resourceId-1"),
			new HintDto("ELISA", "Test2", HintDto.Category.INFO, false,
				 "E5678", getTestLocalDateTime(), "1", "resourceId-1")
		);
		verify(hintService).saveHints(expectedHintDtos);
	}

	@Test
	@WithMockAuthentication(name = AUTHORIZED_USER)
	void saveValidHintsJson() throws Exception {
		final var hintDtosJsonContentAsString = """
			[{"hintSource":"ELISA","message":"TestMessage", "hintCategory":"INFO","showToUser":true,"processId":"12345","creationDate":[2024,5,7,12,1],"processVersion":"1","resourceId":"resourceId-1"}]
			""".stripTrailing();

		mockMvc.perform(post("/hints")
				.content(hintDtosJsonContentAsString)
				.contentType("application/json"))
			.andExpect(status().isCreated())
			.andReturn();

		verify(hintService).saveHints(List.of(new HintDto("ELISA", "TestMessage", HintDto.Category.INFO, true,
			 "12345", getTestLocalDateTimeJSON(), "1", "resourceId-1")));
	}

	@Test
	@WithMockAuthentication(name = AUTHORIZED_USER)
	void shouldRejectSavingMoreThan100Hints() throws Exception {
		final String hintDtoJsonContentAsString = getHintDtoJsonContentAsString();
		HintDto hint = objectMapper.readValue(hintDtoJsonContentAsString, HintDto.class);
		List<HintDto> hints = IntStream.rangeClosed(1, 101).boxed().map(i -> hint).toList();
		MvcResult mvcResult = mockMvc.perform(post("/hints")
				.content(objectMapper.writeValueAsString(hints))
				.contentType("application/json"))
			.andExpect(status().isBadRequest())
			.andReturn();
		assertThat(mvcResult.getResponse().getContentAsString()).isEqualTo("""
			{"message":"Request invalid","reason":"saveHints.hintDtos: List size must be between 0 and 100"}""");
	}

	@Test
	@WithMockAuthentication(name = "S12345")
	void shouldSaveHintsEvenWhenFieldIdSourceIsNull() throws Exception {
		HintDto hintDto = objectMapper.readValue(getHintDtoJsonContentAsString(), HintDto.class);
		mockMvc.perform(post("/hints")
				.content(objectMapper.writeValueAsString(List.of(hintDto)))
				.contentType("application/json"))
			.andExpect(status().isCreated())
			.andReturn();
	}

	private String getHintDtoJsonContentAsString() throws IOException {
		return hintDtoJson.getContentAsString(StandardCharsets.UTF_8).replaceAll("\\s+", "");
	}

	private LocalDateTime getTestLocalDateTime  () {
		return LocalDateTime.of(2023, 5, 22, 9, 1, 2);
	}
	private LocalDateTime getTestLocalDateTimeJSON() { return LocalDateTime.of(2024, 5, 7, 12, 1);}
}
