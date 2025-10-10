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
import de.signaliduna.elpa.hint.util.HintTestDataGenerator;
import de.signaliduna.elpa.hint.util.MigrationTestDataGenerator;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.data.mongodb.core.query.Query;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MigrationService Test")
class MigrationServiceTest {
	@Mock
	private MongoTemplate mongoTemplate;
	@Mock
	private HintRepository hintRepository;
	@Mock
	private MigrationJobRepo migrationJobRepo;
	@Mock
	private MigrationErrorRepo migrationErrorRepo;
	@Spy
	private HintMapper hintMapper = Mappers.getMapper(HintMapper.class);
	@InjectMocks
	private MigrationService migrationService;
	private MigrationJobEntity testJob;

	@BeforeEach
	void setUp() {
		testJob = MigrationJobEntity.builder().id(1L).creationDate(LocalDateTime.now()).state(MigrationJobEntity.STATE.RUNNING).build();
	}

	@Nested
	@DisplayName("startMigration")
	class StartMigration {
		@Test
		@DisplayName("should migrate all hints successfully")
		void shouldMigrateAllHintsSuccessfully() throws ExecutionException, InterruptedException {
			// Given
			List<HintDao> hintDaos = List.of(
				HintTestDataGenerator.createHintDaoWithId("mongoId1"),
				HintTestDataGenerator.createHintDaoWithId("mongoId2")
			);

			List<Document> documents = hintDaos.stream().map(HintTestDataGenerator::createDocumentFromHintDao).toList();
			when(mongoTemplate.getCollectionName(HintDao.class)).thenReturn("Hint");
			when(mongoTemplate.find(any(Query.class), eq(Document.class), eq("Hint")))
				.thenReturn(documents)
				.thenReturn(Collections.emptyList());

			MongoConverter converter = mock(MongoConverter.class);
			when(mongoTemplate.getConverter()).thenReturn(converter);
			when(converter.read(eq(HintDao.class), any(Document.class)))
				.thenAnswer(invocation -> {
					Document doc = invocation.getArgument(1);
					return hintDaos.stream().filter(h -> h.id().equals(doc.get("_id").toString())).findFirst().orElse(null);
				});

			when(mongoTemplate.count(any(Query.class), eq(HintDao.class))).thenReturn((long) hintDaos.size());
			when(hintRepository.existsByMongoUUID(anyString())).thenReturn(false);
			// When
			CompletableFuture<Long> future = migrationService.startMigration(testJob);
			Long jobId = future.get();
			// Then
			assertThat(jobId).isEqualTo(testJob.getId());
			verify(hintRepository, times(2)).save(any(HintEntity.class));
			verify(migrationErrorRepo, never()).save(any());
			ArgumentCaptor<MigrationJobEntity> jobCaptor = ArgumentCaptor.forClass(MigrationJobEntity.class);
			verify(migrationJobRepo, atLeastOnce()).save(jobCaptor.capture());

			MigrationJobEntity finalJobState = jobCaptor.getAllValues().stream()
				.reduce((first, second) -> second).orElse(null);
			assertThat(finalJobState).isNotNull();
			assertThat(finalJobState.getState()).isEqualTo(MigrationJobEntity.STATE.COMPLETED);
		}

		@Test
		@DisplayName("should skip existing hints")
		void shouldSkipExistingHints() throws ExecutionException, InterruptedException {
			// Given
			HintDao existingHint = HintTestDataGenerator.createHintDaoWithId("existingId");
			when(mongoTemplate.getCollectionName(HintDao.class)).thenReturn("Hint");
			List<Document> documents = List.of(HintTestDataGenerator.createDocumentFromHintDao(existingHint));
			when(mongoTemplate.find(any(Query.class), eq(Document.class), eq("Hint")))
				.thenReturn(documents)
				.thenReturn(Collections.emptyList());

			MongoConverter converter = mock(MongoConverter.class);
			when(mongoTemplate.getConverter()).thenReturn(converter);
			when(converter.read(eq(HintDao.class), any(Document.class))).thenReturn(existingHint);

			when(mongoTemplate.count(any(Query.class), eq(HintDao.class))).thenReturn(1L);
			when(hintRepository.existsByMongoUUID(existingHint.id())).thenReturn(true);
			// When
			migrationService.startMigration(testJob).get();
			// Then
			verify(hintRepository, never()).save(any(HintEntity.class));
			verify(migrationErrorRepo, never()).save(any());
		}

