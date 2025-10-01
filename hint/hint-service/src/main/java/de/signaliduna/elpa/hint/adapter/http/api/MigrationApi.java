package de.signaliduna.elpa.hint.adapter.http.api;

import de.signaliduna.elpa.hint.adapter.database.MigrationErrorRepo;
import de.signaliduna.elpa.hint.adapter.database.MigrationJobRepo;
import de.signaliduna.elpa.hint.adapter.database.model.MigrationErrorEntity;
import de.signaliduna.elpa.hint.adapter.database.model.MigrationJobEntity;
import de.signaliduna.elpa.hint.core.MigrationService;
import de.signaliduna.elpa.hint.core.model.ValidationResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/migration")
@PreAuthorize("isAuthenticated() and isAuthorizedMigrationUser()")
public class MigrationApi {

	private final MigrationService migrationService;
	private final MigrationJobRepo migrationJobRepo;
	private final MigrationErrorRepo migrationErrorRepo;

	public MigrationApi(MigrationService migrationService, MigrationJobRepo migrationJobRepo, MigrationErrorRepo migrationErrorRepo) {
		this.migrationService = migrationService;
		this.migrationJobRepo = migrationJobRepo;
		this.migrationErrorRepo = migrationErrorRepo;
	}

	@PostMapping("/count")
	public ResponseEntity<Long> countNumberOfMigrationItem(@RequestParam(required = false) LocalDateTime dataSetStartDate, @RequestParam(required = false) LocalDateTime dataSetEndDate) {
		long numberOfItem = migrationService.countMongoHints(dataSetStartDate, dataSetEndDate);
		return ResponseEntity.accepted().body(numberOfItem);
	}

	@PostMapping("/start")
	public ResponseEntity<Long> startMigration(@RequestParam(required = false) LocalDateTime dataSetStartDate, @RequestParam(required = false) LocalDateTime dataSetEndDate) {
		MigrationJobEntity job = migrationJobRepo.save(MigrationJobEntity.builder()
			.dataSetStartDate(dataSetStartDate)
			.dataSetStopDate(dataSetEndDate)
			.creationDate(LocalDateTime.now())
			.state(MigrationJobEntity.STATE.RUNNING)
			.build());
		migrationService.startMigration(job);
		return ResponseEntity.accepted().body(job.getId());
	}

	@GetMapping("/validate/{jobId}")
	public ResponseEntity<ValidationResult> validateMigration(@PathVariable Long jobId) {
		ValidationResult result = migrationService.validateMigration(jobId);
		if (result.successful()) {
			return ResponseEntity.ok(result);
		} else {
			return ResponseEntity.status(HttpStatus.EXPECTATION_FAILED).body(result);
		}
	}

	@GetMapping("/jobs")
	public Iterable<MigrationJobEntity> getJobs() {
		return migrationJobRepo.findAll();
	}

	@GetMapping("/jobs/{jobId}")
	public ResponseEntity<MigrationJobEntity> getJob(@PathVariable Long jobId) {
		return ResponseEntity.of(migrationJobRepo.findById(jobId));
	}

	@GetMapping("/errors")
	public Iterable<MigrationErrorEntity> getErrors() {
		return migrationErrorRepo.findAll();
	}

	@GetMapping("/errors/{errorId}")
	public ResponseEntity<MigrationErrorEntity> getError(@PathVariable Long errorId) {
		return ResponseEntity.of(migrationErrorRepo.findById(errorId));
	}

	@PostMapping("/fix/{jobId}")
	public ResponseEntity<Long> fixErrors(@PathVariable Long jobId) {
		MigrationJobEntity job = migrationJobRepo.save(MigrationJobEntity.builder()
			.creationDate(LocalDateTime.now())
			.state(MigrationJobEntity.STATE.RUNNING)
			.build());
		migrationService.fixUnresolvedErrors(job, jobId);
		return ResponseEntity.accepted().body(job.getId());
	}
}
