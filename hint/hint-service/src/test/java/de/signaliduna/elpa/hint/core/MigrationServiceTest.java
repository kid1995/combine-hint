package de.signaliduna.elpa.hint.core;

import com.fasterxml.jackson.databind.ObjectMapper;
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
			List<HintDao> hintDaos = List.of(HintTestDataGenerator.createWarningHintDao(), HintTestDataGenerator.createErrorHintDao());
			when(mongoTemplate.find(any(Query.class), eq(HintDao.class))).thenReturn(hintDaos).thenReturn(Collections.emptyList());
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
			verify(migrationJobRepo).save(jobCaptor.capture());
			assertThat(jobCaptor.getValue().getState()).isEqualTo(MigrationJobEntity.STATE.COMPLETED);
			assertThat(jobCaptor.getValue().getMessage()).isEqualTo("Migration completed successfully.");
		}

		@Test
		@DisplayName("should skip existing hints")
		void shouldSkipExistingHints() throws ExecutionException, InterruptedException {
			// Given
			HintDao existingHint = HintTestDataGenerator.createWarningHintDao();
			List<HintDao> hintDaos = List.of(existingHint);
			when(mongoTemplate.find(any(Query.class), eq(HintDao.class))).thenReturn(hintDaos).thenReturn(Collections.emptyList());
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
			HintDao badHint = HintTestDataGenerator.createErrorHintDao();
			when(mongoTemplate.find(any(Query.class), eq(HintDao.class))).thenReturn(List.of(badHint)).thenReturn(Collections.emptyList());
			when(mongoTemplate.count(any(Query.class), eq(HintDao.class))).thenReturn(1L);
			when(hintRepository.existsByMongoUUID(badHint.id())).thenReturn(false);
			when(hintRepository.save(any(HintEntity.class))).thenThrow(new DataIntegrityViolationException("Test Exception"));

			// When
			migrationService.startMigration(testJob).get();

			// Then
			verify(hintRepository, times(1)).save(any(HintEntity.class));
			ArgumentCaptor<MigrationErrorEntity> errorCaptor = ArgumentCaptor.forClass(MigrationErrorEntity.class);
			verify(migrationErrorRepo).save(errorCaptor.capture());
			assertThat(errorCaptor.getValue().getMongoUUID()).isEqualTo(badHint.id());
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
			verify(migrationJobRepo).save(jobCaptor.capture());
			assertThat(jobCaptor.getValue().getState()).isEqualTo(MigrationJobEntity.STATE.BROKEN);
			assertThat(jobCaptor.getValue().getMessage()).isEqualTo("Mongo is down");
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
			HintDao hintToFix = HintTestDataGenerator.createErrorHintDao();
			MigrationErrorEntity error = MigrationErrorEntity.builder().mongoUUID(hintToFix.id()).resolved(false).jobID(oldJob).build();

			when(migrationErrorRepo.findByJobIDAndResolved(2L, false)).thenReturn(List.of(error));
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
			HintDao hintToFix = HintTestDataGenerator.createErrorHintDao();
			MigrationErrorEntity error = MigrationErrorEntity.builder().mongoUUID(hintToFix.id()).resolved(false).jobID(oldJob).build();

			when(migrationErrorRepo.findByJobIDAndResolved(2L, false)).thenReturn(List.of(error));
			when(hintRepository.existsByMongoUUID(hintToFix.id())).thenReturn(false);
			when(mongoTemplate.findById(hintToFix.id(), HintDao.class)).thenReturn(hintToFix);
			when(hintRepository.save(any(HintEntity.class))).thenThrow(new RuntimeException("Still broken"));

			// When
			migrationService.fixUnresolvedErrors(testJob, 2L);

			// Then
			ArgumentCaptor<MigrationErrorEntity> errorCaptor = ArgumentCaptor.forClass(MigrationErrorEntity.class);
			verify(migrationErrorRepo).save(errorCaptor.capture());
			assertThat(errorCaptor.getValue().getResolved()).isFalse();
			assertThat(errorCaptor.getValue().getJobID()).isEqualTo(testJob);
			assertThat(errorCaptor.getValue().getMessage()).isEqualTo("Still broken");
		}

		@Test
		@DisplayName("should resolve error if hint now exists in postgres")
		void shouldResolveErrorIfHintNowExists() {
			// Given
			MigrationJobEntity oldJob = MigrationJobEntity.builder().id(2L).build();
			MigrationErrorEntity error = MigrationErrorEntity.builder().mongoUUID("mongo123").resolved(false).jobID(oldJob).build();
			when(migrationErrorRepo.findByJobIDAndResolved(2L, false)).thenReturn(List.of(error));
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
			MigrationErrorEntity error = MigrationErrorEntity.builder().mongoUUID("mongo123").resolved(false).jobID(oldJob).build();
			when(migrationErrorRepo.findByJobIDAndResolved(2L, false)).thenReturn(List.of(error));
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
}