		@Test
		@DisplayName("should log error on data integrity violation")
		void shouldLogErrorOnDataIntegrityViolation() throws ExecutionException, InterruptedException {
			// Given
			HintDao hintMongoDataWithNullProcessId = HintTestDataGenerator.createHintDaoWithId("DataIntegrityViolationID");

			when(mongoTemplate.getCollectionName(HintDao.class)).thenReturn("Hint");
			List<Document> documents = List.of(HintTestDataGenerator.createDocumentFromHintDao(hintMongoDataWithNullProcessId));
			when(mongoTemplate.find(any(Query.class), eq(Document.class), eq("Hint")))
				.thenReturn(documents)
				.thenReturn(Collections.emptyList());

			MongoConverter converter = mock(MongoConverter.class);
			when(mongoTemplate.getConverter()).thenReturn(converter);
			when(converter.read(eq(HintDao.class), any(Document.class))).thenReturn(hintMongoDataWithNullProcessId);


			when(mongoTemplate.count(any(Query.class), eq(HintDao.class))).thenReturn(1L);
			when(hintRepository.existsByMongoUUID(hintMongoDataWithNullProcessId.id())).thenReturn(false);
			when(hintRepository.save(any(HintEntity.class))).thenThrow(new DataIntegrityViolationException("Test Exception"));
			// When
			migrationService.startMigration(testJob).get();
			// Then
			verify(hintRepository, times(1)).save(any(HintEntity.class));
			ArgumentCaptor<MigrationErrorEntity> errorCaptor = ArgumentCaptor.forClass(MigrationErrorEntity.class);
			verify(migrationErrorRepo).save(errorCaptor.capture());
			assertThat(errorCaptor.getValue().getMongoUUID()).isEqualTo(hintMongoDataWithNullProcessId.id());
		}

		@Test
		@DisplayName("should handle general exception and set job to broken")
		void shouldHandleGeneralException() throws ExecutionException, InterruptedException {
			// Given
			when(mongoTemplate.count(any(Query.class), eq(HintDao.class))).thenThrow(new RuntimeException("Mongo is down"));
			// When
			migrationService.startMigration(testJob).get();
			// Then
			ArgumentCaptor<MigrationJobEntity> jobCaptor = ArgumentCaptor.forClass(MigrationJobEntity.class);
			verify(migrationJobRepo, atLeastOnce()).save(jobCaptor.capture());

			MigrationJobEntity finalJobState = jobCaptor.getAllValues().stream()
				.reduce((first, second) -> second).orElse(null);
			assertThat(finalJobState).isNotNull();
			assertThat(finalJobState.getState()).isEqualTo(MigrationJobEntity.STATE.BROKEN);
			assertThat(finalJobState.getMessage()).startsWith("Mongo is down");
		}

		@Test
		@DisplayName("should handle exception during migration and set job to broken")
		void shouldHandleExceptionDuringMigration() throws ExecutionException, InterruptedException {
			// Given
			when(mongoTemplate.count(any(Query.class), eq(HintDao.class))).thenThrow(new RuntimeException("Test exception"));
			// When
			CompletableFuture<Long> future = migrationService.startMigration(testJob);
			future.get();
			// Then
			ArgumentCaptor<MigrationJobEntity> jobCaptor = ArgumentCaptor.forClass(MigrationJobEntity.class);
			verify(migrationJobRepo, atLeastOnce()).save(jobCaptor.capture());

			MigrationJobEntity finalJobState = jobCaptor.getAllValues().stream()
				.reduce((first, second) -> second).orElse(null);
			assertThat(finalJobState).isNotNull();
			assertThat(finalJobState.getState()).isEqualTo(MigrationJobEntity.STATE.BROKEN);
			assertThat(finalJobState.getMessage()).startsWith("Test exception");
		}

		private static Stream<Arguments> provideDateRangeArguments() {
			LocalDateTime now = LocalDateTime.now().withNano(0); // Align with MongoDB's precision
			LocalDateTime yesterday = now.minusDays(1);
			return Stream.of(
				Arguments.of(yesterday, now, "Both dates present"),
				Arguments.of(yesterday, null, "Only start date present"),
				Arguments.of(null, now, "Only end date present"),
				Arguments.of(null, null, "No dates present")
			);
		}

		@ParameterizedTest
		@DisplayName("should filter by date range if provided")
		@MethodSource("provideDateRangeArguments")
		void shouldFilterByDateRange(LocalDateTime startDate, LocalDateTime stopDate, String testName) throws ExecutionException, InterruptedException {
			// Given
			testJob.setDataSetStartDate(startDate);
			testJob.setDataSetStopDate(stopDate);
			when(mongoTemplate.getCollectionName(HintDao.class)).thenReturn("Hint");
			when(mongoTemplate.find(any(Query.class), eq(Document.class), eq("Hint"))).thenReturn(Collections.emptyList());


			// When
			migrationService.startMigration(testJob).get();

			// Then
			ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
			verify(mongoTemplate, atLeast(1)).find(queryCaptor.capture(), eq(Document.class), eq("Hint"));
			Query capturedQuery = queryCaptor.getValue();
			// Spring Data MongoDB's Query.getQueryObject() returns a org.bson.Document
			org.bson.Document queryObject = capturedQuery.getQueryObject();

			// If at least one date is provided, the 'creationDate' filter should exist
			assertThat(queryObject).containsKey("creationDate");
			assertThat(queryObject.get("creationDate")).isInstanceOf(org.bson.Document.class);

			org.bson.Document creationDateFilter = (org.bson.Document) queryObject.get("creationDate");

			if (startDate != null) {
				// If startDate is provided, it should contain the $gte operator
				assertThat(creationDateFilter).containsEntry("$gte", startDate);
			} else {
				// If startDate is null, it should not contain the $gte operator
				assertThat(creationDateFilter).doesNotContainKey("$gte");
			}

			if (stopDate != null) {
				// If stopDate is provided, it should contain the $lte operator
				assertThat(creationDateFilter).containsEntry("$lte", stopDate);
			} else {
				// If stopDate is null, it should not contain the $lte operator
				assertThat(creationDateFilter).doesNotContainKey("$lte");
			}

		}

