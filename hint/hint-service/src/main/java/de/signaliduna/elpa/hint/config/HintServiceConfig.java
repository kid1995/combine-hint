package de.signaliduna.elpa.hint.config;

import de.signaliduna.elpa.hint.adapter.database.HintRepository;
import de.signaliduna.elpa.hint.core.HintService;
import de.signaliduna.elpa.hint.adapter.mapper.HintMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HintServiceConfig {

	@Bean
	public HintService hintService(
		HintRepository hintRepository,
		HintMapper hintMapper
	) {
		return new HintService(
			hintRepository,
			hintMapper);
	}
}
