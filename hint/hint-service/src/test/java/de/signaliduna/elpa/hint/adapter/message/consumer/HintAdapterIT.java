package de.signaliduna.elpa.hint.adapter.message.consumer;

import de.signaliduna.elpa.hint.model.HintDto;
import de.signaliduna.elpa.hint.core.HintService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.InputDestination;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
	properties = {
		"AUTH_SERVICE_PASSWORD=somePassword",
		"com.c4-soft.springaddons.oidc.resourceserver.enabled=false"
	}
)
@ActiveProfiles("nodb")
@Import(TestChannelBinderConfiguration.class)
class HintAdapterIT{

	@Autowired
	InputDestination inputDestination;
	@Autowired
	OutputDestination outputDestination;
	@MockitoBean
	HintService hintServiceMock;

	@Test
	void shouldCallApplicationReceived() {
		var hintDto = new HintDto(
			"someSource",
			"someMessage",
			HintDto.Category.INFO,
			false,
			"someProcessId",
			LocalDateTime.of(2020, 12, 12, 12, 12),
			"1",
			"resourceId-1"
		);
		Message<HintDto> inputMessage = MessageBuilder.withPayload(hintDto).build();

		inputDestination.send(inputMessage, "elpa-hint-created");

		verify(hintServiceMock).saveHints(any());
	}
}