		@Test
		@DisplayName("should process multiple pages")
		void shouldProcessMultiplePages() throws ExecutionException, InterruptedException {
			// Given
			List<HintDao> page1Daos = HintTestDataGenerator.createMultipleHintDao(100);
			List<HintDao> page2Daos = List.of(HintTestDataGenerator.createHintDaoWithId("mongoId2"));
			long totalHintDaos = page1Daos.size() + page2Daos.size();

			List<Document> page1Docs = page1Daos.stream().map(HintTestDataGenerator::createDocumentFromHintDao).toList();
			List<Document> page2Docs = page2Daos.stream().map(HintTestDataGenerator::createDocumentFromHintDao).toList();

			when(mongoTemplate.getCollectionName(HintDao.class)).thenReturn("Hint");
			when(mongoTemplate.find(any(Query.class), eq(Document.class), eq("Hint")))
				.thenReturn(page1Docs)
				.thenReturn(page2Docs)
				.thenReturn(Collections.emptyList());

			MongoConverter converter = mock(MongoConverter.class);
			when(mongoTemplate.getConverter()).thenReturn(converter);

			// Mock converter for page 1
			for(int i = 0; i < page1Daos.size(); i++) {
				when(converter.read(HintDao.class, page1Docs.get(i))).thenReturn(page1Daos.get(i));
			}
			// Mock converter for page 2
			when(converter.read(HintDao.class, page2Docs.getFirst())).thenReturn(page2Daos.getFirst());


			when(mongoTemplate.count(any(Query.class), eq(HintDao.class))).thenReturn(totalHintDaos);
			// When
			migrationService.startMigration(testJob).get();
			// Then
			verify(hintRepository, times((int) (totalHintDaos))).save(any(HintEntity.class));
		}
	}

	@Nested
	@DisplayName("fixUnresolvedErrors")
	class FixUnresolvedErrors {
		@Test
		@DisplayName("should reprocess and resolve errors")
		void shouldReprocessAndResolveErrors() {
			// Given
			MigrationJobEntity oldJob = MigrationJobEntity.builder().id(2L).build();
			HintDao hintToFix = HintTestDataGenerator.createHintDaoWithId("fixId");
			MigrationErrorEntity error = MigrationErrorEntity.builder().mongoUUID(hintToFix.id()).resolved(false).job(oldJob).build();
			when(migrationErrorRepo.findByJob_IdAndResolved(2L, false)).thenReturn(List.of(error));
			when(hintRepository.existsByMongoUUID(hintToFix.id())).thenReturn(false);
			when(mongoTemplate.findById(hintToFix.id(), HintDao.class)).thenReturn(hintToFix);
			// When
			migrationService.fixUnresolvedErrors(testJob, 2L);
			// Then
			verify(hintRepository).save(any(HintEntity.class));
			ArgumentCaptor<MigrationErrorEntity> errorCaptor = ArgumentCaptor.forClass(MigrationErrorEntity.class);
			verify(migrationErrorRepo).save(errorCaptor.capture());
			assertThat(errorCaptor.getValue().getResolved()).isTrue();
			ArgumentCaptor<MigrationJobEntity> jobCaptor = ArgumentCaptor.forClass(MigrationJobEntity.class);
			verify(migrationJobRepo).save(jobCaptor.capture());
			assertThat(jobCaptor.getValue().getState()).isEqualTo(MigrationJobEntity.STATE.COMPLETED);
		}

		@Test
		@DisplayName("should log new error if reprocessing fails")
		void shouldLogNewErrorIfReprocessingFails() {
			// Given
			MigrationJobEntity oldJob = MigrationJobEntity.builder().id(2L).build();
			HintDao hintToFix = HintTestDataGenerator.createHintDaoWithId("fixId");
			MigrationErrorEntity error = MigrationErrorEntity.builder().mongoUUID(hintToFix.id()).resolved(false).job(oldJob).build();
			when(migrationErrorRepo.findByJob_IdAndResolved(2L, false)).thenReturn(List.of(error));
			when(hintRepository.existsByMongoUUID(hintToFix.id())).thenReturn(false);
			when(mongoTemplate.findById(hintToFix.id(), HintDao.class)).thenReturn(hintToFix);
			when(hintRepository.save(any(HintEntity.class))).thenThrow(new RuntimeException("Still broken"));
			// When
			migrationService.fixUnresolvedErrors(testJob, 2L);
			// Then
			ArgumentCaptor<MigrationErrorEntity> errorCaptor = ArgumentCaptor.forClass(MigrationErrorEntity.class);
			verify(migrationErrorRepo).save(errorCaptor.capture());
			assertThat(errorCaptor.getValue().getResolved()).isFalse();
			assertThat(errorCaptor.getValue().getJob()).isEqualTo(testJob);
			assertThat(errorCaptor.getValue().getMessage()).isEqualTo("Still broken");
		}

