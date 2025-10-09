package de.signaliduna.elpa.hint.core;

import de.signaliduna.elpa.hint.adapter.database.HintRepository;
import de.signaliduna.elpa.hint.adapter.database.MigrationErrorRepo;
import de.signaliduna.elpa.hint.adapter.database.MigrationJobRepo;
import de.signaliduna.elpa.hint.adapter.database.legacy.model.HintDao;
import de.signaliduna.elpa.hint.adapter.database.model.HintEntity;
import de.signaliduna.elpa.hint.adapter.database.model.MigrationErrorEntity;
import de.signaliduna.elpa.hint.adapter.database.model.MigrationJobEntity;
import de.signaliduna.elpa.hint.adapter.mapper.HintMapper;
import de.signaliduna.elpa.hint.model.HintDto;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
	private static final int BATCH_SIZE = 100;
	private static final String CREATION_DATE_KEY ="creationDate";
	private static final Pageable FIRST_PAGE_REQUEST = PageRequest.of(0, BATCH_SIZE, Sort.by(Sort.Direction.ASC, CREATION_DATE_KEY));
	private static final String ERROR_STRING_FORMAT = "%s%n%s";

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
				processMigration(job, FIRST_PAGE_REQUEST);
				updateJobState(job, MigrationJobEntity.STATE.COMPLETED, "Migration completed successfully.");
			} catch (Exception e) {
				updateJobState(job, MigrationJobEntity.STATE.BROKEN, String.format(ERROR_STRING_FORMAT, e.getMessage(), Arrays.toString(e.getStackTrace())));
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
			updateJobState(newJob, MigrationJobEntity.STATE.BROKEN, String.format(ERROR_STRING_FORMAT, e.getMessage(), Arrays.toString(e.getStackTrace())));
		}
	}

	@Async
	public void startValidation(MigrationJobEntity job) {
		CompletableFuture.supplyAsync(() -> {
			try {
				long totalItems = countMongoHints(job.getDataSetStartDate(), job.getDataSetStopDate());
				job.setTotalItems(totalItems);
				job.setProcessedItems(0L);
				migrationJobRepo.save(job);
				boolean successMigrate = processValidation(job, FIRST_PAGE_REQUEST);
				String validationJobMsg = successMigrate ? "Migration completed successfully" : "Migration failed";
				updateJobState(job, MigrationJobEntity.STATE.COMPLETED, validationJobMsg);
				return successMigrate;
			} catch (Exception e) {
				updateJobState(job, MigrationJobEntity.STATE.BROKEN, String.format(ERROR_STRING_FORMAT, e.getMessage(), Arrays.toString(e.getStackTrace())));
				return false;
			}
		});
	}

	// Counts the total number of hints in MongoDB based on the provided start and end dates.
	public long countMongoHints(LocalDateTime dataSetStartDate, LocalDateTime dataSetStopDate) {
		Query countQuery = createQueryByStartAndEndDate(dataSetStartDate, dataSetStopDate, Optional.empty());
		return mongoTemplate.count(countQuery, HintDao.class);
	}

	private void processMigration(MigrationJobEntity job, Pageable pageable) {
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
				logAndSaveError(job, String.format("Failed to parse HintDao from MongoDB document with _id: %s. Error: %s", mongoId, e.getMessage()), mongoId);
				log.warn("Parsing error for document with _id: {}. \n Error: {}", mongoId, e.getMessage(), e);
			}
		}

		Page<HintDao> page = new PageImpl<>(hints, pageable, job.getTotalItems());
		log.info("Processing page {} / {} (size {}). Found {} hints for job {}", page.getNumber() + 1, page.getTotalPages(), page.getSize(), hints.size(), job.getId());
		for (HintDao hintDao : page.getContent()) {
			processSingleHint(job, hintDao.id(), Optional.of(hintDao), Optional.empty());
		}

		job.setProcessedItems(job.getProcessedItems() + hints.size());
		migrationJobRepo.save(job);

		if (page.hasNext()) {
			processMigration(job, pageable.next());
		}
	}

	private boolean processValidation(MigrationJobEntity job, Pageable pageable) {
		Query mongoQuery = createQueryByStartAndEndDate(job.getDataSetStartDate(), job.getDataSetStopDate(), Optional.of(pageable));
		List<HintDao> hintDaos = mongoTemplate.find(mongoQuery, HintDao.class);
		Page<HintEntity> hintEntitysPage = hintRepository.findByCreationDateBetweenAndMongoUUIDIsNotNull(job.getDataSetStartDate(), job.getDataSetStopDate(), pageable);
		List<HintEntity> hintEntitys = hintEntitysPage.getContent();

		if (hintDaos.size() != hintEntitys.size()) {
			updateJobState(job, MigrationJobEntity.STATE.BROKEN, "Mismatch in number of elements between MongoDB and Postgres.");
			return false;
		}

		for (int i = 0; i < hintDaos.size() ; i++) {
				if(!compareHintAfterMigration(job, hintDaos.get(i), hintEntitys.get(i))) {
					return  false;
				}
		}
		job.setProcessedItems(job.getProcessedItems() + hintEntitys.size());
		migrationJobRepo.save(job);
		if(hintEntitysPage.hasNext()){
			return processValidation(job, pageable.next());
		}
		return  true;
	}

	private boolean compareHintAfterMigration(MigrationJobEntity job, HintDao hintDao, HintEntity hintEntity) {
		if (!hintDao.id().equals(hintEntity.getMongoUUID())) {
			String errorMsg = String.format("Id mismatch: Sort or Query from DBs are not identical - hintMongoId: %s, postgresMongoUUID: %s", hintDao.id(), hintEntity.getMongoUUID());
			logAndSaveError(job, errorMsg, hintDao.id());
			return false;
		}
		StringBuilder diffs = new StringBuilder();
		if (!hintDao.hintSource().equals(hintEntity.getHintSource())) {
			diffs.append("hintSource: Mongo='").append(hintDao.hintSource()).append("', Postgres='").append(hintEntity.getHintSource()).append("'; ");
		}
		if (!hintDao.hintTextOriginal().equals(hintEntity.getMessage())) {
			diffs.append("message: Mongo='").append(hintDao.hintTextOriginal()).append("', Postgres='").append(hintEntity.getMessage()).append("'; ");
		}
		if (!diffs.isEmpty()) {
			logAndSaveError(job, String.format("Field mismatches for mongo_uuid %s: %s", hintDao.id(), diffs), hintDao.id());
		} return true;

	}

	private void processSingleHint(MigrationJobEntity job, String mongoId, Optional<HintDao> optionalHintDao, Optional<MigrationErrorEntity> existingError) {
		try {
			if (hintRepository.existsByMongoUUID(mongoId)) {
				existingError.ifPresent(this::resolveError);
				return;
			}
			HintDao hintDao = optionalHintDao.orElseGet(() -> mongoTemplate.findById(mongoId, HintDao.class));
			if (hintDao == null) {
				logAndSaveError(job, String.format("Hint with mongoUUID %s not found in MongoDB.", mongoId), mongoId);
				return;
			}
			HintEntity hintEntity = hintMapper.dtoToEntity(hintMapper.daoToDto(hintDao));
			hintEntity.setMongoUUID(hintDao.id());
			hintRepository.save(hintEntity);
			existingError.ifPresent(this::resolveError);
		} catch (Exception e) {
			logAndSaveError(job, e.getMessage(), mongoId);
		}
	}

	private void logAndSaveError(MigrationJobEntity job, String message, String mongoId) {
		log.warn("[MIGRATION-ERROR]: job-id: {}, mongoId: {}, error: {}", job.getId(), mongoId, message);
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
		log.info("[MIGRATION-INFO]: job-id: {}, state: {}, msg: {}", job.getId(), state, message);
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

		Criteria dateCriteria = Criteria.where(CREATION_DATE_KEY).ne(null);
		if (dataSetStartDate != null) {
			dateCriteria.gte(dataSetStartDate);
		}
		if (dataSetStopDate != null) {
			dateCriteria.lte(dataSetStopDate);
		}
		query.addCriteria(dateCriteria);
		pageable.ifPresent(query::with);
		return query;
	}
}
