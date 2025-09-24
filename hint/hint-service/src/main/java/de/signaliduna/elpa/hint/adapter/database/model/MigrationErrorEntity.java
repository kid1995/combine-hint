package de.signaliduna.elpa.hint.adapter.database.model;

import jakarta.persistence.*;

@Entity(name="migration_error")
public class MigrationErrorEntity {
	@Id
	@GeneratedValue(strategy =  GenerationType.AUTO)
	@Column(name = "id")
	private long id;

	@Column(name = "message")
	private String message;

	@Column(name = "mongo_uuid")
	private  String mongoUUID;

	public MigrationErrorEntity(String message, String mongoUUID) {
		this.message = message;
		this.mongoUUID = mongoUUID;
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
}