		@Test
		@DisplayName("should resolve error if hint now exists in postgres")
		void shouldResolveErrorIfHintNowExists() {
			// Given
			MigrationJobEntity oldJob = MigrationJobEntity.builder().id(2L).build();
			MigrationErrorEntity error = MigrationErrorEntity.builder().mongoUUID("mongo123").resolved(false).job(oldJob).build();
			when(migrationErrorRepo.findByJob_IdAndResolved(2L, false)).thenReturn(List.of(error));
			when(hintRepository.existsByMongoUUID("mongo123")).thenReturn(true);
			// When
			migrationService.fixUnresolvedErrors(testJob, 2L);
			// Then
			verify(mongoTemplate, never()).findById(anyString(), any());
			ArgumentCaptor<MigrationErrorEntity> errorCaptor = ArgumentCaptor.forClass(MigrationErrorEntity.class);
			verify(migrationErrorRepo).save(errorCaptor.capture());
			assertThat(errorCaptor.getValue().getResolved()).isTrue();
		}

		@Test
		@DisplayName("should log error if hint not found in mongo anymore")
		void shouldLogErrorIfHintNotFoundInMongo() {
			// Given
			MigrationJobEntity oldJob = MigrationJobEntity.builder().id(2L).build();
			MigrationErrorEntity error = MigrationErrorEntity.builder().mongoUUID("mongo123").resolved(false).job(oldJob).build();
			when(migrationErrorRepo.findByJob_IdAndResolved(2L, false)).thenReturn(List.of(error));
			when(hintRepository.existsByMongoUUID("mongo123")).thenReturn(false);
			when(mongoTemplate.findById("mongo123", HintDao.class)).thenReturn(null);
			// When
			migrationService.fixUnresolvedErrors(testJob, 2L);
			// Then
			ArgumentCaptor<MigrationErrorEntity> errorCaptor = ArgumentCaptor.forClass(MigrationErrorEntity.class);
			verify(migrationErrorRepo).save(errorCaptor.capture());
			assertThat(errorCaptor.getValue().getMessage()).isEqualTo("Hint with mongoUUID mongo123 not found in MongoDB.");
		}
	}

	@Test
	@DisplayName("should handle exception during error fixing and set job to broken")
	void shouldHandleExceptionDuringErrorFixing() {
		// Given
		when(migrationErrorRepo.findByJob_IdAndResolved(anyLong(), eq(false))).thenThrow(new RuntimeException("Test exception"));
		// When
		migrationService.fixUnresolvedErrors(testJob, 123L);
		// Then
		ArgumentCaptor<MigrationJobEntity> jobCaptor = ArgumentCaptor.forClass(MigrationJobEntity.class);
		verify(migrationJobRepo).save(jobCaptor.capture());
		MigrationJobEntity captured = jobCaptor.getValue();
		assertThat(captured.getState()).isEqualTo(MigrationJobEntity.STATE.BROKEN);
		assertThat(captured.getMessage()).startsWith("Test exception");
	}

	@Nested
	@DisplayName("startValidation Tests - 100% Coverage")
	class StartValidation {

		@Test
		@DisplayName("should complete successfully when validation passes with matching data")
		void shouldCompleteSuccessfullyWithMatchingData() throws ExecutionException, InterruptedException {
			// Given
			MigrationJobEntity validationJob = MigrationJobEntity.builder()
				.id(1L)
				.type(MigrationJobEntity.TYPE.VALIDATION)
				.dataSetStartDate(LocalDateTime.now().minusDays(1))
				.dataSetStopDate(LocalDateTime.now())
				.build();

			HintDao hintDao = HintTestDataGenerator.createHintDaoWithId("mongoId1");
			HintEntity hintEntity = HintTestDataGenerator.createHintEntityWithMongoId("mongoId1");
			hintEntity.setHintSource(hintDao.hintSource());
			hintEntity.setMessage(hintDao.hintTextOriginal());
			hintEntity.setHintCategory(hintDao.hintCategory());
			hintEntity.setProcessId(hintDao.processId());

			List<HintDao> mongoHints = List.of(hintDao);
			Page<HintEntity> postgresHints = new PageImpl<>(List.of(hintEntity));

			when(mongoTemplate.count(any(Query.class), eq(HintDao.class))).thenReturn(1L);
			when(mongoTemplate.find(any(Query.class), eq(HintDao.class))).thenReturn(mongoHints);
			when(hintRepository.findByCreationDateBetweenAndMongoUUIDIsNotNull(any(), any(), any())).thenReturn(postgresHints);
			when(migrationErrorRepo.findByJob_IdAndResolved(validationJob.getId(), false)).thenReturn(Collections.emptyList());

			// When
			CompletableFuture<Boolean> result = migrationService.startValidation(validationJob);
			Boolean validationResult = result.get();

			// Then
			assertThat(validationResult).isTrue();
			ArgumentCaptor<MigrationJobEntity> jobCaptor = ArgumentCaptor.forClass(MigrationJobEntity.class);
			verify(migrationJobRepo, atLeastOnce()).save(jobCaptor.capture());
			MigrationJobEntity savedJob = jobCaptor.getValue();
			assertThat(savedJob.getState()).isEqualTo(MigrationJobEntity.STATE.COMPLETED);
			assertThat(savedJob.getMessage()).contains("Migration completed successfully.");
			verify(migrationErrorRepo, never()).save(any());
		}

