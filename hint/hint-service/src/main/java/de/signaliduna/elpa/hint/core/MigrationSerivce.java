package de.signaliduna.elpa.hint.core;

import de.signaliduna.elpa.hint.adapter.database.HintRepository;
import de.signaliduna.elpa.hint.adapter.database.MigrationErrorRepo;
import de.signaliduna.elpa.hint.adapter.database.MigrationJobRepo;
import de.signaliduna.elpa.hint.adapter.database.model.MigrationJobEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.scheduling.annotation.Async;

import java.time.LocalDateTime;

public class MigrationSerivce {
	private static final Logger log = LoggerFactory.getLogger(MigrationSerivce.class);
	private static final int BATCH_SIZE = 100;

	private MongoTemplate mongoTemplate;
	private HintRepository hintRepository;
	private MigrationJobRepo migrationJobRepo;
	private MigrationErrorRepo migrationErrorRepo;

	public  MigrationSerivce() {}

	public MigrationSerivce(MongoTemplate mongoTemplate, HintRepository hintRepository, MigrationJobRepo migrationJobRepo, MigrationErrorRepo migrationErrorRepo) {
		this.mongoTemplate = mongoTemplate;
		this.hintRepository = hintRepository;
		this.migrationJobRepo = migrationJobRepo;
		this.migrationErrorRepo = migrationErrorRepo;
	}

	@Async
	public Long startMigration(MigrationJobEntity job) {

		return job.getId();
	}
}
