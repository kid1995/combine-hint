package de.signaliduna.elpa.hint.adapter.http.api;

import com.c4_soft.springaddons.security.oauth2.test.annotations.WithMockAuthentication;
import com.c4_soft.springaddons.security.oauth2.test.webmvc.AutoConfigureAddonsWebmvcResourceServerSecurity;
import de.signaliduna.elpa.hint.adapter.database.MigrationErrorRepo;
import de.signaliduna.elpa.hint.adapter.database.MigrationJobRepo;
import de.signaliduna.elpa.hint.adapter.database.model.MigrationJobEntity;
import de.signaliduna.elpa.hint.config.WebSecurityConfig;
import de.signaliduna.elpa.hint.core.MigrationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

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
		MigrationJobEntity job = MigrationJobEntity.builder().id(1L).build();
		when(migrationJobRepo.save(any(MigrationJobEntity.class))).thenReturn(job);
		when(migrationService.startMigration(any(MigrationJobEntity.class)))
			.thenReturn(CompletableFuture.completedFuture(1L));

		mockMvc.perform(post("/migration/start")
				.param("dataSetStartDate", "2024-01-01T00:00:00")
				.param("dataSetEndDate", "2024-01-31T23:59:59"))
			.andExpect(status().isAccepted())
			.andExpect(content().string("1"));
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
		MigrationJobEntity job = MigrationJobEntity.builder()
			.id(1L)
			.state(MigrationJobEntity.STATE.COMPLETED)
			.creationDate(LocalDateTime.now())
			.build();
		when(migrationJobRepo.findById(1L)).thenReturn(Optional.of(job));

		mockMvc.perform(get("/migration/jobs/1"))
			.andExpect(status().isOk());
	}

	@Test
	@DisplayName("POST /fix/{jobId} - should be accepted for migration user")
	@WithMockAuthentication(name = MIGRATION_USER)
	void fixErrors_accepted() throws Exception {
		MigrationJobEntity newJob = MigrationJobEntity.builder().id(2L).build();
		when(migrationJobRepo.save(any(MigrationJobEntity.class))).thenReturn(newJob);

		mockMvc.perform(post("/migration/fix/1"))
			.andExpect(status().isAccepted())
			.andExpect(content().string("2"));

		verify(migrationService).fixUnresolvedErrors(any(MigrationJobEntity.class), eq(1L));
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
