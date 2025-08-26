package de.signaliduna.elpa.hint.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class HintDtoTest {

	private static final HintDto HINT_DTO = new HintDto("hintSource", "message",
		HintDto.Category.INFO, true, "processId",
		LocalDateTime.of(2024, 05, 20, 12, 0, 0, 0), "processVersion", "resourceId");

	@Test
	void testBuilder() {
		assertThat(HintDto.builder()
			.hintSource(HINT_DTO.hintSource())
			.message(HINT_DTO.message())
			.hintCategory(HINT_DTO.hintCategory())
			.showToUser(HINT_DTO.showToUser())
			.processId(HINT_DTO.processId())
			.creationDate(HINT_DTO.creationDate())
			.processVersion(HINT_DTO.processVersion())
			.resourceId(HINT_DTO.resourceId())
			.build()).isEqualTo(HINT_DTO);
	}

	@Test
	void testWithers() {
		assertThat(HintDto.builder().build()
			.withHintSource(HINT_DTO.hintSource())
			.withMessage(HINT_DTO.message())
			.withHintCategory(HINT_DTO.hintCategory())
			.withShowToUser(HINT_DTO.showToUser())
			.withProcessId(HINT_DTO.processId())
			.withCreationDate(HINT_DTO.creationDate())
			.withProcessVersion(HINT_DTO.processVersion())
			.withResourceId(HINT_DTO.resourceId())
		).isEqualTo(HINT_DTO);
	}
}
