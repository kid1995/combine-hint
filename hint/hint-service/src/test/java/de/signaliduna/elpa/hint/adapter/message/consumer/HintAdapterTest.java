package de.signaliduna.elpa.hint.adapter.message.consumer;

import de.signaliduna.elpa.hint.model.HintDto;
import de.signaliduna.elpa.hint.core.HintService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class HintAdapterTest {
	@InjectMocks
	HintAdapter hintAdapter;
	@Mock
	HintService hintService;

	@Test
	void handleHint() {
		// Given
		HintDto hintDto = new HintDto(
			"someSource",
			"someMessage",
			HintDto.Category.INFO,
			false,
			"someProcessId",
			LocalDateTime.of(2020, 12, 12, 12, 12),
			"1",
			"resourceId-1"
		);

		// When
		hintAdapter.hintCreated().accept(hintDto);

		// Then
		verify(hintService).saveHints(List.of(hintDto));
	}
}