		@Test
		@DisplayName("should break job when counts mismatch - MongoDB has more items")
		void shouldBreakJobWhenMongoHasMoreItems() throws ExecutionException, InterruptedException {
			// Given
			MigrationJobEntity validationJob = MigrationJobEntity.builder()
				.id(1L)
				.type(MigrationJobEntity.TYPE.VALIDATION)
				.build();

			List<HintDao> mongoHints = List.of(
				HintTestDataGenerator.createHintDaoWithId("1"),
				HintTestDataGenerator.createHintDaoWithId("2")
			);
			Page<HintEntity> postgresHints = new PageImpl<>(List.of(
				HintTestDataGenerator.createHintEntityWithMongoId("1")
			));

			when(mongoTemplate.count(any(Query.class), eq(HintDao.class))).thenReturn(2L);
			when(mongoTemplate.find(any(Query.class), eq(HintDao.class))).thenReturn(mongoHints);
			when(hintRepository.findByCreationDateBetweenAndMongoUUIDIsNotNull(any(), any(), any())).thenReturn(postgresHints);

			// When
			CompletableFuture<Boolean> result = migrationService.startValidation(validationJob);
			Boolean validationResult = result.get();

			// Then
			assertThat(validationResult).isFalse();
			ArgumentCaptor<MigrationJobEntity> jobCaptor = ArgumentCaptor.forClass(MigrationJobEntity.class);
			verify(migrationJobRepo, atLeastOnce()).save(jobCaptor.capture());
			assertThat(jobCaptor.getValue().getState()).isEqualTo(MigrationJobEntity.STATE.BROKEN);
			assertThat(jobCaptor.getValue().getMessage()).contains("Mismatch in number of elements");
		}

		@Test
		@DisplayName("should break job when counts mismatch - PostgreSQL has more items")
		void shouldBreakJobWhenPostgresHasMoreItems() throws ExecutionException, InterruptedException {
			// Given
			MigrationJobEntity validationJob = MigrationJobEntity.builder()
				.id(1L)
				.type(MigrationJobEntity.TYPE.VALIDATION)
				.build();

			List<HintDao> mongoHints = List.of(HintTestDataGenerator.createHintDaoWithId("1"));
			Page<HintEntity> postgresHints = new PageImpl<>(List.of(
				HintTestDataGenerator.createHintEntityWithMongoId("1"),
				HintTestDataGenerator.createHintEntityWithMongoId("2")
			));

			when(mongoTemplate.count(any(Query.class), eq(HintDao.class))).thenReturn(1L);
			when(mongoTemplate.find(any(Query.class), eq(HintDao.class))).thenReturn(mongoHints);
			when(hintRepository.findByCreationDateBetweenAndMongoUUIDIsNotNull(any(), any(), any())).thenReturn(postgresHints);

			// When
			CompletableFuture<Boolean> result = migrationService.startValidation(validationJob);
			Boolean validationResult = result.get();

			// Then
			assertThat(validationResult).isFalse();
			ArgumentCaptor<MigrationJobEntity> jobCaptor = ArgumentCaptor.forClass(MigrationJobEntity.class);
			verify(migrationJobRepo, atLeastOnce()).save(jobCaptor.capture());
			assertThat(jobCaptor.getValue().getState()).isEqualTo(MigrationJobEntity.STATE.BROKEN);
			assertThat(jobCaptor.getValue().getMessage()).contains("Mismatch in number of elements");
		}

		@Test
		@DisplayName("should break job on immediate ID mismatch")
		void shouldBreakJobOnIdMismatch() throws ExecutionException, InterruptedException {
			// Given
			MigrationJobEntity validationJob = MigrationJobEntity.builder()
				.id(1L)
				.type(MigrationJobEntity.TYPE.VALIDATION)
				.build();

			List<HintDao> mongoHints = List.of(HintTestDataGenerator.createHintDaoWithId("mongoId1"));
			Page<HintEntity> postgresHints = new PageImpl<>(List.of(
				HintTestDataGenerator.createHintEntityWithMongoId("differentMongoId")
			));

			when(mongoTemplate.count(any(Query.class), eq(HintDao.class))).thenReturn(1L);
			when(mongoTemplate.find(any(Query.class), eq(HintDao.class))).thenReturn(mongoHints);
			when(hintRepository.findByCreationDateBetweenAndMongoUUIDIsNotNull(any(), any(), any())).thenReturn(postgresHints);

			// When
			CompletableFuture<Boolean> result = migrationService.startValidation(validationJob);
			Boolean validationResult = result.get();

			// Then
			assertThat(validationResult).isFalse();
			ArgumentCaptor<MigrationJobEntity> jobCaptor = ArgumentCaptor.forClass(MigrationJobEntity.class);
			verify(migrationJobRepo, atLeastOnce()).save(jobCaptor.capture());
			assertThat(jobCaptor.getValue().getState()).isEqualTo(MigrationJobEntity.STATE.BROKEN);
			assertThat(jobCaptor.getValue().getMessage()).contains("Mismatch id between mongo und postgres element");
		}

