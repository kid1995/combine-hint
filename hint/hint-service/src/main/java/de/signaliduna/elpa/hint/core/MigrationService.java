package de.signaliduna.elpa.hint.core;

import de.signaliduna.elpa.hint.adapter.database.HintRepository;
import de.signaliduna.elpa.hint.adapter.database.MigrationErrorRepo;
import de.signaliduna.elpa.hint.adapter.database.MigrationJobRepo;
import de.signaliduna.elpa.hint.adapter.database.legacy.model.HintDao;
import de.signaliduna.elpa.hint.adapter.database.model.HintEntity;
import de.signaliduna.elpa.hint.adapter.database.model.MigrationErrorEntity;
import de.signaliduna.elpa.hint.adapter.database.model.MigrationJobEntity;
import de.signaliduna.elpa.hint.adapter.mapper.HintMapper;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Service
public class MigrationService {

	@Value("${migration.batch_size:100}")
	private int batchSize;

	private final MongoTemplate mongoTemplate;
	private final HintRepository hintRepository;
	private final MigrationJobRepo migrationJobRepo;
	private final MigrationErrorRepo migrationErrorRepo;
	private final HintMapper hintMapper;

	public MigrationService(MongoTemplate mongoTemplate, HintRepository hintRepository, MigrationJobRepo migrationJobRepo,
													MigrationErrorRepo migrationErrorRepo, HintMapper hintMapper) {
		this.mongoTemplate = mongoTemplate;
		this.hintRepository = hintRepository;
		this.migrationJobRepo = migrationJobRepo;
		this.migrationErrorRepo = migrationErrorRepo;
		this.hintMapper = hintMapper;
	}

	@Async
	public CompletableFuture<Long> startMigration(MigrationJobEntity job) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				processPages(job, PageRequest.of(0, batchSize));
				updateJobState(job, MigrationJobEntity.STATE.COMPLETED, "Migration completed successfully.");
			} catch (Exception e) {
				updateJobState(job, MigrationJobEntity.STATE.BROKEN, e.getMessage());
			}
			return job.getId();
		});
	}

	@Async
	public void fixUnresolvedErrors(MigrationJobEntity newJob, Long oldJobId) {
		try {
			List<MigrationErrorEntity> unresolvedErrors = migrationErrorRepo.findByJob_IdAndResolved(oldJobId, false);
			for (MigrationErrorEntity error : unresolvedErrors) {
				processSingleHint(newJob, error.getMongoUUID(), Optional.empty(), Optional.of(error));
			}
			updateJobState(newJob, MigrationJobEntity.STATE.COMPLETED, "Fix job completed.");
		} catch (Exception e) {
			updateJobState(newJob, MigrationJobEntity.STATE.BROKEN, e.getMessage());
		}
	}

	private void processPages(MigrationJobEntity job, Pageable pageable) {
		Query query = new Query().with(pageable);
		if (job.getDataSetStartDate() != null && job.getDataSetStopDate() != null) {
			query.addCriteria(Criteria.where("creationDate").gte(job.getDataSetStartDate()).lt(job.getDataSetStopDate()));
		}

		List<HintDao> hints = mongoTemplate.find(query, HintDao.class);
		Page<HintDao> page = new PageImpl<>(hints, pageable, mongoTemplate.count(query, HintDao.class));

		for (HintDao hintDao : page.getContent()) {
			processSingleHint(job, hintDao.id(), Optional.of(hintDao), Optional.empty());
		}

		if (page.hasNext()) {
			processPages(job, pageable.next());
		}
	}

	private void processSingleHint(MigrationJobEntity job, String mongoId, Optional<HintDao> optionalHintDao, Optional<MigrationErrorEntity> existingError) {
		try {
			if (hintRepository.existsByMongoUUID(mongoId)) {
				existingError.ifPresent(this::resolveError);
				return;
			}

			HintDao hintDao = optionalHintDao.orElseGet(() -> mongoTemplate.findById(mongoId, HintDao.class));

			if (hintDao == null) {
				logAndSaveError(job, "Hint with mongoUUID " + mongoId + " not found in MongoDB.", mongoId);
				return;
			}

			HintEntity hintEntity = hintMapper.dtoToEntity(hintMapper.daoToDto(hintDao));
			hintEntity.setMongoUUID(hintDao.id());
			hintRepository.save(hintEntity);

			existingError.ifPresent(this::resolveError);

		} catch (DataIntegrityViolationException e) {
			logAndSaveError(job, "Data integrity violation: " + e.getMessage(), mongoId);
		} catch (Exception e) {
			logAndSaveError(job, e.getMessage(), mongoId);
		}
	}

	private void logAndSaveError(MigrationJobEntity job, String message, String mongoId) {
		LoggerFactory.getLogger(MigrationService.class).info(message);
		migrationErrorRepo.save(MigrationErrorEntity.builder()
			.message(message)
			.mongoUUID(mongoId)
			.resolved(false)
			.job(job)
			.build());
	}

	private void resolveError(MigrationErrorEntity error) {
		error.setResolved(true);
		migrationErrorRepo.save(error);
	}

	private void updateJobState(MigrationJobEntity job, MigrationJobEntity.STATE state, String message) {
		job.setState(state);
		job.setMessage(message);
		job.setFinishingDate(LocalDateTime.now());
		migrationJobRepo.save(job);
	}
}
