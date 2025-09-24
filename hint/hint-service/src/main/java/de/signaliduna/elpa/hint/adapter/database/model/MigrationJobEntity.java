package de.signaliduna.elpa.hint.adapter.database.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity(name = "migration_job")
public class MigrationJobEntity {
	@Id
	@GeneratedValue(strategy =  GenerationType.AUTO)
	@Column(name = "id")
	private long id;

	@Column(name = "message")
	private String message;

	@Column(name = "mongo_uuid")
	private  String lastMergedPoint;

	@Column(name = "data_set_start_date")
	private LocalDateTime dataSetStartDate;

	@Column(name = "data_set_end_date")
	private LocalDateTime dataSetStopDate;

	@Column(name = "creation_date")
	private LocalDateTime creationDate;

	@Column(name = "finishing_date")
	private LocalDateTime finishingDate;

	@Enumerated(value=EnumType.STRING)
	private MigrationJobEntity.STATE state;

	protected MigrationJobEntity() {

	}

	public enum STATE{
		RUNNING, FAILED, SUCCESS
	}

	public MigrationJobEntity(String message, String lastMergedPoint, LocalDateTime creationDate, LocalDateTime finishingDate, STATE state) {
		this.message = message;
		this.lastMergedPoint = lastMergedPoint;
		this.creationDate = creationDate;
		this.finishingDate = finishingDate;
		this.state = state;
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

	public String getLastMergedPoint() {
		return lastMergedPoint;
	}

	public void setLastMergedPoint(String lastMergedPoint) {
		this.lastMergedPoint = lastMergedPoint;
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
}