		private static Stream<Arguments> provideFieldMismatchScenarios() {
			return Stream.of(
				Arguments.of("processId", "different-processId", "processId"),
				Arguments.of("hintCategory", HintDto.Category.WARNING, "hintCategory"),
				Arguments.of("hintSource", "DIFFERENT-SOURCE", "hintSource"),
				Arguments.of("message", "Different message text", "message")
			);
		}

		@ParameterizedTest(name = "Field mismatch: {0}")
		@MethodSource("provideFieldMismatchScenarios")
		@DisplayName("should log errors on individual field mismatches")
		void shouldLogErrorsOnIndividualFieldMismatches(String fieldName, Object differentValue, String expectedInMessage) throws ExecutionException, InterruptedException {
			// Given
			MigrationJobEntity validationJob = MigrationJobEntity.builder()
				.id(1L)
				.type(MigrationJobEntity.TYPE.VALIDATION)
				.build();

			HintDao mongoHint = HintTestDataGenerator.createHintDaoWithId("mongoId1");
			HintEntity postgresHint = HintTestDataGenerator.createHintEntityWithMongoId("mongoId1");

			// Set all fields to match initially
			postgresHint.setProcessId(mongoHint.processId());
			postgresHint.setHintCategory(mongoHint.hintCategory());
			postgresHint.setHintSource(mongoHint.hintSource());
			postgresHint.setMessage(mongoHint.hintTextOriginal());

			// Now set the specific field to be different
			switch (fieldName) {
				case "processId" -> postgresHint.setProcessId((String) differentValue);
				case "hintCategory" -> postgresHint.setHintCategory((HintDto.Category) differentValue);
				case "hintSource" -> postgresHint.setHintSource((String) differentValue);
				case "message" -> postgresHint.setMessage((String) differentValue);
				default ->  throw new IllegalStateException("Unexpected value: " + fieldName);
			}

			when(mongoTemplate.count(any(Query.class), eq(HintDao.class))).thenReturn(1L);
			when(mongoTemplate.find(any(Query.class), eq(HintDao.class))).thenReturn(List.of(mongoHint));
			when(hintRepository.findByCreationDateBetweenAndMongoUUIDIsNotNull(any(), any(), any()))
				.thenReturn(new PageImpl<>(List.of(postgresHint)));
			when(migrationErrorRepo.findByJob_IdAndResolved(validationJob.getId(), false))
				.thenReturn(List.of(MigrationTestDataGenerator.createUnresolvedError(validationJob)));

			// When
			CompletableFuture<Boolean> result = migrationService.startValidation(validationJob);
			Boolean validationResult = result.get();

			// Then
			assertThat(validationResult).isFalse();
			ArgumentCaptor<MigrationErrorEntity> errorCaptor = ArgumentCaptor.forClass(MigrationErrorEntity.class);
			verify(migrationErrorRepo, atLeastOnce()).save(errorCaptor.capture());
			MigrationErrorEntity savedError = errorCaptor.getValue();
			assertThat(savedError.getMessage()).contains("Field mismatches");
			assertThat(savedError.getMessage()).contains(expectedInMessage);
			assertThat(savedError.getMongoUUID()).isEqualTo("mongoId1");
		}

		@Test
		@DisplayName("should log errors on multiple field mismatches")
		void shouldLogErrorsOnMultipleFieldMismatches() throws ExecutionException, InterruptedException {
			// Given
			MigrationJobEntity validationJob = MigrationJobEntity.builder()
				.id(1L)
				.type(MigrationJobEntity.TYPE.VALIDATION)
				.build();

			HintDao mongoHint = HintTestDataGenerator.createHintDaoWithId("mongoId1");
			HintEntity postgresHint = HintTestDataGenerator.createHintEntityWithMongoId("mongoId1");

			// Set multiple fields to be different
			postgresHint.setMessage("different message");
			postgresHint.setHintCategory(HintDto.Category.WARNING);
			postgresHint.setHintSource("DIFFERENT-SOURCE");
			postgresHint.setProcessId("different-process-id");

			when(mongoTemplate.count(any(Query.class), eq(HintDao.class))).thenReturn(1L);
			when(mongoTemplate.find(any(Query.class), eq(HintDao.class))).thenReturn(List.of(mongoHint));
			when(hintRepository.findByCreationDateBetweenAndMongoUUIDIsNotNull(any(), any(), any()))
				.thenReturn(new PageImpl<>(List.of(postgresHint)));
			when(migrationErrorRepo.findByJob_IdAndResolved(validationJob.getId(), false))
				.thenReturn(List.of(MigrationTestDataGenerator.createUnresolvedError(validationJob)));

			// When
			CompletableFuture<Boolean> result = migrationService.startValidation(validationJob);
			Boolean validationResult = result.get();

			// Then
			assertThat(validationResult).isFalse();
			ArgumentCaptor<MigrationErrorEntity> errorCaptor = ArgumentCaptor.forClass(MigrationErrorEntity.class);
			verify(migrationErrorRepo, atLeastOnce()).save(errorCaptor.capture());
			MigrationErrorEntity savedError = errorCaptor.getValue();
			assertThat(savedError.getMessage()).contains("Field mismatches");
			assertThat(savedError.getMessage()).contains("processId");
			assertThat(savedError.getMessage()).contains("hintCategory");
			assertThat(savedError.getMessage()).contains("hintSource");
			assertThat(savedError.getMessage()).contains("message");
		}

