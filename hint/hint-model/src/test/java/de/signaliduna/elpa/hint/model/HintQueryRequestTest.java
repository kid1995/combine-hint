package de.signaliduna.elpa.hint.model;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HintQueryRequestTest {

	@Test
	void shouldReturnEmptyMap_whenAllFieldsAreNull() {
		HintQueryRequest request = new HintQueryRequest(null, null, null, null, null, null, null, null);

		Map<HintParams, Object> result = request.toQueryParams();

		assertThat(result).isEmpty();
	}

	@Test
	void shouldIncludeAllFields() {
		HintQueryRequest request = new HintQueryRequest(
			"PREFIX", "SOURCE", "OriginalText", "info",
			true, "PID", "1", "RID"
		);

		Map<HintParams, Object> result = request.toQueryParams();

		assertThat(result).containsEntry(HintParams.HINT_SOURCE_PREFIX, "PREFIX")
			.doesNotContainKey(HintParams.HINT_SOURCE)
			.containsEntry(HintParams.HINT_TEXT_ORIGINAL, "OriginalText")
			.containsEntry(HintParams.HINT_CATEGORY, "INFO")
			.containsEntry(HintParams.SHOW_TO_USER, true)
			.containsEntry(HintParams.PROCESS_ID, "PID")
			.containsEntry(HintParams.PROCESS_VERSION, "1")
			.containsEntry(HintParams.RESOURCE_ID, "RID");
	}

	@Test
	void shouldIncludeOnlyHintSource_whenNoPrefixProvided() {
		HintQueryRequest request = new HintQueryRequest(
			null, "SOURCE", null, null,
			null, null, null, null
		);

		Map<HintParams, Object> result = request.toQueryParams();
		assertThat(result).containsEntry(HintParams.HINT_SOURCE, "SOURCE").doesNotContainKey(HintParams.HINT_SOURCE_PREFIX);

	}
}


