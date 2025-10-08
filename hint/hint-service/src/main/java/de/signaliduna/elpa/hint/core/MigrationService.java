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
import de.signaliduna.elpa.hint.model.HintDto;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
@Service
public class MigrationService {
	@Value("${migration.batch_size:100}")
	private int batchSize = 100;
	private final Logger log = LoggerFactory.getLogger(MigrationService.class);
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
				processPages(job, PageRequest.of(0, batchSize, Sort.by(Sort.Direction.ASC, "creationDate")));
				updateJobState(job, MigrationJobEntity.STATE.COMPLETED, "Migration completed successfully.");
			} catch (Exception e) {
				updateJobState(job, MigrationJobEntity.STATE.BROKEN, e.getMessage() + "\n" + Arrays.toString(e.getStackTrace()));
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
			updateJobState(newJob, MigrationJobEntity.STATE.BROKEN, e.getMessage() + "\n" + Arrays.toString(e.getStackTrace()));
		}
	}

	/**
	 * Validates a migration job based on specific criteria.
	 * This method checks:
	 * 1. If a job with the provided ID exists.
	 * 2. If the job's state is 'COMPLETED'.
	 * 3. If there are no unresolved migration errors linked to this job.
	 * 4. If the count of items successfully migrated to PostgresSQL matches the total items recorded for the job.
	 * @param jobId The ID of the migration job to validate.
	 * @return A ValidationResult object indicating success or failure and a descriptive message.
	 */

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
		if (postgresCount != job.getTotalItems()) {
			return new ValidationResult(false, "Item count mismatch. Expected: " + job.getTotalItems() + ", Found in PostgreSQL: " + postgresCount);
		}
		return new ValidationResult(true, "Migration job " + jobId + " validated successfully.");
	}

	// Counts the total number of hints in MongoDB based on the provided start and end dates.
	public long countMongoHints(LocalDateTime dataSetStartDate, LocalDateTime dataSetStopDate) {
		Query countQuery = createQueryByStartAndEndDate(dataSetStartDate, dataSetStopDate, Optional.empty());
		return mongoTemplate.count(countQuery, HintDao.class);
	}

	private void processPages(MigrationJobEntity job, Pageable pageable) {
    // Create a query that includes pagination for fetching the current page's data.
    Query queryForPage = createQueryByStartAndEndDate(job.getDataSetStartDate(), job.getDataSetStopDate(), Optional.of(pageable));

		String collectionName = mongoTemplate.getCollectionName(HintDao.class);
		List<Document> rawHints = mongoTemplate.find(queryForPage, Document.class, collectionName);

		List<HintDao> hints = new java.util.ArrayList<>();
		for (Document rawHint : rawHints) {
			// check missing _id
			String mongoId = rawHint.get("_id") != null ? rawHint.get("_id").toString() : "UNKNOWN_MONGO_ID";
			try {
				// Attempt to convert each raw document to HintDao
				HintDao hintDao = mongoTemplate.getConverter().read(HintDao.class, rawHint);
				hints.add(hintDao);
			} catch (Exception e) {
				// Log and save convert error
				logAndSaveError(job, "Failed to parse HintDao from MongoDB document with _id: " + mongoId + ". Error: " + e.getMessage(), mongoId);
				log.warn("Parsing error for document with _id: {}. \n Error: {}", mongoId, e.getMessage(), e);
			}
		}

    Page<HintDao> page = new PageImpl<>(hints, pageable, job.getTotalItems());

    log.info("Processing page {}/{} (size {}). Found {} hints for job {}",
            page.getNumber() + 1, page.getTotalPages(), page.getSize(), hints.size(), job.getId());
		for (HintDao hintDao : page.getContent()) {
			processSingleHint(job, hintDao.id(), Optional.of(hintDao), Optional.empty());
		}

    job.setProcessedItems(job.getProcessedItems() + hints.size());
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
			job.setLastMergedPoint(mongoId);

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
	private Query createQueryByStartAndEndDate(LocalDateTime dataSetStartDate, LocalDateTime dataSetStopDate, Optional<Pageable> pageable) {
		Query query = new Query();

		query.addCriteria(Criteria.where("hintSource").ne(null))
			.addCriteria(Criteria.where("hintTextOriginal").ne(null))
			.addCriteria(Criteria.where("hintCategory").in(List.of(HintDto.Category.values())).ne(null))
			.addCriteria(Criteria.where("processId").ne(null));

		Criteria dateCriteria = Criteria.where("creationDate").ne(null);
		if (dataSetStartDate != null  ) {
			dateCriteria.gte(dataSetStartDate);
		}
		if(dataSetStopDate != null){
			dateCriteria.lte(dataSetStopDate);
		}
		query.addCriteria(dateCriteria);
		pageable.ifPresent(query::with);
		return query;
	}
}