		@Test
		@DisplayName("should process multiple pages and validate all")
		void shouldProcessMultiplePagesAndValidateAll() throws ExecutionException, InterruptedException {
			// Given
			MigrationJobEntity validationJob = MigrationJobEntity.builder()
				.id(1L)
				.type(MigrationJobEntity.TYPE.VALIDATION)
				.build();

			// Create data for two pages
			HintDao dao1 = HintTestDataGenerator.createHintDaoWithId("page1-id1");
			HintDao dao2 = HintTestDataGenerator.createHintDaoWithId("page1-id2");
			HintDao dao3 = HintTestDataGenerator.createHintDaoWithId("page2-id1");

			HintEntity entity1 = HintTestDataGenerator.createHintEntityWithMongoId("page1-id1");
			entity1.setProcessId(dao1.processId());
			entity1.setHintCategory(dao1.hintCategory());
			entity1.setHintSource(dao1.hintSource());
			entity1.setMessage(dao1.hintTextOriginal());

			HintEntity entity2 = HintTestDataGenerator.createHintEntityWithMongoId("page1-id2");
			entity2.setProcessId(dao2.processId());
			entity2.setHintCategory(dao2.hintCategory());
			entity2.setHintSource(dao2.hintSource());
			entity2.setMessage(dao2.hintTextOriginal());

			HintEntity entity3 = HintTestDataGenerator.createHintEntityWithMongoId("page2-id1");
			entity3.setProcessId(dao3.processId());
			entity3.setHintCategory(dao3.hintCategory());
			entity3.setHintSource(dao3.hintSource());
			entity3.setMessage(dao3.hintTextOriginal());

			Page<HintEntity> page1 = new PageImpl<>(List.of(entity1, entity2), Pageable.ofSize(2), 3);
			Page<HintEntity> page2 = new PageImpl<>(List.of(entity3), Pageable.ofSize(2).next(), 3);

			when(mongoTemplate.count(any(Query.class), eq(HintDao.class))).thenReturn(3L);
			when(mongoTemplate.find(any(Query.class), eq(HintDao.class)))
				.thenReturn(List.of(dao1, dao2))
				.thenReturn(List.of(dao3));
			when(hintRepository.findByCreationDateBetweenAndMongoUUIDIsNotNull(any(), any(), any()))
				.thenReturn(page1)
				.thenReturn(page2);
			when(migrationErrorRepo.findByJob_IdAndResolved(validationJob.getId(), false))
				.thenReturn(Collections.emptyList());

			// When
			CompletableFuture<Boolean> result = migrationService.startValidation(validationJob);
			Boolean validationResult = result.get();

			// Then
			assertThat(validationResult).isTrue();
			verify(mongoTemplate, times(2)).find(any(Query.class), eq(HintDao.class));
			verify(hintRepository, times(2)).findByCreationDateBetweenAndMongoUUIDIsNotNull(any(), any(), any());
			ArgumentCaptor<MigrationJobEntity> jobCaptor = ArgumentCaptor.forClass(MigrationJobEntity.class);
			verify(migrationJobRepo, atLeastOnce()).save(jobCaptor.capture());
			MigrationJobEntity finalJob = jobCaptor.getValue();
			assertThat(finalJob.getProcessedItems()).isEqualTo(3L);
		}

