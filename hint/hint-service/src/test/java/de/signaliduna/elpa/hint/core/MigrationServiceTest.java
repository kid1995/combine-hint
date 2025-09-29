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
import org.springframework.data.mongodb.core.MongoTemplate;
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
			when(mongoTemplate.find(any(Query.class), eq(HintDao.class)))
				.thenReturn(hintDaos)
				.thenReturn(Collections.emptyList()); // This return need to end recursion
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
			when(mongoTemplate.find(any(Query.class), eq(HintDao.class)))
				.thenReturn(List.of(existingHint))
				.thenReturn(Collections.emptyList());
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

			when(mongoTemplate.find(any(Query.class), eq(HintDao.class)))
				.thenReturn(List.of(hintMongoDataWithNullProcessId))
				.thenReturn(Collections.emptyList());

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
			assertThat(errorCaptor.getValue().getMessage()).contains("Data integrity violation");
		}

		@Test
		@DisplayName("should handle general exception and set job to broken")
		void shouldHandleGeneralException() throws ExecutionException, InterruptedException {
			// Given
			when(mongoTemplate.find(any(Query.class), eq(HintDao.class))).thenThrow(new RuntimeException("Mongo is down"));

			// When
			migrationService.startMigration(testJob).get();

			// Then
			ArgumentCaptor<MigrationJobEntity> jobCaptor = ArgumentCaptor.forClass(MigrationJobEntity.class);
			verify(migrationJobRepo, atLeastOnce()).save(jobCaptor.capture());
			assertThat(jobCaptor.getValue().getState()).isEqualTo(MigrationJobEntity.STATE.BROKEN);
			assertThat(jobCaptor.getValue().getMessage()).isEqualTo("Mongo is down");
		}

		@Test
		@DisplayName("should handle exception during migration and set job to broken")
		void shouldHandleExceptionDuringMigration() throws ExecutionException, InterruptedException {
			// Given
			when(mongoTemplate.find(any(Query.class), eq(HintDao.class))).thenThrow(new RuntimeException("Test exception"));

			// When
			CompletableFuture<Long> future = migrationService.startMigration(testJob);
			future.get();

			// Then
			ArgumentCaptor<MigrationJobEntity> jobCaptor = ArgumentCaptor.forClass(MigrationJobEntity.class);
			verify(migrationJobRepo, atLeastOnce()).save(jobCaptor.capture());
			MigrationJobEntity captured = jobCaptor.getValue();
			assertThat(captured.getState()).isEqualTo(MigrationJobEntity.STATE.BROKEN);
			assertThat(captured.getMessage()).isEqualTo("Test exception");
		}

		private static Stream<Arguments> provideDateRangeArguments() {
			return Stream.of(
				Arguments.of(LocalDateTime.now().minusDays(1), LocalDateTime.now(), true, "Both dates present"),
				Arguments.of(null, LocalDateTime.now(), false, "Start date null"),
				Arguments.of(LocalDateTime.now().minusDays(1), null, false, "End date null"));
		}

		@ParameterizedTest
		@DisplayName("should filter by date range if provided")
		@MethodSource("provideDateRangeArguments")
		void shouldFilterByDateRange(LocalDateTime startDate, LocalDateTime stopDate, boolean shouldFilter, String testName) throws ExecutionException, InterruptedException {
			// Given
			testJob.setDataSetStartDate(startDate);
			testJob.setDataSetStopDate(stopDate);
			when(mongoTemplate.find(any(Query.class), eq(HintDao.class))).thenReturn(Collections.emptyList());

			// When
			migrationService.startMigration(testJob).get();

			// Then
			ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
			verify(mongoTemplate, atLeast(1)).find(queryCaptor.capture(), eq(HintDao.class));
			Query capturedQuery = queryCaptor.getValue();
			if (shouldFilter) {
				assertThat(capturedQuery.getQueryObject()).containsKey("creationDate");
			} else {
				assertThat(capturedQuery.getQueryObject()).doesNotContainKey("creationDate");
			}
		}

		@Test
		@DisplayName("should process multiple pages")
		void shouldProcessMultiplePages() throws ExecutionException, InterruptedException {
			// Given
			List<HintDao> page1 = HintTestDataGenerator.createMultipleHintDao(100);
			List<HintDao> page2 = List.of(HintTestDataGenerator.createHintDaoWithId("mongoId2"));
			long totalHintDaos = page1.size() + page2.size();
			when(mongoTemplate.find(any(Query.class), eq(HintDao.class)))
				.thenReturn(page1)
				.thenReturn(page2)
				.thenReturn(Collections.emptyList());
			when(mongoTemplate.count(any(Query.class), eq(HintDao.class))).thenReturn(totalHintDaos).thenReturn(totalHintDaos).thenReturn(0L);

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
		assertThat(captured.getMessage()).isEqualTo("Test exception");
	}
}