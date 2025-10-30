package de.signaliduna.elpa.hint.adapter.http.api;

import de.signaliduna.elpa.hint.core.HintService;
import de.signaliduna.elpa.hint.model.HintDto;
import de.signaliduna.elpa.hint.model.HintParams;
import de.signaliduna.elpa.hint.model.HintQueryRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HintApiTest {
	private HintApi classUnderTest;
	@Mock
	private HintService hintServiceMock;

	@BeforeEach
	void setup() {
		this.classUnderTest = new HintApi(hintServiceMock);
	}

	private HintDto createHintDTO() {
		return new HintDto("ELISA", "Test1", HintDto.Category.BLOCKER, true,
			"E1234", LocalDateTime.of(2023, 5, 22, 9, 1, 2), "1", "resourceId-1");
	}

	@Test
	void shouldReturnHintsAsDtoFromService() {
		HintDto hintDTO = createHintDTO();
		when(hintServiceMock.getHints(any())).thenReturn(List.of(hintDTO));
		HintQueryRequest request = new HintQueryRequest(
			null, "ELISA", "Test1", "BLOCKER",
			true, "E1234", "1", "resourceId-1"
		);
		ResponseEntity<List<HintDto>> result = classUnderTest.getHints(request);
		List<HintDto> hintDtoList = result.getBody();
		assertThat(hintDtoList).containsExactly(createHintDTO());
		verify(hintServiceMock).getHints(request.toQueryParams());
	}

	@Test
	void shouldBuildEmptyQueryListIfNoQueryParamsProvided() {
		HintDto hintDTO = createHintDTO();
		HintQueryRequest request = new HintQueryRequest(
			null, null, null, null,
			null, null, null, null
		);
		when(hintServiceMock.getHints(Map.of())).thenReturn(List.of(hintDTO));
		classUnderTest.getHints(request);
		verify(hintServiceMock).getHints(Map.of());
	}

	@Test
	void shouldBuildFullQueryListIfAllParamsProvided() {
		HintDto hintDTO = createHintDTO();
		HintQueryRequest request = new HintQueryRequest(
			null, "ELISA", "Test1", "BLOCKER",
			true, "E1234", "1", "resourceId-1"
		);
		Map<HintParams, Object> expectedParams = request.toQueryParams();
		when(hintServiceMock.getHints(expectedParams)).thenReturn(List.of(hintDTO));
		classUnderTest.getHints(request);
		verify(hintServiceMock).getHints(expectedParams);
	}

	@Test
	void shouldUseHintSourcePrefixIfProvided() {
		HintDto hintDTO = createHintDTO();
		HintQueryRequest request = new HintQueryRequest(
			"EL", null, null, null,
			null, null, null, null
		);
		Map<HintParams, Object> expectedParams = request.toQueryParams();
		when(hintServiceMock.getHints(expectedParams)).thenReturn(List.of(hintDTO));
		ResponseEntity<List<HintDto>> result = classUnderTest.getHints(request);
		List<HintDto> hintDtoList = result.getBody();
		assertThat(hintDtoList).containsExactly(createHintDTO());
		verify(hintServiceMock).getHints(expectedParams);
	}

	@Test
	void shouldIgnoreHintSourceIfPrefixIsPresent() {
		HintDto hintDTO = createHintDTO();
		HintQueryRequest request = new HintQueryRequest(
			"EL", "SHOULD_BE_IGNORED", null, null,
			null, null, null, null
		);
		Map<HintParams, Object> expectedParams = request.toQueryParams();
		when(hintServiceMock.getHints(expectedParams)).thenReturn(List.of(hintDTO));
		ResponseEntity<List<HintDto>> result = classUnderTest.getHints(request);
		List<HintDto> hintDtoList = result.getBody();
		assertThat(hintDtoList).containsExactly(createHintDTO());
		verify(hintServiceMock).getHints(expectedParams);
	}
}


