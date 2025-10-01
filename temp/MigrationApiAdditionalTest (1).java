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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Additional tests for MigrationApi to achieve 100% code coverage.
 * This file complements MigrationApiTest.java and adds missing test cases.
 */
@WebMvcTest(controllers = MigrationApi.class, properties = {
	"authorization.users=HINT_USER",
	"authorization.migration_users=MIGRATION_USER"
})
@AutoConfigureAddonsWebmvcResourceServerSecurity
@Import(WebSecurityConfig.class)
@DisplayName("MigrationApi Additional Coverage Tests")
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
	@DisplayName("POST /migration/count - Additional Cases")
	class CountMigrationItemsAdditional {

		@Test
		@DisplayName("should count without any date parameters")
		@WithJwt(MIGRATION_USER_JWT)
		void shouldCountWithoutDates() throws Exception {
			// Given
			when(migrationService.countMongoHints(null, null)).thenReturn(1000L);

			// When & Then
			mockMvc.perform(post("/migration/count"))
				.andExpect(status().isAccepted())
				.andExpect(content().string("1000"));

			verify(migrationService).countMongoHints(null, null);
		}

		@Test
		@DisplayName("should count with only start date")
		@WithJwt(MIGRATION_USER_JWT)
		void shouldCountWithOnlyStartDate() throws Exception {
			// Given
			when(migrationService.countMongoHints(any(LocalDateTime.class), eq(null))).thenReturn(500L);

			// When & Then
			mockMvc.perform(post("/migration/count")
					.param("dataSetStartDate", "2024-01-01T00:00:00"))
				.andExpect(status().isAccepted())
				.andExpect(content().string("500"));

			verify(migrationService).countMongoHints(any(LocalDateTime.class), eq(null));
		}

		@Test
		@DisplayName("should count with only end date")
		@WithJwt(MIGRATION_USER_JWT)
		void shouldCountWithOnlyEndDate() throws Exception {
			// Given
			when(migrationService.countMongoHints(eq(null), any(LocalDateTime.class))).thenReturn(750L);

			// When & Then
			mockMvc.perform(post("/migration/count")
					.param("dataSetEndDate", "2024-12-31T23:59:59"))
				.andExpect(status().isAccepted())
				.andExpect(content().string("750"));

			verify(migrationService).countMongoHints(eq(null), any(LocalDateTime.class));
		}

		@Test
		@DisplayName("should return zero when no items")
		@WithJwt(MIGRATION_USER_JWT)
		void shouldReturnZeroWhenNoItems() throws Exception {
			// Given
			when(migrationService.countMongoHints(null, null)).thenReturn(0L);

			// When & Then
			mockMvc.perform(post("/migration/count"))
				.andExpect(status().isAccepted())
				.andExpect(content().string("0"));
		}

		@Test
		@DisplayName("should be forbidden for regular user")
		@WithJwt(REGULAR_USER_JWT)
		void shouldBeForbidden() throws Exception {
			mockMvc.perform(post("/migration/count"))
				.andExpect(status().isForbidden());

			verifyNoInteractions(migrationService);
		}
	}

	@Nested
	@DisplayName("POST /migration/start - Additional Date Scenarios")
	class StartMigrationAdditional {

		@Test
		@DisplayName("should start with only start date")
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
		@DisplayName("should start with only end date")
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
	}

	@Nested
	@DisplayName("GET /migration/validate/{jobId} - All Validation Results")
	class ValidateMigrationAdditional {

		@Test
		@DisplayName("should return OK for successful validation")
		@WithJwt(MIGRATION_USER_JWT)
		void shouldReturnOkForSuccess() throws Exception {
			// Given
			ValidationResult result = new ValidationResult(true, "Validation passed");
			when(migrationService.validateMigration(1L)).thenReturn(result);

			// When & Then
			mockMvc.perform(get("/migration/validate/1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.successful").value(true))
				.andExpect(jsonPath("$.message").value("Validation passed"));
		}

		@Test
		@DisplayName("should return EXPECTATION_FAILED for failed validation")
		@WithJwt(MIGRATION_USER_JWT)
		void shouldReturnExpectationFailedForFailure() throws Exception {
			// Given
			ValidationResult result = new ValidationResult(false, "Validation failed");
			when(migrationService.validateMigration(2L)).thenReturn(result);

			// When & Then
			mockMvc.perform(get("/migration/validate/2"))
				.andExpect(status().isExpectationFailed())
				.andExpect(jsonPath("$.successful").value(false))
				.andExpect(jsonPath("$.message").value("Validation failed"));
		}

		private static Stream<Arguments> provideValidationScenarios() {
			return Stream.of(
				Arguments.of(true, "All checks passed", 200),
				Arguments.of(false, "Job not found", 417),
				Arguments.of(false, "Unresolved errors exist", 417),
				Arguments.of(false, "Item count mismatch", 417)
			);
		}

		@ParameterizedTest(name = "successful={0}, expectedStatus={2}")
		@MethodSource("provideValidationScenarios")
		@DisplayName("should handle different validation scenarios")
		@WithJwt(MIGRATION_USER_JWT)
		void shouldHandleValidationScenarios(boolean successful, String message, int expectedStatus) 
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
	@DisplayName("GET /migration/jobs - Additional Cases")
	class GetJobsAdditional {

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
		@DisplayName("should return empty list when no jobs")
		@WithJwt(MIGRATION_USER_JWT)
		void shouldReturnEmptyList() throws Exception {
			// Given
			when(migrationJobRepo.findAll()).thenReturn(List.of());

			// When & Then
			mockMvc.perform(get("/migration/jobs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
		}
	}

	@Nested
	@DisplayName("GET /migration/jobs/{jobId} - Additional Cases")
	class GetJobByIdAdditional {

		private static Stream<Arguments> provideJobStates() {
			return Stream.of(
				Arguments.of(MigrationJobEntity.STATE.RUNNING, "RUNNING"),
				Arguments.of(MigrationJobEntity.STATE.COMPLETED, "COMPLETED"),
				Arguments.of(MigrationJobEntity.STATE.BROKEN, "BROKEN")
			);
		}

		@ParameterizedTest(name = "state={1}")
		@MethodSource("provideJobStates")
		@DisplayName("should return jobs in all states")
		@WithJwt(MIGRATION_USER_JWT)
		void shouldReturnJobsInAllStates(MigrationJobEntity.STATE state, String stateName) 
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
	@DisplayName("GET /migration/errors - Additional Cases")
	class GetErrorsAdditional {

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
		@DisplayName("should return empty list when no errors")
		@WithJwt(MIGRATION_USER_JWT)
		void shouldReturnEmptyList() throws Exception {
			// Given
			when(migrationErrorRepo.findAll()).thenReturn(List.of());

			// When & Then
			mockMvc.perform(get("/migration/errors"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
		}
	}

	@Nested
	@DisplayName("GET /migration/errors/{errorId} - Additional Cases")
	class GetErrorByIdAdditional {

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

		@Test
		@DisplayName("should return unresolved error")
		@WithJwt(MIGRATION_USER_JWT)
		void shouldReturnUnresolvedError() throws Exception {
			// Given
			MigrationJobEntity job = MigrationTestDataGenerator.createBrokenJob();
			MigrationErrorEntity error = MigrationTestDataGenerator.createUnresolvedError(job);
			when(migrationErrorRepo.findById(2L)).thenReturn(Optional.of(error));

			// When & Then
			mockMvc.perform(get("/migration/errors/2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.resolved").value(false));
		}
	}

	@Nested
	@DisplayName("POST /migration/fix/{jobId} - Additional Cases")
	class FixErrorsAdditional {

		@Test
		@DisplayName("should handle fixing non-existent job")
		@WithJwt(MIGRATION_USER_JWT)
		void shouldHandleNonExistentJob() throws Exception {
			// Given
			MigrationJobEntity newJob = MigrationTestDataGenerator.createJobWithId(11L);
			when(migrationJobRepo.save(any(MigrationJobEntity.class))).thenReturn(newJob);

			// When & Then
			mockMvc.perform(post("/migration/fix/999"))
				.andExpect(status().isAccepted())
				.andExpect(content().string("11"));

			verify(migrationService).fixUnresolvedErrors(any(MigrationJobEntity.class), eq(999L));
		}
	}

	@Nested
	@DisplayName("Authorization - Comprehensive Tests")
	class AuthorizationComprehensive {

		private static Stream<Arguments> provideEndpoints() {
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
		@MethodSource("provideEndpoints")
		@DisplayName("should require authentication")
		void shouldRequireAuthentication(String method, String endpoint) throws Exception {
			if ("POST".equals(method)) {
				mockMvc.perform(post(endpoint))
					.andExpect(status().isUnauthorized());
			} else {
				mockMvc.perform(get(endpoint))
					.andExpect(status().isUnauthorized());
			}
		}

		@ParameterizedTest(name = "{0} {1}")
		@MethodSource("provideEndpoints")
		@DisplayName("should forbid regular users")
		@WithJwt(REGULAR_USER_JWT)
		void shouldForbidRegularUsers(String method, String endpoint) throws Exception {
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