		@Test
		@DisplayName("should break job when unresolved errors exist after validation")
		void shouldBreakJobWhenUnresolvedErrorsExistAfterValidation() throws ExecutionException, InterruptedException {
			// Given
			MigrationJobEntity validationJob = MigrationJobEntity.builder()
				.id(1L)
				.type(MigrationJobEntity.TYPE.VALIDATION)
				.build();

			HintDao hintDao = HintTestDataGenerator.createHintDaoWithId("mongoId1");
			HintEntity hintEntity = HintTestDataGenerator.createHintEntityWithMongoId("mongoId1");
			hintEntity.setProcessId(hintDao.processId());
			hintEntity.setHintCategory(hintDao.hintCategory());
			hintEntity.setHintSource(hintDao.hintSource());
			hintEntity.setMessage(hintDao.hintTextOriginal());

			when(mongoTemplate.count(any(Query.class), eq(HintDao.class))).thenReturn(1L);
			when(mongoTemplate.find(any(Query.class), eq(HintDao.class))).thenReturn(List.of(hintDao));
			when(hintRepository.findByCreationDateBetweenAndMongoUUIDIsNotNull(any(), any(), any()))
				.thenReturn(new PageImpl<>(List.of(hintEntity)));
			// Simulate that errors were found during validation
			when(migrationErrorRepo.findByJob_IdAndResolved(validationJob.getId(), false))
				.thenReturn(List.of(MigrationTestDataGenerator.createUnresolvedError(validationJob)));

			// When
			CompletableFuture<Boolean> result = migrationService.startValidation(validationJob);
			Boolean validationResult = result.get();

			// Then
			assertThat(validationResult).isFalse();
			ArgumentCaptor<MigrationJobEntity> jobCaptor = ArgumentCaptor.forClass(MigrationJobEntity.class);
			verify(migrationJobRepo, atLeastOnce()).save(jobCaptor.capture());
			assertThat(jobCaptor.getValue().getState()).isEqualTo(MigrationJobEntity.STATE.BROKEN);
			assertThat(jobCaptor.getValue().getMessage()).contains("There are errors in migration process");
		}

		@Test
		@DisplayName("should handle exception during validation and break the job")
		void shouldHandleExceptionDuringValidation() throws ExecutionException, InterruptedException {
			// Given
			MigrationJobEntity validationJob = MigrationJobEntity.builder()
				.id(1L)
				.type(MigrationJobEntity.TYPE.VALIDATION)
				.build();
			when(mongoTemplate.count(any(Query.class), eq(HintDao.class))).thenThrow(new RuntimeException("MongoDB connection error"));

			// When
			CompletableFuture<Boolean> result = migrationService.startValidation(validationJob);
			Boolean validationResult = result.get();

			// Then
			assertThat(validationResult).isFalse();
			ArgumentCaptor<MigrationJobEntity> jobCaptor = ArgumentCaptor.forClass(MigrationJobEntity.class);
			verify(migrationJobRepo, atLeastOnce()).save(jobCaptor.capture());
			MigrationJobEntity finalJob = jobCaptor.getValue();
			assertThat(finalJob.getState()).isEqualTo(MigrationJobEntity.STATE.BROKEN);
			assertThat(finalJob.getMessage()).contains("MongoDB connection error");
		}

		@Test
		@DisplayName("should handle null date ranges in validation")
		void shouldHandleNullDateRangesInValidation() throws ExecutionException, InterruptedException {
			// Given
			MigrationJobEntity validationJob = MigrationJobEntity.builder()
				.id(1L)
				.type(MigrationJobEntity.TYPE.VALIDATION)
				.dataSetStartDate(null)
				.dataSetStopDate(null)
				.build();

			HintDao hintDao = HintTestDataGenerator.createHintDaoWithId("mongoId1");
			HintEntity hintEntity = HintTestDataGenerator.createHintEntityWithMongoId("mongoId1");
			hintEntity.setProcessId(hintDao.processId());
			hintEntity.setHintCategory(hintDao.hintCategory());
			hintEntity.setHintSource(hintDao.hintSource());
			hintEntity.setMessage(hintDao.hintTextOriginal());

			when(mongoTemplate.count(any(Query.class), eq(HintDao.class))).thenReturn(1L);
			when(mongoTemplate.find(any(Query.class), eq(HintDao.class))).thenReturn(List.of(hintDao));
			when(hintRepository.findByCreationDateBetweenAndMongoUUIDIsNotNull(isNull(), isNull(), any()))
				.thenReturn(new PageImpl<>(List.of(hintEntity)));
			when(migrationErrorRepo.findByJob_IdAndResolved(validationJob.getId(), false))
				.thenReturn(Collections.emptyList());

			// When
			CompletableFuture<Boolean> result = migrationService.startValidation(validationJob);
			Boolean validationResult = result.get();

			// Then
			assertThat(validationResult).isTrue();
			verify(hintRepository).findByCreationDateBetweenAndMongoUUIDIsNotNull(isNull(), isNull(), any());
		}
	}

	@Nested
	@DisplayName("countMongoHints Tests")
	class CountMongoHints {
		private static Stream<Arguments> provideDateRangeCombinations() {
			LocalDateTime now = LocalDateTime.now();
			LocalDateTime lastWeek = now.minusDays(7);
			return Stream.of(
				Arguments.of(null, null, "both null"),
				Arguments.of(lastWeek, null, "only start date"),
				Arguments.of(null, now, "only end date"),
				Arguments.of(lastWeek, now, "both dates present")
			);
		}

		@ParameterizedTest(name = "{2}")
		@MethodSource("provideDateRangeCombinations")
		@DisplayName("should count with different date range combinations")
		void shouldCountWithDifferentDateRanges(LocalDateTime startDate, LocalDateTime endDate, String scenario) {
			// Given
			when(mongoTemplate.count(any(Query.class), eq(HintDao.class))).thenReturn(42L);
			// When
			long count = migrationService.countMongoHints(startDate, endDate);
			// Then
			assertThat(count).isEqualTo(42L);
			verify(mongoTemplate).count(any(Query.class), eq(HintDao.class));
		}
	}
}
