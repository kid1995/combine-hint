package de.signaliduna.elpa.hint.util;

import de.signaliduna.elpa.hint.adapter.database.model.MigrationErrorEntity;
import de.signaliduna.elpa.hint.adapter.database.model.MigrationJobEntity;

import java.time.LocalDateTime;
import java.util.Random;

public class MigrationTestDataGenerator {

	private static final Random random = new Random();

	private MigrationTestDataGenerator() {
	}

	// MigrationJobEntity generators
	public static MigrationJobEntity createRunningJob() {
		return MigrationJobEntity.builder()
			.id(generateRandomId())
			.creationDate(LocalDateTime.now().minusMinutes(random.nextInt(60)))
			.state(MigrationJobEntity.STATE.RUNNING)
			.dataSetStartDate(LocalDateTime.now().minusDays(30))
			.dataSetStopDate(LocalDateTime.now())
			.build();
	}

	public static MigrationJobEntity createCompletedJob() {
		LocalDateTime creationDate = LocalDateTime.now().minusHours(random.nextInt(24) + 1);
		return MigrationJobEntity.builder()
			.id(generateRandomId())
			.creationDate(creationDate)
			.finishingDate(creationDate.plusMinutes(random.nextInt(120) + 30))
			.state(MigrationJobEntity.STATE.COMPLETED)
			.message("Migration completed successfully.")
			.dataSetStartDate(LocalDateTime.now().minusDays(60))
			.dataSetStopDate(LocalDateTime.now().minusDays(30))
			.build();
	}

	public static MigrationJobEntity createBrokenJob() {
		return createBrokenJob("Unexpected error during migration");
	}

	public static MigrationJobEntity createBrokenJob(String errorMessage) {
		LocalDateTime creationDate = LocalDateTime.now().minusHours(random.nextInt(12) + 1);
		return MigrationJobEntity.builder()
			.id(generateRandomId())
			.creationDate(creationDate)
			.finishingDate(creationDate.plusMinutes(random.nextInt(30) + 5))
			.state(MigrationJobEntity.STATE.BROKEN)
			.message(errorMessage)
			.build();
	}

	public static MigrationJobEntity createJobWithId(Long id) {
		return MigrationJobEntity.builder()
			.id(id)
			.creationDate(LocalDateTime.now())
			.state(MigrationJobEntity.STATE.RUNNING)
			.build();
	}

	public static MigrationJobEntity createJobWithDateRange(LocalDateTime startDate, LocalDateTime endDate) {
		return MigrationJobEntity.builder()
			.id(generateRandomId())
			.creationDate(LocalDateTime.now())
			.dataSetStartDate(startDate)
			.dataSetStopDate(endDate)
			.state(MigrationJobEntity.STATE.RUNNING)
			.build();
	}

	// MigrationErrorEntity generators
	public static MigrationErrorEntity createUnresolvedError(MigrationJobEntity job) {
		return MigrationErrorEntity.builder()
			.mongoUUID(generateMongoId())
			.message("Failed to migrate hint: " + getRandomErrorMessage())
			.resolved(false)
			.jobID(job)
			.build();
	}

	public static MigrationErrorEntity createResolvedError(MigrationJobEntity job) {
		return MigrationErrorEntity.builder()
			.mongoUUID(generateMongoId())
			.message("Error resolved after retry")
			.resolved(true)
			.jobID(job)
			.build();
	}

	public static MigrationErrorEntity createErrorWithMongoId(String mongoId, MigrationJobEntity job) {
		return MigrationErrorEntity.builder()
			.mongoUUID(mongoId)
			.message("Migration error for document: " + mongoId)
			.resolved(false)
			.jobID(job)
			.build();
	}

	public static MigrationErrorEntity createDataIntegrityError(String mongoId, MigrationJobEntity job) {
		return MigrationErrorEntity.builder()
			.mongoUUID(mongoId)
			.message("Data integrity violation: Duplicate key value")
			.resolved(false)
			.jobID(job)
			.build();
	}

	// Helper methods
	private static Long generateRandomId() {
		return (long) (random.nextInt(9000) + 1000);
	}

	private static String generateMongoId() {
		// Generate a realistic MongoDB ObjectId (24 hex characters)
		StringBuilder sb = new StringBuilder();
		String hexChars = "0123456789abcdef";
		for (int i = 0; i < 24; i++) {
			sb.append(hexChars.charAt(random.nextInt(hexChars.length())));
		}
		return sb.toString();
	}

	private static String getRandomErrorMessage() {
		String[] errors = {
			"Validation failed",
			"Duplicate key error",
			"Network timeout",
			"Invalid data format",
			"Constraint violation"
		};
		return errors[random.nextInt(errors.length)];
	}
}
