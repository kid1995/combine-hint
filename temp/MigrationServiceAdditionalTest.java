package de.signaliduna.elpa.hint.core;

import de.signaliduna.elpa.hint.adapter.database.HintRepository;
import de.signaliduna.elpa.hint.adapter.database.MigrationErrorRepo;
import de.signaliduna.elpa.hint.adapter.database.MigrationJobRepo;
import de.signaliduna.elpa.hint.adapter.database.legacy.model.HintDao;
import de.signaliduna.elpa.hint.adapter.database.model.MigrationErrorEntity;
import de.signaliduna.elpa.hint.adapter.database.model.MigrationJobEntity;
import de.signaliduna.elpa.hint.adapter.mapper.HintMapper;
import de.signaliduna.elpa.hint.core.model.ValidationResult;
import de.signaliduna.elpa.hint.util.HintTestDataGenerator;
import de.signaliduna.elpa.hint.util.MigrationTestDataGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MigrationService Additional Tests for 100% Coverage")
class MigrationServiceAdditionalTest {

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
		testJob = MigrationJobEntity.builder()
			.id(1L)
			.creationDate(LocalDateTime.now())
			.state(MigrationJobEntity.STATE.RUNNING)
			.build();
	}

	@Nested
	@DisplayName("countMongoHints Tests")
	class CountMongoHints {

		private static Stream<Arguments> provideDateRangeCombinations() {
			LocalDateTime now = LocalDateTime.now();
			LocalDateTime yesterday = now.minusDays(1);
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

		@Test
		@DisplayName("should return zero when collection is empty")
		void shouldReturnZeroWhenEmpty() {
			// Given
			when(mongoTemplate.count(any(Query.class), eq(HintDao.class))).thenReturn(0L);

			// When
			long count = migrationService.countMongoHints(null, null);

			// Then
			assertThat(count).isEqualTo(0L);
		}
	}

	@Nested
	@DisplayName("validateMigration Tests")
	class ValidateMigration {

		@Test
		@DisplayName("should return failure when job not found")
		void shouldReturnFailureWhenJobNotFound() {
			// Given
			when(migrationJobRepo.findById(999L)).thenReturn(Optional.empty());

			// When
			ValidationResult result = migrationService.validateMigration(999L);

			// Then
			assertThat(result.successful()).isFalse();
			assertThat(result.message()).contains("Job with ID 999 not found");
		}

		private static Stream<Arguments> provideNonCompletedStates() {
			return Stream.of(
				Arguments.of(MigrationJobEntity.STATE.RUNNING, "RUNNING"),
				Arguments.of(MigrationJobEntity.STATE.BROKEN, "BROKEN")
			);
		}

		@ParameterizedTest(name = "state = {1}")
		@MethodSource("provideNonCompletedStates")
		@DisplayName("should return failure when job state is not COMPLETED")
		void shouldReturnFailureWhenJobNotCompleted(MigrationJobEntity.STATE state, String stateName) {
			// Given
			MigrationJobEntity job = MigrationJobEntity.builder()
				.id(1L)
				.state(state)
				.build();
			when(migrationJobRepo.findById(1L)).thenReturn(Optional.of(job));

			// When
			ValidationResult result = migrationService.validateMigration(1L);

			// Then
			assertThat(result.successful()).isFalse();
			assertThat(result.message()).contains("Job state is " + stateName + ", not COMPLETED");
		}

		@Test
		@DisplayName("should return failure when unresolved errors exist")
		void shouldReturnFailureWhenUnresolvedErrorsExist() {
			// Given
			MigrationJobEntity job = MigrationJobEntity.builder()
				.id(1L)
				.state(MigrationJobEntity.STATE.COMPLETED)
				.build();
			List<MigrationErrorEntity> unresolvedErrors = List.of(
				MigrationTestDataGenerator.createUnresolvedError(job),
				MigrationTestDataGenerator.createUnresolvedError(job)
			);

			when(migrationJobRepo.findById(1L)).thenReturn(Optional.of(job));
			when(migrationErrorRepo.findByJob_IdAndResolved(1L, false)).thenReturn(unresolvedErrors);

			// When
			ValidationResult result = migrationService.validateMigration(1L);

			// Then
			assertThat(result.successful()).isFalse();
			assertThat(result.message()).contains("Found 2 unresolved errors");
		}

		@Test
		@DisplayName("should return failure when item count mismatch")
		void shouldReturnFailureWhenItemCountMismatch() {
			// Given
			MigrationJobEntity job = MigrationJobEntity.builder()
				.id(1L)
				.state(MigrationJobEntity.STATE.COMPLETED)
				.totalItems(100L)
				.build();

			when(migrationJobRepo.findById(1L)).thenReturn(Optional.of(job));
			when(migrationErrorRepo.findByJob_IdAndResolved(1L, false)).thenReturn(Collections.emptyList());
			when(hintRepository.countByMongoUUIDIsNotNull()).thenReturn(95L);

			// When
			ValidationResult result = migrationService.validateMigration(1L);

			// Then
			assertThat(result.successful()).isFalse();
			assertThat(result.message()).contains("Item count mismatch. Expected: 100, Found in PostgreSQL: 95");
		}

		@Test
		@DisplayName("should return success when validation passes")
		void shouldReturnSuccessWhenValidationPasses() {
			// Given
			MigrationJobEntity job = MigrationJobEntity.builder()
				.id(1L)
				.state(MigrationJobEntity.STATE.COMPLETED)
				.totalItems(100L)
				.build();

			when(migrationJobRepo.findById(1L)).thenReturn(Optional.of(job));
			when(migrationErrorRepo.findByJob_IdAndResolved(1L, false)).thenReturn(Collections.emptyList());
			when(hintRepository.countByMongoUUIDIsNotNull()).thenReturn(100L);

			// When
			ValidationResult result = migrationService.validateMigration(1L);

			// Then
			assertThat(result.successful()).isTrue();
			assertThat(result.message()).contains("validated successfully");
		}

		@Test
		@DisplayName("should return success when totalItems is null")
		void shouldReturnSuccessWhenTotalItemsIsNull() {
			// Given
			MigrationJobEntity job = MigrationJobEntity.builder()
				.id(1L)
				.state(MigrationJobEntity.STATE.COMPLETED)
				.totalItems(null)
				.build();

			when(migrationJobRepo.findById(1L)).thenReturn(Optional.of(job));
			when(migrationErrorRepo.findByJob_IdAndResolved(1L, false)).thenReturn(Collections.emptyList());

			// When
			ValidationResult result = migrationService.validateMigration(1L);

			// Then
			assertThat(result.successful()).isTrue();
			assertThat(result.message()).contains("validated successfully");
			verify(hintRepository, never()).countByMongoUUIDIsNotNull();
		}
	}

	@Nested
	@DisplayName("fixUnresolvedErrors Edge Cases")
	class FixUnresolvedErrorsEdgeCases {

		@Test
		@DisplayName("should complete when no unresolved errors exist")
		void shouldCompleteWhenNoUnresolvedErrors() {
			// Given
			when(migrationErrorRepo.findByJob_IdAndResolved(2L, false)).thenReturn(Collections.emptyList());

			// When
			migrationService.fixUnresolvedErrors(testJob, 2L);

			// Then
			verify(migrationJobRepo).save(testJob);
			assertThat(testJob.getState()).isEqualTo(MigrationJobEntity.STATE.COMPLETED);
			verify(hintRepository, never()).existsByMongoUUID(any());
			verify(mongoTemplate, never()).findById(anyString(), any());
		}

		@Test
		@DisplayName("should handle multiple errors with mixed outcomes")
		void shouldHandleMultipleErrorsWithMixedOutcomes() {
			// Given
			MigrationJobEntity oldJob = MigrationJobEntity.builder().id(2L).build();
			
			HintDao existingHint = HintTestDataGenerator.createHintDaoWithId("existing123");
			HintDao newHint = HintTestDataGenerator.createHintDaoWithId("new456");
			
			MigrationErrorEntity existingError = MigrationErrorEntity.builder()
				.mongoUUID(existingHint.id())
				.resolved(false)
				.job(oldJob)
				.build();
				
			MigrationErrorEntity newError = MigrationErrorEntity.builder()
				.mongoUUID(newHint.id())
				.resolved(false)
				.job(oldJob)
				.build();

			when(migrationErrorRepo.findByJob_IdAndResolved(2L, false))
				.thenReturn(List.of(existingError, newError));
			when(hintRepository.existsByMongoUUID(existingHint.id())).thenReturn(true);
			when(hintRepository.existsByMongoUUID(newHint.id())).thenReturn(false);
			when(mongoTemplate.findById(newHint.id(), HintDao.class)).thenReturn(newHint);

			// When
			migrationService.fixUnresolvedErrors(testJob, 2L);

			// Then
			verify(hintRepository, times(1)).save(any());
			verify(migrationErrorRepo, times(2)).save(any(MigrationErrorEntity.class));
		}
	}

	@Nested
	@DisplayName("startMigration Edge Cases")
	class StartMigrationEdgeCases {

		@Test
		@DisplayName("should update processed items correctly during migration")
		void shouldUpdateProcessedItemsCorrectly() throws Exception {
			// Given
			List<HintDao> hints = List.of(
				HintTestDataGenerator.createHintDaoWithId("id1"),
				HintTestDataGenerator.createHintDaoWithId("id2"),
				HintTestDataGenerator.createHintDaoWithId("id3")
			);
			
			when(mongoTemplate.find(any(Query.class), eq(HintDao.class)))
				.thenReturn(hints)
				.thenReturn(Collections.emptyList());
			when(mongoTemplate.count(any(Query.class), eq(HintDao.class)))
				.thenReturn((long) hints.size())
				.thenReturn((long) hints.size())
				.thenReturn(0L);
			when(hintRepository.existsByMongoUUID(anyString())).thenReturn(false);

			// When
			migrationService.startMigration(testJob).get();

			// Then
			verify(migrationJobRepo, atLeast(2)).save(any(MigrationJobEntity.class));
			verify(hintRepository, times(3)).save(any());
		}

		@Test
		@DisplayName("should handle empty first page")
		void shouldHandleEmptyFirstPage() throws Exception {
			// Given
			when(mongoTemplate.find(any(Query.class), eq(HintDao.class)))
				.thenReturn(Collections.emptyList());
			when(mongoTemplate.count(any(Query.class), eq(HintDao.class))).thenReturn(0L);
			// When
			migrationService.startMigration(testJob).get();
			// Then
			verify(hintRepository, never()).save(any());
			verify(migrationJobRepo, atLeast(1)).save(any(MigrationJobEntity.class));
		}

		@Test
		@DisplayName("should handle hint with null process id gracefully")
		void shouldHandleHintWithNullProcessId() throws Exception {
			// Given
			HintDao hintWithNullProcessId = new HintDao(
				"mongoId123",
				"source",
				"message",
				de.signaliduna.elpa.hint.model.HintDto.Category.INFO,
				true,
				null, // null processId
				LocalDateTime.now(),
				"1",
				"resourceId"
			);

			when(mongoTemplate.find(any(Query.class), eq(HintDao.class)))
				.thenReturn(List.of(hintWithNullProcessId))
				.thenReturn(Collections.emptyList());
			when(mongoTemplate.count(any(Query.class), eq(HintDao.class))).thenReturn(1L);
			when(hintRepository.existsByMongoUUID(anyString())).thenReturn(false);
			when(hintRepository.save(any())).thenThrow(new RuntimeException("Null process id"));

			// When
			migrationService.startMigration(testJob).get();

			// Then
			verify(migrationErrorRepo).save(any(MigrationErrorEntity.class));
		}
	}

	@Nested
	@DisplayName("Error Resolution Tests")
	class ErrorResolutionTests {

		@Test
		@DisplayName("should resolve previously unresolved error on successful migration")
		void shouldResolvePreviouslyUnresolvedError() {
			// Given
			MigrationJobEntity oldJob = MigrationJobEntity.builder().id(2L).build();
			HintDao hintToFix = HintTestDataGenerator.createHintDaoWithId("resolveMe");
			MigrationErrorEntity error = MigrationErrorEntity.builder()
				.mongoUUID(hintToFix.id())
				.resolved(false)
				.job(oldJob)
				.build();

			when(migrationErrorRepo.findByJob_IdAndResolved(2L, false)).thenReturn(List.of(error));
			when(hintRepository.existsByMongoUUID(hintToFix.id())).thenReturn(false);
			when(mongoTemplate.findById(hintToFix.id(), HintDao.class)).thenReturn(hintToFix);

			// When
			migrationService.fixUnresolvedErrors(testJob, 2L);

			// Then
			verify(hintRepository).save(any());
			verify(migrationErrorRepo).save(argThat(e -> e.getResolved() == true));
		}

		@Test
		@DisplayName("should not create new error when hint already exists in postgres during fix")
		void shouldNotCreateNewErrorWhenHintExists() {
			// Given
			MigrationJobEntity oldJob = MigrationJobEntity.builder().id(2L).build();
			MigrationErrorEntity error = MigrationErrorEntity.builder()
				.mongoUUID("alreadyExists")
				.resolved(false)
				.job(oldJob)
				.build();

			when(migrationErrorRepo.findByJob_IdAndResolved(2L, false)).thenReturn(List.of(error));
			when(hintRepository.existsByMongoUUID("alreadyExists")).thenReturn(true);

			// When
			migrationService.fixUnresolvedErrors(testJob, 2L);

			// Then
			verify(hintRepository, never()).save(any());
			verify(migrationErrorRepo).save(argThat(e -> e.getResolved() == true && e.getMongoUUID().equals("alreadyExists")));
			verify(migrationJobRepo).save(argThat(j -> j.getState() == MigrationJobEntity.STATE.COMPLETED));
		}
	}
}
