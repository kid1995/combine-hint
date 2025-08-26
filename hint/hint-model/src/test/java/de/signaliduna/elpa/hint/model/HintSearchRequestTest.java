package de.signaliduna.elpa.hint.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HintSearchRequestTest {

	private static final HintSearchRequest HINT_SEARCH_REQUEST = new HintSearchRequest(
		"hintSource", "hintTextOriginal", "hintCategory",
		true, "processId", "processVersion", "resourceId"
	);

	@Test
	void testBuilder() {
		assertThat(HintSearchRequest.builder()
			.hintSource("hintSource").hintTextOriginal("hintTextOriginal")
			.hintCategory("hintCategory").showToUser(true).processId("processId").processVersion("processVersion").resourceId("resourceId")
			.build()
		).isEqualTo(HINT_SEARCH_REQUEST);
	}
}
