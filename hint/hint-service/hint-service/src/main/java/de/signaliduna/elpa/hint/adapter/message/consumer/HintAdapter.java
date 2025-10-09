package de.signaliduna.elpa.hint.adapter.message.consumer;

import de.signaliduna.elpa.hint.model.HintDto;
import de.signaliduna.elpa.hint.core.HintService;
import io.github.springwolf.core.asyncapi.annotations.AsyncListener;
import io.github.springwolf.core.asyncapi.annotations.AsyncOperation;
import io.github.springwolf.bindings.kafka.annotations.KafkaAsyncOperationBinding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Consumer;

@Component
public class HintAdapter {
	private static final Logger log = LoggerFactory.getLogger(HintAdapter.class);
	private final HintService hintService;

	public HintAdapter(HintService hintService) {
		this.hintService = hintService;
	}

	@Bean
	@AsyncListener(operation = @AsyncOperation(
		channelName = "${topics.hintCreated}",
		description = "Received a created hint",
		payloadType = HintDto.class
	))
	@KafkaAsyncOperationBinding
	public Consumer<HintDto> hintCreated() {
		return hintDto -> {
			log.info("Saved hint: {} for process-id: {}", hintDto, hintDto.processId());
			this.hintService.saveHints(List.of(hintDto));
		};
	}
}
