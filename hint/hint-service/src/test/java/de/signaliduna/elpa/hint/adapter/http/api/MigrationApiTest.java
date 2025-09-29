package de.signaliduna.elpa.hint.adapter.http.api;

import com.c4_soft.springaddons.security.oauth2.test.annotations.WithMockAuthentication;
import com.c4_soft.springaddons.security.oauth2.test.webmvc.AutoConfigureAddonsWebmvcResourceServerSecurity;
import de.signaliduna.elpa.hint.adapter.database.MigrationErrorRepo;
import de.signaliduna.elpa.hint.adapter.database.MigrationJobRepo;
import de.signaliduna.elpa.hint.adapter.database.model.MigrationJobEntity;
import de.signaliduna.elpa.hint.config.WebSecurityConfig;
import de.signaliduna.elpa.hint.core.MigrationService;
import de.signaliduna.elpa.hint.util.MigrationTestDataGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MigrationApi.class, properties = {
	"authorization.users=HINT_USER",
	"authorization.migration_users=MIGRATION_USER"
})
@AutoConfigureAddonsWebmvcResourceServerSecurity
@Import(WebSecurityConfig.class)
@DisplayName("MigrationApi Security and MVC Test")
class MigrationApiTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private MigrationService migrationService;
	@MockitoBean
	private MigrationJobRepo migrationJobRepo;
	@MockitoBean
	private MigrationErrorRepo migrationErrorRepo;

	private static final String MIGRATION_USER = "MIGRATION_USER";
	private static final String REGULAR_USER = "HINT_USER";

	@Test
	@DisplayName("POST /start - should be forbidden for regular user")
	@WithMockAuthentication(name = REGULAR_USER)
	void startMigration_forbidden() throws Exception {
		mockMvc.perform(post("/migration/start"))
			.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("POST /start - should be accepted for migration user")
	@WithMockAuthentication(name = MIGRATION_USER)
	void startMigration_accepted() throws Exception {
		// Create a saved job entity with ID
		MigrationJobEntity savedJob = MigrationTestDataGenerator.createJobWithId(1L);
		savedJob.setDataSetStartDate(LocalDateTime.of(2024, 1, 1, 0, 0, 0));
		savedJob.setDataSetStopDate(LocalDateTime.of(2024, 1, 31, 23, 59, 59));

		// Mock the save to return the job with ID
		when(migrationJobRepo.save(any(MigrationJobEntity.class))).thenReturn(savedJob);

		when(migrationService.startMigration(any(MigrationJobEntity.class)))
			.thenReturn(CompletableFuture.completedFuture(1L));

		mockMvc.perform(post("/migration/start")
				.param("dataSetStartDate", "2024-01-01T00:00:00")
				.param("dataSetEndDate", "2024-01-31T23:59:59"))
			.andExpect(status().isAccepted())
			.andExpect(content().string("1"));

		// Verify the job was saved with correct parameters
		ArgumentCaptor<MigrationJobEntity> jobCaptor = ArgumentCaptor.forClass(MigrationJobEntity.class);
		verify(migrationJobRepo).save(jobCaptor.capture());
		MigrationJobEntity capturedJob = jobCaptor.getValue();
		assertThat(capturedJob.getDataSetStartDate()).isEqualTo(LocalDateTime.of(2024, 1, 1, 0, 0, 0));
		assertThat(capturedJob.getDataSetStopDate()).isEqualTo(LocalDateTime.of(2024, 1, 31, 23, 59, 59));
		assertThat(capturedJob.getState()).isEqualTo(MigrationJobEntity.STATE.RUNNING);
	}

	@Test
	@DisplayName("POST /start - should handle migration without date range")
	@WithMockAuthentication(name = MIGRATION_USER)
	void startMigration_withoutDateRange() throws Exception {
		// Create a saved job without date range
		MigrationJobEntity savedJob = MigrationTestDataGenerator.createRunningJob();
		savedJob.setId(3L);
		savedJob.setDataSetStartDate(null);
		savedJob.setDataSetStopDate(null);

		when(migrationJobRepo.save(any(MigrationJobEntity.class))).thenReturn(savedJob);
		when(migrationService.startMigration(any(MigrationJobEntity.class)))
			.thenReturn(CompletableFuture.completedFuture(3L));

		mockMvc.perform(post("/migration/start"))
			.andExpect(status().isAccepted())
			.andExpect(content().string("3"));
	}

	@Test
	@DisplayName("GET /jobs - should be forbidden for regular user")
	@WithMockAuthentication(name = REGULAR_USER)
	void getJobs_forbidden() throws Exception {
		mockMvc.perform(get("/migration/jobs"))
			.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("GET /jobs - should return jobs for migration user")
	@WithMockAuthentication(name = MIGRATION_USER)
	void getJobs_ok() throws Exception {
		mockMvc.perform(get("/migration/jobs"))
			.andExpect(status().isOk());
	}

	@Test
	@DisplayName("GET /jobs/{jobId} - should return specific job for migration user")
	@WithMockAuthentication(name = MIGRATION_USER)
	void getJobById_ok() throws Exception {
		MigrationJobEntity completedJob = MigrationTestDataGenerator.createCompletedJob();
		when(migrationJobRepo.findById(completedJob.getId())).thenReturn(Optional.of(completedJob));

		mockMvc.perform(get("/migration/jobs/" + completedJob.getId()))
			.andExpect(status().isOk());
	}

	@Test
	@DisplayName("GET /jobs/{jobId} - should return 404 when job not found")
	@WithMockAuthentication(name = MIGRATION_USER)
	void getJobById_notFound() throws Exception {
		when(migrationJobRepo.findById(999L)).thenReturn(Optional.empty());

		mockMvc.perform(get("/migration/jobs/999"))
			.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("POST /fix/{jobId} - should be accepted for migration user")
	@WithMockAuthentication(name = MIGRATION_USER)
	void fixErrors_accepted() throws Exception {
		// Create a new job for fixing errors
		MigrationJobEntity newJob = MigrationTestDataGenerator.createJobWithId(2L);

		// Mock the save to return the job with ID
		when(migrationJobRepo.save(any(MigrationJobEntity.class))).thenReturn(newJob);

		mockMvc.perform(post("/migration/fix/1"))
			.andExpect(status().isAccepted())
			.andExpect(content().string("2"));

		verify(migrationService).fixUnresolvedErrors(any(MigrationJobEntity.class), eq(1L));

		// Verify the job was saved with correct state
		ArgumentCaptor<MigrationJobEntity> jobCaptor = ArgumentCaptor.forClass(MigrationJobEntity.class);
		verify(migrationJobRepo).save(jobCaptor.capture());
		MigrationJobEntity capturedJob = jobCaptor.getValue();
		assertThat(capturedJob.getState()).isEqualTo(MigrationJobEntity.STATE.RUNNING);
		assertThat(capturedJob.getCreationDate()).isNotNull();
	}

	@Test
	@DisplayName("POST /fix/{jobId} - should be forbidden for regular user")
	@WithMockAuthentication(name = REGULAR_USER)
	void fixErrors_forbidden() throws Exception {
		mockMvc.perform(post("/migration/fix/1"))
			.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("GET /errors - should be forbidden for regular user")
	@WithMockAuthentication(name = REGULAR_USER)
	void getErrors_forbidden() throws Exception {
		mockMvc.perform(get("/migration/errors"))
			.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("GET /errors - should return errors for migration user")
	@WithMockAuthentication(name = MIGRATION_USER)
	void getErrors_ok() throws Exception {
		mockMvc.perform(get("/migration/errors"))
			.andExpect(status().isOk());
	}

	@Test
	@DisplayName("GET /errors/{errorId} - should return specific error for migration user")
	@WithMockAuthentication(name = MIGRATION_USER)
	void getErrorById_ok() throws Exception {
		when(migrationErrorRepo.findById(1L)).thenReturn(Optional.of(MigrationTestDataGenerator.createErrorWithMongoId("1", MigrationTestDataGenerator.createCompletedJob())));
		mockMvc.perform(get("/migration/errors/1"))
			.andExpect(status().isOk());
	}

	@Test
	@DisplayName("All endpoints should be unauthorized without JWT")
	void allEndpoints_unauthorized() throws Exception {
		mockMvc.perform(post("/migration/start")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/migration/jobs")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/migration/jobs/1")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/migration/errors")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/migration/errors/1")).andExpect(status().isUnauthorized());
		mockMvc.perform(post("/migration/fix/1")).andExpect(status().isUnauthorized());
	}
}
