package de.signaliduna.elpa.hint.core;

import de.signaliduna.elpa.hint.adapter.database.HintRepository;
import de.signaliduna.elpa.hint.adapter.database.MigrationErrorRepo;
import de.signaliduna.elpa.hint.adapter.database.MigrationJobRepo;
import de.signaliduna.elpa.hint.adapter.database.model.MigrationErrorEntity;
import de.signaliduna.elpa.hint.adapter.database.model.HintEntity;
import de.signaliduna.elpa.hint.adapter.database.model.MigrationJobEntity;
import de.signaliduna.elpa.hint.adapter.mapper.HintMapper;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import de.signaliduna.elpa.hint.adapter.database.legacy.model.HintDao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.scheduling.annotation.Async;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;


public class MigrationService {

	private final MongoTemplate mongoTemplate;
	private final HintRepository hintRepository;
	private final MigrationJobRepo migrationJobRepo;
	private final MigrationErrorRepo migrationErrorRepo;
	private final HintMapper hintMapper;
	private final ObjectMapper objectMapper;


	public MigrationService(MongoTemplate mongoTemplate, HintRepository hintRepository, MigrationJobRepo migrationJobRepo, MigrationErrorRepo migrationErrorRepo, HintMapper hintMapper, ObjectMapper objectMapper) {
		this.mongoTemplate = mongoTemplate;
		this.hintRepository = hintRepository;
		this.migrationJobRepo = migrationJobRepo;
		this.migrationErrorRepo = migrationErrorRepo;
		this.hintMapper = hintMapper;
		this.objectMapper = objectMapper;
	}

	@Async
	public void startMigration(MigrationJobEntity job) {
		try {
			int BATCH_SIZE = 100;
			Pageable initialPageable = PageRequest.of(0, BATCH_SIZE);
			processPage(job, initialPageable);
			job.setState(MigrationJobEntity.STATE.COMPLETED);
		} catch (Exception e) {
			job.setState(MigrationJobEntity.STATE.BROKEN);
			job.setMessage(e.getMessage());
		} finally {
			job.setFinishingDate(LocalDateTime.now());
			migrationJobRepo.save(job);
		}
		CompletableFuture.completedFuture(job.getId());
	}

	private void processPage(MigrationJobEntity job, Pageable pageable) {
		Query query = new Query().with(pageable);
		if (job.getDataSetStartDate() != null && job.getDataSetStopDate() != null) {
			query.addCriteria(Criteria.where("creationDate")
				.gte(job.getDataSetStartDate())
				.lt(job.getDataSetStopDate()));
		}

		List<HintDao> hints = mongoTemplate.find(query, HintDao.class);
		Page<HintDao> page = new PageImpl<>(hints, pageable, mongoTemplate.count(query, HintDao.class));

		for (HintDao hintDao : page.getContent()) {
			try {
				HintEntity hintEntity = hintMapper.dtoToEntity(hintMapper.daoToDto(hintDao));
				hintEntity.setMongoUUID(hintDao.id());
				hintRepository.save(hintEntity);
			} catch (Exception e) {
				String serializedMessage = trySerialize(objectMapper, hintDao, e.getMessage());
				migrationErrorRepo.save(MigrationErrorEntity.builder()
					.message(serializedMessage)
					.mongoUUID(hintDao.id())
					.build());
			}
		}

		if (page.hasNext()) {
			// recursive call
			processPage(job, pageable.next());
		}
	}

	private String trySerialize(ObjectMapper objectMapper, Object value, String fallback) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (Exception ignored) {
			return fallback;
		}
	}

}
