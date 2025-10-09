package de.signaliduna.elpa.hint.core;

import de.signaliduna.elpa.hint.adapter.database.HintRepository;
import de.signaliduna.elpa.hint.adapter.database.MigrationErrorRepo;
import de.signaliduna.elpa.hint.adapter.database.MigrationJobRepo;
import de.signaliduna.elpa.hint.adapter.database.legacy.model.HintDao;
import de.signaliduna.elpa.hint.adapter.database.model.HintEntity;
import de.signaliduna.elpa.hint.adapter.database.model.MigrationErrorEntity;
import de.signaliduna.elpa.hint.adapter.database.model.MigrationJobEntity;
import de.signaliduna.elpa.hint.adapter.mapper.HintMapper;
import de.signaliduna.elpa.hint.util.HintTestDataGenerator;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
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
			assertThat(jobCaptor.getValue().getState()).isEqualTo(MigrationJobEntity.STATE.COMPLETED);
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
		@DisplayName("should handle general exception and set job to broken")
		void shouldHandleGeneralException() throws ExecutionException, InterruptedException {
			// Given
			when(mongoTemplate.count(any(Query.class), eq(HintDao.class))).thenThrow(new RuntimeException("Mongo is down"));
			// When
			migrationService.startMigration(testJob).get();
			// Then
			ArgumentCaptor<MigrationJobEntity> jobCaptor = ArgumentCaptor.forClass(MigrationJobEntity.class);
			verify(migrationJobRepo, atLeastOnce()).save(jobCaptor.capture());
			assertThat(jobCaptor.getValue().getState()).isEqualTo(MigrationJobEntity.STATE.BROKEN);
			assertThat(jobCaptor.getValue().getMessage()).startsWith("Mongo is down");
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
			MigrationJobEntity captured = jobCaptor.getValue();
			assertThat(captured.getState()).isEqualTo(MigrationJobEntity.STATE.BROKEN);
			assertThat(captured.getMessage()).startsWith("Test exception");
		}

		private static Stream<Arguments> provideDateRangeArguments() {
			LocalDateTime now = LocalDateTime.now().withNano(0);
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
			Document queryObject = capturedQuery.getQueryObject();
			assertThat(queryObject).containsKey("creationDate");
			assertThat(queryObject.get("creationDate")).isInstanceOf(Document.class);
			Document creationDateFilter = (Document) queryObject.get("creationDate");

			if (startDate != null) {
				assertThat(creationDateFilter).containsEntry("$gte", startDate);
			} else {
				assertThat(creationDateFilter).doesNotContainKey("$gte");
			}

			if (stopDate != null) {
				assertThat(creationDateFilter).containsEntry("$lte", stopDate);
			} else {
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
			for(int i = 0; i < page1Daos.size(); i++) {
				when(converter.read(HintDao.class, page1Docs.get(i))).thenReturn(page1Daos.get(i));
			}
			when(converter.read(HintDao.class, page2Docs.getFirst())).thenReturn(page2Daos.getFirst());
			when(mongoTemplate.count(any(Query.class), eq(HintDao.class))).thenReturn(totalHintDaos);
			// When
			migrationService.startMigration(testJob).get();
			// Then
			verify(hintRepository, times((int) (totalHintDaos))).save(any(HintEntity.class));
		}

		@Test
		@DisplayName("should handle document with missing id")
		void shouldHandleDocumentWithMissingId() throws ExecutionException, InterruptedException {
			// Given
			Document docWithMissingId = new Document("some_other_field", "some_value");

			when(mongoTemplate.getCollectionName(HintDao.class)).thenReturn("Hint");
			when(mongoTemplate.find(any(Query.class), eq(Document.class), eq("Hint")))
				.thenReturn(List.of(docWithMissingId))
				.thenReturn(Collections.emptyList());

			MongoConverter converter = mock(MongoConverter.class);
			when(mongoTemplate.getConverter()).thenReturn(converter);
			when(converter.read(eq(HintDao.class), any(Document.class))).thenThrow(new RuntimeException("Conversion fails"));

			when(mongoTemplate.count(any(Query.class), eq(HintDao.class))).thenReturn(1L);

			// When
			migrationService.startMigration(testJob).get();

			// Then
			ArgumentCaptor<MigrationErrorEntity> errorCaptor = ArgumentCaptor.forClass(MigrationErrorEntity.class);
			verify(migrationErrorRepo).save(errorCaptor.capture());
			assertThat(errorCaptor.getValue().getMongoUUID()).isEqualTo("UNKNOWN_MONGO_ID");
		}



		@Test
		@DisplayName("should log parsing errors and continue migration")
		void shouldLogParsingErrorsAndContinue() throws ExecutionException, InterruptedException {
			// Given
			HintDao validHint = HintTestDataGenerator.createHintDaoWithId("validId");
			Document validDoc = HintTestDataGenerator.createDocumentFromHintDao(validHint);
			Document invalidDoc = new Document("_id", "invalidId");

			when(mongoTemplate.find(any(Query.class), eq(Document.class), anyString()))
				.thenReturn(List.of(invalidDoc, validDoc))
				.thenReturn(Collections.emptyList());
			when(mongoTemplate.count(any(Query.class), eq(HintDao.class))).thenReturn(2L);
			when(mongoTemplate.getCollectionName(HintDao.class)).thenReturn("Hint");

			// Mock converter to throw exception for invalid document, succeed for valid
			MongoConverter converter = mock(MongoConverter.class);
			when(mongoTemplate.getConverter()).thenReturn(converter);
			when(converter.read(eq(HintDao.class), any(Document.class)))
				.thenThrow(new RuntimeException("Parse error"))
				.thenReturn(validHint);
			when(hintRepository.existsByMongoUUID("validId")).thenReturn(false);
			// When
			migrationService.startMigration(testJob).get();
			// Then
			verify(hintRepository, times(1)).save(any(HintEntity.class));
			ArgumentCaptor<MigrationErrorEntity> errorCaptor = ArgumentCaptor.forClass(MigrationErrorEntity.class);
			verify(migrationErrorRepo).save(errorCaptor.capture());
			assertThat(errorCaptor.getValue().getMongoUUID()).isEqualTo("invalidId");
			assertThat(errorCaptor.getValue().getMessage()).contains("Failed to parse HintDao");
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

	@Nested
	@DisplayName("startValidation")
	class StartValidation {

		@Test
		@DisplayName("should complete successfully when validation passes")
		void shouldCompleteSuccessfully() throws ExecutionException, InterruptedException {
			// Given
			MigrationJobEntity validationJob = MigrationJobEntity.builder().id(1L).type(MigrationJobEntity.TYPE.VALIDATION).build();
			List<HintDao> mongoHints = List.of(HintTestDataGenerator.createHintDaoWithId("1"));
			Page<HintEntity> postgresHints = new PageImpl<>(List.of(HintTestDataGenerator.createHintEntityWithMongoId("1")));

			when(mongoTemplate.count(any(Query.class), eq(HintDao.class))).thenReturn(1L);
			when(mongoTemplate.find(any(Query.class), eq(HintDao.class))).thenReturn(mongoHints);
			when(hintRepository.findByCreationDateBetweenAndMongoUUIDIsNotNull(any(), any(), any())).thenReturn(postgresHints);

			// When
			migrationService.startValidation(validationJob).get();

			// Then
			ArgumentCaptor<MigrationJobEntity> jobCaptor = ArgumentCaptor.forClass(MigrationJobEntity.class);
			verify(migrationJobRepo, atLeastOnce()).save(jobCaptor.capture());
			assertThat(jobCaptor.getValue().getState()).isEqualTo(MigrationJobEntity.STATE.COMPLETED);
		}

		@Test
		@DisplayName("should break job when item counts mismatch")
		void shouldBreakJobWhenCountsMismatch() throws ExecutionException, InterruptedException {
			// Given
			MigrationJobEntity validationJob = MigrationJobEntity.builder().id(1L).type(MigrationJobEntity.TYPE.VALIDATION).build();
			List<HintDao> mongoHints = List.of(HintTestDataGenerator.createHintDaoWithId("1"));
			Page<HintEntity> postgresHints = new PageImpl<>(Collections.emptyList());

			when(mongoTemplate.count(any(Query.class), eq(HintDao.class))).thenReturn(1L);
			when(mongoTemplate.find(any(Query.class), eq(HintDao.class))).thenReturn(mongoHints);
			when(hintRepository.findByCreationDateBetweenAndMongoUUIDIsNotNull(any(), any(), any())).thenReturn(postgresHints);

			// When
			migrationService.startValidation(validationJob).get();

			// Then
			ArgumentCaptor<MigrationJobEntity> jobCaptor = ArgumentCaptor.forClass(MigrationJobEntity.class);
			verify(migrationJobRepo, atLeastOnce()).save(jobCaptor.capture());
			assertThat(jobCaptor.getValue().getState()).isEqualTo(MigrationJobEntity.STATE.BROKEN);
		}

		@Test
		@DisplayName("should break job on immediate ID mismatch")
		void shouldBreakJobOnIdMismatch() throws ExecutionException, InterruptedException {
			// Given
			MigrationJobEntity validationJob = MigrationJobEntity.builder().id(1L).type(MigrationJobEntity.TYPE.VALIDATION).build();
			List<HintDao> mongoHints = List.of(HintTestDataGenerator.createHintDaoWithId("1"));
			Page<HintEntity> postgresHints = new PageImpl<>(List.of(HintTestDataGenerator.createHintEntityWithMongoId("2")));

			when(mongoTemplate.count(any(Query.class), eq(HintDao.class))).thenReturn(1L);
			when(mongoTemplate.find(any(Query.class), eq(HintDao.class))).thenReturn(mongoHints);
			when(hintRepository.findByCreationDateBetweenAndMongoUUIDIsNotNull(any(), any(), any())).thenReturn(postgresHints);

			// When
			migrationService.startValidation(validationJob).get();

			// Then
			ArgumentCaptor<MigrationJobEntity> jobCaptor = ArgumentCaptor.forClass(MigrationJobEntity.class);
			verify(migrationJobRepo, atLeastOnce()).save(jobCaptor.capture());
			assertThat(jobCaptor.getValue().getState()).isEqualTo(MigrationJobEntity.STATE.BROKEN);
		}

		@Test
		@DisplayName("should log errors on field mismatch but complete job")
		void shouldLogErrorsOnFieldMismatch() throws ExecutionException, InterruptedException {
			// Given
			MigrationJobEntity validationJob = MigrationJobEntity.builder().id(1L).type(MigrationJobEntity.TYPE.VALIDATION).build();
			HintDao mongoHint = HintTestDataGenerator.createHintDaoWithId("1");
			HintEntity postgresHint = HintTestDataGenerator.createHintEntityWithMongoId("1");
			postgresHint.setMessage("different message");

			when(mongoTemplate.count(any(Query.class), eq(HintDao.class))).thenReturn(1L);
			when(mongoTemplate.find(any(Query.class), eq(HintDao.class))).thenReturn(List.of(mongoHint));
			when(hintRepository.findByCreationDateBetweenAndMongoUUIDIsNotNull(any(), any(), any())).thenReturn(new PageImpl<>(List.of(postgresHint)));

			// When
			migrationService.startValidation(validationJob).get();

			// Then
			verify(migrationErrorRepo).save(any(MigrationErrorEntity.class));
			ArgumentCaptor<MigrationJobEntity> jobCaptor = ArgumentCaptor.forClass(MigrationJobEntity.class);
			verify(migrationJobRepo, atLeastOnce()).save(jobCaptor.capture());
			assertThat(jobCaptor.getValue().getState()).isEqualTo(MigrationJobEntity.STATE.COMPLETED);
		}

		@Test
		@DisplayName("should handle exceptions and break the job")
		void shouldHandleExceptions() throws ExecutionException, InterruptedException {
			// Given
			MigrationJobEntity validationJob = MigrationJobEntity.builder().id(1L).type(MigrationJobEntity.TYPE.VALIDATION).build();
			when(mongoTemplate.count(any(Query.class), eq(HintDao.class))).thenThrow(new RuntimeException("DB error"));

			// When
			migrationService.startValidation(validationJob).get();

			// Then
			ArgumentCaptor<MigrationJobEntity> jobCaptor = ArgumentCaptor.forClass(MigrationJobEntity.class);
			verify(migrationJobRepo, atLeastOnce()).save(jobCaptor.capture());
			MigrationJobEntity finalJob = jobCaptor.getValue();
			assertThat(finalJob.getState()).isEqualTo(MigrationJobEntity.STATE.BROKEN);
			assertThat(finalJob.getMessage()).contains("DB error");
		}
	}
}
