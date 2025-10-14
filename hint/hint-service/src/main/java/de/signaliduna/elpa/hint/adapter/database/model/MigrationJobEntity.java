package de.signaliduna.elpa.hint.adapter.database.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity(name = "migration_job")
public class MigrationJobEntity {
	@Id
	@GeneratedValue(strategy= GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@Column(name = "message")
	private String message;

	@Column(name = "data_set_start_date")
	private LocalDateTime dataSetStartDate;

	@Column(name = "data_set_end_date")
	private LocalDateTime dataSetStopDate;

	@Column(name = "creation_date")
	private LocalDateTime creationDate;

	@Column(name = "finishing_date")
	private LocalDateTime finishingDate;

	@Column(name = "total_items")
	private Long totalItems;

	@Column(name = "processed_items")
	private Long processedItems;

	@Column(name = "state")
	@Enumerated(value=EnumType.STRING)
	private MigrationJobEntity.STATE state;

	@Column(name = "type")
	@Enumerated(value=EnumType.STRING)
	private MigrationJobEntity.TYPE type;

	@OneToMany(mappedBy = "job", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
	private List<MigrationErrorEntity> errors = new ArrayList<>();

	protected MigrationJobEntity() {}

	public enum STATE{
		RUNNING, BROKEN, COMPLETED
	}

	public enum TYPE{
		MIGRATION, FIXING, VALIDATION, UNKNOWN
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

	public LocalDateTime getCreationDate() {
		return creationDate;
	}

	public void setCreationDate(LocalDateTime creationDate) {
		this.creationDate = creationDate;
	}

	public LocalDateTime getFinishingDate() {
		return finishingDate;
	}

	public void setFinishingDate(LocalDateTime finishingDate) {
		this.finishingDate = finishingDate;
	}

	public STATE getState() {
		return state;
	}

	public void setState(STATE state) {
		this.state = state;
	}

	public LocalDateTime getDataSetStartDate() {
		return dataSetStartDate;
	}

	public void setDataSetStartDate(LocalDateTime dataSetStartDate) {
		this.dataSetStartDate = dataSetStartDate;
	}

	public LocalDateTime getDataSetStopDate() {
		return dataSetStopDate;
	}

	public void setDataSetStopDate(LocalDateTime dataSetStopDate) {
		this.dataSetStopDate = dataSetStopDate;
	}

	public List<MigrationErrorEntity> getErrors() {
		return errors;
	}
	public void setErrors(List<MigrationErrorEntity> errors) {
		this.errors = errors;
	}

	public Long getTotalItems() {
		return totalItems;
	}

	public void setTotalItems(Long totalItems) {
		this.totalItems = totalItems;
	}

	public Long getProcessedItems() {
		return processedItems;
	}

	public void setProcessedItems(Long processedItems) {
		this.processedItems = processedItems;
	}

	public TYPE getType() {
		return type;
	}

	public void setType(TYPE type) {
		this.type = type;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private Long id;
		private String message;
		private LocalDateTime dataSetStartDate;
		private LocalDateTime dataSetStopDate;
		private LocalDateTime creationDate;
		private LocalDateTime finishingDate;
		private STATE state;
		private final List<MigrationErrorEntity> errors = new ArrayList<>();
		private Long totalItems;
		private Long processedItems;
		private TYPE type;

		private Builder() {}

		public Builder id(long id) {
			this.id = id;
			return this;
		}

		public Builder message(String message) {
			this.message = message;
			return this;
		}

		public Builder dataSetStartDate(LocalDateTime dataSetStartDate) {
			this.dataSetStartDate = dataSetStartDate;
			return this;
		}

		public Builder dataSetStopDate(LocalDateTime dataSetStopDate) {
			this.dataSetStopDate = dataSetStopDate;
			return this;
		}

		public Builder creationDate(LocalDateTime creationDate) {
			this.creationDate = creationDate;
			return this;
		}

		public Builder finishingDate(LocalDateTime finishingDate) {
			this.finishingDate = finishingDate;
			return this;
		}

		public Builder state(STATE state) {
			this.state = state;
			return this;
		}

		public Builder totalItems(Long totalItems) {
			this.totalItems = totalItems;
			return this;
		}

		public  Builder type(TYPE type) {
			this.type = type;
			return this;
		}

		public MigrationJobEntity build() {
			MigrationJobEntity migrationJobEntity = new MigrationJobEntity();
			migrationJobEntity.id = id;
			migrationJobEntity.setMessage(message);
			migrationJobEntity.setDataSetStartDate(dataSetStartDate);
			migrationJobEntity.setDataSetStopDate(dataSetStopDate);
			migrationJobEntity.setCreationDate(creationDate);
			migrationJobEntity.setFinishingDate(finishingDate);
			migrationJobEntity.setState(state);
			migrationJobEntity.setErrors(errors);
			migrationJobEntity.setTotalItems(totalItems);
			migrationJobEntity.setType(type);
			migrationJobEntity.setProcessedItems(processedItems);
			return migrationJobEntity;
		}
	}
}
