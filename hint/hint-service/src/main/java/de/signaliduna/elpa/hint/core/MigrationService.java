package de.signaliduna.elpa.hint.core;

import de.signaliduna.elpa.hint.adapter.database.HintRepository;
import de.signaliduna.elpa.hint.adapter.database.MigrationErrorRepo;
import de.signaliduna.elpa.hint.adapter.database.MigrationJobRepo;
import de.signaliduna.elpa.hint.adapter.database.legacy.model.HintDao;
import de.signaliduna.elpa.hint.adapter.database.model.HintEntity;
import de.signaliduna.elpa.hint.adapter.database.model.MigrationErrorEntity;
import de.signaliduna.elpa.hint.adapter.database.model.MigrationJobEntity;
import de.signaliduna.elpa.hint.adapter.mapper.HintMapper;
import de.signaliduna.elpa.hint.core.model.ValidationResult;
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
	private int batchSize = 100;

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
				long totalItems = countMongoHints(job.getDataSetStartDate(), job.getDataSetStopDate());
				job.setTotalItems(totalItems);
				job.setProcessedItems(0L);
				migrationJobRepo.save(job);
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

	public long countMongoHints(LocalDateTime dataSetStartDate, LocalDateTime dataSetStopDate) {
		Query countQuery = createQueryByStartAndEndDate(dataSetStartDate, dataSetStopDate);
		return mongoTemplate.count(countQuery, HintDao.class);
	}

	public ValidationResult validateMigration(Long jobId) {
		Optional<MigrationJobEntity> jobOptional = migrationJobRepo.findById(jobId);
		if (jobOptional.isEmpty()) {
			return new ValidationResult(false, "Job with ID " + jobId + " not found.");
		}
		MigrationJobEntity job = jobOptional.get();
		if (job.getState() != MigrationJobEntity.STATE.COMPLETED) {
			return new ValidationResult(false, "Job state is " + job.getState() + ", not COMPLETED.");
		}
		List<MigrationErrorEntity> unresolvedErrors = migrationErrorRepo.findByJob_IdAndResolved(jobId, false);
		if (!unresolvedErrors.isEmpty()) {
			return new ValidationResult(false, "Found " + unresolvedErrors.size() + " unresolved errors for this job.");
		}
		long postgresCount = hintRepository.countByMongoUUIDIsNotNull();
		if (job.getTotalItems() != null && postgresCount != job.getTotalItems()) {
			return new ValidationResult(false, "Item count mismatch. Expected: " + job.getTotalItems() + ", Found in PostgreSQL: " + postgresCount);
		}
		return new ValidationResult(true, "Migration job " + jobId + " validated successfully.");
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
			job.setProcessedItems(job.getProcessedItems() + 1);
		}
		migrationJobRepo.save(job);

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

	private Query createQueryByStartAndEndDate(LocalDateTime dataSetStartDate, LocalDateTime dataSetStopDate){
		Query countQuery = new Query();
		Criteria criteria = Criteria.where("creationDate");
		if (dataSetStartDate != null) {
			criteria.gte(dataSetStartDate);
		}
		if( dataSetStopDate != null){
			criteria.lte(dataSetStopDate);
		}
		if (dataSetStartDate != null || dataSetStopDate != null ){
			countQuery.addCriteria(criteria);
		}
		return countQuery;
	}

}
