package de.signaliduna.elpa.hint.adapter.database.model;

import jakarta.persistence.*;

@Entity(name = "migration_error")
public class MigrationErrorEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private long id;

	@Column(name = "message")
	private String message;

	@Column(name = "mongo_uuid")
	private String mongoUUID;

	@Column(name = "resolved")
	private Boolean resolved;

	@ManyToOne
	@JoinColumn(name = "job")
	private MigrationJobEntity job;

	public MigrationErrorEntity(String message, String mongoUUID, Boolean resolved, MigrationJobEntity job) {
		this.message = message;
		this.mongoUUID = mongoUUID;
		this.resolved = resolved;
		this.job = job;
	}

	protected MigrationErrorEntity() {
	}

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public String getMongoUUID() {
		return mongoUUID;
	}

	public void setMongoUUID(String mongoUUID) {
		this.mongoUUID = mongoUUID;
	}

	public Boolean getResolved() {
		return resolved;
	}

	public void setResolved(Boolean resolved) {
		this.resolved = resolved;
	}

	public MigrationJobEntity getJob() {
		return job;
	}

	public void setJob(MigrationJobEntity job) {
		this.job = job;
	}

	public static Builder builder() {
		return new Builder();
	}


	public static final class Builder {
		private String message;
		private String mongoUUID;
		private Boolean resolved;
		private MigrationJobEntity job;

		private Builder() {
		}

		public Builder message(String message) {
			this.message = message;
			return this;
		}

		public Builder mongoUUID(String mongoUUID) {
			this.mongoUUID = mongoUUID;
			return this;
		}

		public Builder resolved(Boolean resolved) {
			this.resolved = resolved;
			return this;
		}

		public Builder job(MigrationJobEntity job) {
			this.job = job;
			return this;
		}

		public MigrationErrorEntity build() {
			return new MigrationErrorEntity(message, mongoUUID, resolved, job);
		}
	}
}
