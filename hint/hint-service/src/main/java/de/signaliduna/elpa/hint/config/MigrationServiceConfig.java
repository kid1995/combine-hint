package de.signaliduna.elpa.hint.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.signaliduna.elpa.hint.adapter.database.HintRepository;
import de.signaliduna.elpa.hint.adapter.database.MigrationErrorRepo;
import de.signaliduna.elpa.hint.adapter.database.MigrationJobRepo;
import de.signaliduna.elpa.hint.adapter.mapper.HintMapper;
import de.signaliduna.elpa.hint.core.MigrationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;


@Configuration
public class MigrationServiceConfig {

	@Bean
	public MigrationService migrationService(
		MongoTemplate mongoTemplate,
		HintRepository hintRepository,
		MigrationJobRepo migrationJobRepo,
		MigrationErrorRepo migrationErrorRepo,
		HintMapper hintMapper,
		ObjectMapper objectMapper
	) {
		return new MigrationService(
			mongoTemplate,
			hintRepository,
			migrationJobRepo,
			migrationErrorRepo,
			hintMapper,
			objectMapper);
	}
}
