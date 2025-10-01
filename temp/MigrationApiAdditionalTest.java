package de.signaliduna.elpa.hint.adapter.http.api;

import com.c4_soft.springaddons.security.oauth2.test.annotations.WithJwt;
import com.c4_soft.springaddons.security.oauth2.test.webmvc.AutoConfigureAddonsWebmvcResourceServerSecurity;
import de.signaliduna.elpa.hint.adapter.database.MigrationErrorRepo;
import de.signaliduna.elpa.hint.adapter.database.MigrationJobRepo;
import de.signaliduna.elpa.hint.adapter.database.model.MigrationErrorEntity;
import de.signaliduna.elpa.hint.adapter.database.model.MigrationJobEntity;
import de.signaliduna.elpa.hint.config.WebSecurityConfig;
import de.signaliduna.elpa.hint.core.MigrationService;
import de.signaliduna.elpa.hint.core.model.ValidationResult;
import de.signaliduna.elpa.hint.util.MigrationTestDataGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = MigrationApi.class, properties = {
	"authorization.users=HINT_USER",
	"authorization.migration_users=MIGRATION_USER"
})
@AutoConfigureAddonsWebmvcResourceServerSecurity
@Import(WebSecurityConfig.class)
@DisplayName("MigrationApi Additional Tests for 100% Coverage")
class MigrationApiAdditionalTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private MigrationService migrationService;

	@MockitoBean
	private MigrationJobRepo migrationJobRepo;

	@MockitoBean
	private MigrationErrorRepo migrationErrorRepo;

	private static final String MIGRATION_USER_JWT = "jwt/migration-user-access-token.json";
	private static final String REGULAR_USER_JWT = "jwt/normal-user-access-token.json";

	@Nested
	@DisplayName("POST /migration/count Tests")
	class CountMigrationItems {

		@Test
		@DisplayName("should count migration items without date range")
		@WithJwt(MIGRATION_USER_JWT)
		void shouldCountWithoutDateRange() throws Exception {
			// Given
			when(migrationService.countMongoHints(null, null)).thenReturn(1000L);

			// When & Then
			mockMvc.perform(post("/migration/count"))
				.andExpect(status().isAccepted())
				.andExpect(content().string("1000"));

			verify(migrationService).countMongoHints(null, null);
		}

		@Test
		@DisplayName("should count migration items with start date only")
		@WithJwt(MIGRATION_USER_JWT)
		void shouldCountWithStartDateOnly() throws Exception {
			// Given
			LocalDateTime startDate = LocalDateTime.of(2024, 1, 1, 0, 0, 0);
			when(migrationService.countMongoHints(any(LocalDateTime.class), eq(null))).thenReturn(500L);

			// When & Then
			mockMvc.perform(post("/migration/count")
					.param("dataSetStartDate", "2024-01-01T00:00:00"))
				.andExpect(status().isAccepted())
				.andExpect(content().string("500"));

			verify(migrationService).countMongoHints(any(LocalDateTime.class), eq(null));
		}

		@Test
		@DisplayName("should count migration items with end date only")
		@WithJwt(MIGRATION_USER_JWT)
		void shouldCountWithEndDateOnly() throws Exception {
			// Given
			LocalDateTime endDate = LocalDateTime.of(2024, 12, 31, 23, 59, 59);
			when(migrationService.countMongoHints(eq(null), any(LocalDateTime.class))).thenReturn(750L);

			// When & Then
			mockMvc.perform(post("/migration/count")
					.param("dataSetEndDate", "2024-12-31T23:59:59"))
				.andExpect(status().isAccepted())
				.andExpect(content().string("750"));

			verify(migrationService).countMongoHints(eq(null), any(LocalDateTime.class));
		}

		@Test
		@DisplayName("should count migration items with both dates")
		@WithJwt(MIGRATION_USER_JWT)
		void shouldCountWithBothDates() throws Exception {
			// Given
			when(migrationService.countMongoHints(any(LocalDateTime.class), any(LocalDateTime.class)))
				.thenReturn(250L);

			// When & Then
			mockMvc.perform(post("/migration/count")
					.param("dataSetStartDate", "2024-01-01T00:00:00")
					.param("dataSetEndDate", "2024-01-31T23:59:59"))
				.andExpect(status().isAccepted())
				.andExpect(content().string("250"));

			verify(migrationService).countMongoHints(any(LocalDateTime.class), any(LocalDateTime.class));
		}

		@Test
		@DisplayName("should be forbidden for regular user")
		@WithJwt(REGULAR_USER_JWT)
		void shouldBeForbiddenForRegularUser() throws Exception {
			mockMvc.perform(post("/migration/count"))
				.andExpect(status().isForbidden());

			verifyNoInteractions(migrationService);
		}

		@Test
		@DisplayName("should return zero when no items to migrate")
		@WithJwt(MIGRATION_USER_JWT)
		void shouldReturnZeroWhenEmpty() throws Exception {
			// Given
			when(migrationService.countMongoHints(null, null)).thenReturn(0L);

			// When & Then
			mockMvc.perform(post("/migration/count"))
				.andExpect(status().isAccepted())
				.andExpect(content().string("0"));
		}
	}

	@Nested
	@DisplayName("POST /migration/start Tests")
	class StartMigration {

		@Test
		@DisplayName("should start migration with only start date")
		@WithJwt(MIGRATION_USER_JWT)
		void shouldStartWithOnlyStartDate() throws Exception {
			// Given
			MigrationJobEntity savedJob = MigrationTestDataGenerator.createJobWithId(5L);
			when(migrationJobRepo.save(any(MigrationJobEntity.class))).thenReturn(savedJob);
			when(migrationService.startMigration(any(MigrationJobEntity.class)))
				.thenReturn(java.util.concurrent.CompletableFuture.completedFuture(5L));

			// When & Then
			mockMvc.perform(post("/migration/start")
					.param("dataSetStartDate", "2024-01-01T00:00:00"))
				.andExpect(status().isAccepted())
				.andExpect(content().string("5"));

			verify(migrationJobRepo).save(any(MigrationJobEntity.class));
			verify(migrationService).startMigration(any(MigrationJobEntity.class));
		}

		@Test
		@DisplayName("should start migration with only end date")
		@WithJwt(MIGRATION_USER_JWT)
		void shouldStartWithOnlyEndDate() throws Exception {
			// Given
			MigrationJobEntity savedJob = MigrationTestDataGenerator.createJobWithId(6L);
			when(migrationJobRepo.save(any(MigrationJobEntity.class))).thenReturn(savedJob);
			when(migrationService.startMigration(any(MigrationJobEntity.class)))
				.thenReturn(java.util.concurrent.CompletableFuture.completedFuture(6L));

			// When & Then
			mockMvc.perform(post("/migration/start")
					.param("dataSetEndDate", "2024-12-31T23:59:59"))
				.andExpect(status().isAccepted())
				.andExpect(content().string("6"));
		}

		@Test
		@DisplayName("should handle large date range")
		@WithJwt(MIGRATION_USER_JWT)
		void shouldHandleLargeDateRange() throws Exception {
			// Given
			MigrationJobEntity savedJob = MigrationTestDataGenerator.createJobWithId(7L);
			when(migrationJobRepo.save(any(MigrationJobEntity.class))).thenReturn(savedJob);
			when(migrationService.startMigration(any(MigrationJobEntity.class)))
				.thenReturn(java.util.concurrent.CompletableFuture.completedFuture(7L));

			// When & Then
			mockMvc.perform(post("/migration/start")
					.param("dataSetStartDate", "2020-01-01T00:00:00")
					.param("dataSetEndDate", "2024-12-31T23:59:59"))
				.andExpect(status().isAccepted())
				.andExpect(content().string("7"));
		}
	}

	@Nested
	@DisplayName("GET /migration/validate/{jobId} Tests")
	class ValidateMigration {

		@Test
		@DisplayName("should return OK when validation is successful")
		@WithJwt(MIGRATION_USER_JWT)
		void shouldReturnOkWhenValidationSuccessful() throws Exception {
			// Given
			ValidationResult successResult = new ValidationResult(true, "Validation passed");
			when(migrationService.validateMigration(1L)).thenReturn(successResult);

			// When & Then
			mockMvc.perform(get("/migration/validate/1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.successful").value(true))
				.andExpect(jsonPath("$.message").value("Validation passed"));

			verify(migrationService).validateMigration(1L);
		}

		@Test
		@DisplayName("should return EXPECTATION_FAILED when validation fails")
		@WithJwt(MIGRATION_USER_JWT)
		void shouldReturnExpectationFailedWhenValidationFails() throws Exception {
			// Given
			ValidationResult failureResult = new ValidationResult(false, "Validation failed");
			when(migrationService.validateMigration(2L)).thenReturn(failureResult);

			// When & Then
			mockMvc.perform(get("/migration/validate/2"))
				.andExpect(status().isExpectationFailed())
				.andExpect(jsonPath("$.successful").value(false))
				.andExpect(jsonPath("$.message").value("Validation failed"));

			verify(migrationService).validateMigration(2L);
		}

		@Test
		@DisplayName("should be forbidden for regular user")
		@WithJwt(REGULAR_USER_JWT)
		void shouldBeForbiddenForRegularUser() throws Exception {
			mockMvc.perform(get("/migration/validate/1"))
				.andExpect(status().isForbidden());

			verifyNoInteractions(migrationService);
		}

		private static Stream<Arguments> provideValidationScenarios() {
			return Stream.of(
				Arguments.of(true, "All checks passed", 200),
				Arguments.of(false, "Job not found", 417),
				Arguments.of(false, "Unresolved errors exist", 417),
				Arguments.of(false, "Item count mismatch", 417)
			);
		}

		@ParameterizedTest(name = "successful={0}, message={1}, expectedStatus={2}")
		@MethodSource("provideValidationScenarios")
		@DisplayName("should handle different validation scenarios")
		@WithJwt(MIGRATION_USER_JWT)
		void shouldHandleDifferentValidationScenarios(boolean successful, String message, int expectedStatus) 
				throws Exception {
			// Given
			ValidationResult result = new ValidationResult(successful, message);
			when(migrationService.validateMigration(anyLong())).thenReturn(result);

			// When & Then
			mockMvc.perform(get("/migration/validate/1"))
				.andExpect(status().is(expectedStatus))
				.andExpect(jsonPath("$.successful").value(successful))
				.andExpect(jsonPath("$.message").value(message));
		}
	}

	@Nested
	@DisplayName("GET /migration/jobs Tests")
	class GetJobs {

		@Test
		@DisplayName("should return all jobs")
		@WithJwt(MIGRATION_USER_JWT)
		void shouldReturnAllJobs() throws Exception {
			// Given
			List<MigrationJobEntity> jobs = List.of(
				MigrationTestDataGenerator.createRunningJob(),
				MigrationTestDataGenerator.createCompletedJob(),
				MigrationTestDataGenerator.createBrokenJob()
			);
			when(migrationJobRepo.findAll()).thenReturn(jobs);

			// When & Then
			mockMvc.perform(get("/migration/jobs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(3));

			verify(migrationJobRepo).findAll();
		}

		@Test
		@DisplayName("should return empty list when no jobs exist")
		@WithJwt(MIGRATION_USER_JWT)
		void shouldReturnEmptyListWhenNoJobs() throws Exception {
			// Given
			when(migrationJobRepo.findAll()).thenReturn(List.of());

			// When & Then
			mockMvc.perform(get("/migration/jobs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
		}
	}

	@Nested
	@DisplayName("GET /migration/jobs/{jobId} Tests")
	class GetJobById {

		@Test
		@DisplayName("should return job when found")
		@WithJwt(MIGRATION_USER_JWT)
		void shouldReturnJobWhenFound() throws Exception {
			// Given
			MigrationJobEntity job = MigrationTestDataGenerator.createCompletedJob();
			when(migrationJobRepo.findById(job.getId())).thenReturn(Optional.of(job));

			// When & Then
			mockMvc.perform(get("/migration/jobs/" + job.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(job.getId()))
				.andExpect(jsonPath("$.state").value("COMPLETED"));
		}

		@Test
		@DisplayName("should return 404 when job not found")
		@WithJwt(MIGRATION_USER_JWT)
		void shouldReturn404WhenJobNotFound() throws Exception {
			// Given
			when(migrationJobRepo.findById(999L)).thenReturn(Optional.empty());

			// When & Then
			mockMvc.perform(get("/migration/jobs/999"))
				.andExpect(status().isNotFound());
		}

		private static Stream<Arguments> provideJobStates() {
			return Stream.of(
				Arguments.of(MigrationJobEntity.STATE.RUNNING, "RUNNING"),
				Arguments.of(MigrationJobEntity.STATE.COMPLETED, "COMPLETED"),
				Arguments.of(MigrationJobEntity.STATE.BROKEN, "BROKEN")
			);
		}

		@ParameterizedTest(name = "state={1}")
		@MethodSource("provideJobStates")
		@DisplayName("should return jobs in different states")
		@WithJwt(MIGRATION_USER_JWT)
		void shouldReturnJobsInDifferentStates(MigrationJobEntity.STATE state, String stateName) 
				throws Exception {
			// Given
			MigrationJobEntity job = MigrationJobEntity.builder()
				.id(1L)
				.state(state)
				.creationDate(LocalDateTime.now())
				.build();
			when(migrationJobRepo.findById(1L)).thenReturn(Optional.of(job));

			// When & Then
			mockMvc.perform(get("/migration/jobs/1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.state").value(stateName));
		}
	}

	@Nested
	@DisplayName("GET /migration/errors Tests")
	class GetErrors {

		@Test
		@DisplayName("should return all errors")
		@WithJwt(MIGRATION_USER_JWT)
		void shouldReturnAllErrors() throws Exception {
			// Given
			MigrationJobEntity job = MigrationTestDataGenerator.createBrokenJob();
			List<MigrationErrorEntity> errors = List.of(
				MigrationTestDataGenerator.createUnresolvedError(job),
				MigrationTestDataGenerator.createResolvedError(job)
			);
			when(migrationErrorRepo.findAll()).thenReturn(errors);

			// When & Then
			mockMvc.perform(get("/migration/errors"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2));

			verify(migrationErrorRepo).findAll();
		}

		@Test
		@DisplayName("should return empty list when no errors exist")
		@WithJwt(MIGRATION_USER_JWT)
		void shouldReturnEmptyListWhenNoErrors() throws Exception {
			// Given
			when(migrationErrorRepo.findAll()).thenReturn(List.of());

			// When & Then
			mockMvc.perform(get("/migration/errors"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
		}
	}

	@Nested
	@DisplayName("GET /migration/errors/{errorId} Tests")
	class GetErrorById {

		@Test
		@DisplayName("should return error when found")
		@WithJwt(MIGRATION_USER_JWT)
		void shouldReturnErrorWhenFound() throws Exception {
			// Given
			MigrationJobEntity job = MigrationTestDataGenerator.createBrokenJob();
			MigrationErrorEntity error = MigrationTestDataGenerator.createErrorWithMongoId("mongo123", job);
			when(migrationErrorRepo.findById(1L)).thenReturn(Optional.of(error));

			// When & Then
			mockMvc.perform(get("/migration/errors/1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.mongoUUID").value("mongo123"));
		}

		@Test
		@DisplayName("should return 404 when error not found")
		@WithJwt(MIGRATION_USER_JWT)
		void shouldReturn404WhenErrorNotFound() throws Exception {
			// Given
			when(migrationErrorRepo.findById(999L)).thenReturn(Optional.empty());

			// When & Then
			mockMvc.perform(get("/migration/errors/999"))
				.andExpect(status().isNotFound());
		}

		@Test
		@DisplayName("should return resolved error")
		@WithJwt(MIGRATION_USER_JWT)
		void shouldReturnResolvedError() throws Exception {
			// Given
			MigrationJobEntity job = MigrationTestDataGenerator.createCompletedJob();
			MigrationErrorEntity error = MigrationTestDataGenerator.createResolvedError(job);
			when(migrationErrorRepo.findById(1L)).thenReturn(Optional.of(error));

			// When & Then
			mockMvc.perform(get("/migration/errors/1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.resolved").value(true));
		}
	}

	@Nested
	@DisplayName("POST /migration/fix/{jobId} Tests")
	class FixErrors {

		@Test
		@DisplayName("should fix errors successfully")
		@WithJwt(MIGRATION_USER_JWT)
		void shouldFixErrorsSuccessfully() throws Exception {
			// Given
			MigrationJobEntity newJob = MigrationTestDataGenerator.createJobWithId(10L);
			when(migrationJobRepo.save(any(MigrationJobEntity.class))).thenReturn(newJob);

			// When & Then
			mockMvc.perform(post("/migration/fix/5"))
				.andExpect(status().isAccepted())
				.andExpect(content().string("10"));

			verify(migrationService).fixUnresolvedErrors(any(MigrationJobEntity.class), eq(5L));
		}

		@Test
		@DisplayName("should handle fixing errors for non-existent job")
		@WithJwt(MIGRATION_USER_JWT)
		void shouldHandleFixingForNonExistentJob() throws Exception {
			// Given
			MigrationJobEntity newJob = MigrationTestDataGenerator.createJobWithId(11L);
			when(migrationJobRepo.save(any(MigrationJobEntity.class))).thenReturn(newJob);

			// When & Then
			mockMvc.perform(post("/migration/fix/999"))
				.andExpect(status().isAccepted())
				.andExpect(content().string("11"));

			verify(migrationService).fixUnresolvedErrors(any(MigrationJobEntity.class), eq(999L));
		}

		@Test
		@DisplayName("should be forbidden for regular user")
		@WithJwt(REGULAR_USER_JWT)
		void shouldBeForbiddenForRegularUser() throws Exception {
			mockMvc.perform(post("/migration/fix/1"))
				.andExpect(status().isForbidden());

			verifyNoInteractions(migrationService);
		}
	}

	@Nested
	@DisplayName("Authorization Tests")
	class AuthorizationTests {

		private static Stream<Arguments> provideEndpointsForAuthorization() {
			return Stream.of(
				Arguments.of("POST", "/migration/count"),
				Arguments.of("POST", "/migration/start"),
				Arguments.of("GET", "/migration/validate/1"),
				Arguments.of("GET", "/migration/jobs"),
				Arguments.of("GET", "/migration/jobs/1"),
				Arguments.of("GET", "/migration/errors"),
				Arguments.of("GET", "/migration/errors/1"),
				Arguments.of("POST", "/migration/fix/1")
			);
		}

		@ParameterizedTest(name = "{0} {1}")
		@MethodSource("provideEndpointsForAuthorization")
		@DisplayName("should require authentication for all endpoints")
		void shouldRequireAuthenticationForAllEndpoints(String method, String endpoint) throws Exception {
			if ("POST".equals(method)) {
				mockMvc.perform(post(endpoint))
					.andExpect(status().isUnauthorized());
			} else {
				mockMvc.perform(get(endpoint))
					.andExpect(status().isUnauthorized());
			}
		}

		@ParameterizedTest(name = "{0} {1}")
		@MethodSource("provideEndpointsForAuthorization")
		@DisplayName("should forbid regular users from all migration endpoints")
		@WithJwt(REGULAR_USER_JWT)
		void shouldForbidRegularUsersFromAllEndpoints(String method, String endpoint) throws Exception {
			if ("POST".equals(method)) {
				mockMvc.perform(post(endpoint))
					.andExpect(status().isForbidden());
			} else {
				mockMvc.perform(get(endpoint))
					.andExpect(status().isForbidden());
			}
		}
	}
}
