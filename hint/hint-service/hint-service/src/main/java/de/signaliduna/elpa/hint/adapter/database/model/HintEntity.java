package de.signaliduna.elpa.hint.adapter.database.model;

import de.signaliduna.elpa.hint.model.HintDto;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity(name = "hint")
public class HintEntity {
	@Id
	@GeneratedValue(strategy= GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;
	@Column(name ="hint_source")
	private String hintSource;
	@Column(name ="message")
	private String message;
	@Enumerated(EnumType.STRING)
	@Column(name = "hint_category")
	private  HintDto.Category hintCategory;
	@Column(name ="show_to_user")
	private Boolean showToUser;
	@Column(name = "creation_date")
	private LocalDateTime creationDate;
	@Column(name ="process_id")
	private String processId;
	@Column(name ="process_version")
	private String processVersion;
	@Column(name ="resource_id")
	private String resourceId;
	@Column(name ="mongo_uuid")
	private String mongoUUID;

	protected HintEntity(){}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getHintSource() {
		return hintSource;
	}

	public void setHintSource(String hintSource) {
		this.hintSource = hintSource;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String hintTextOriginal) {
		this.message = hintTextOriginal;
	}

	public HintDto.Category getHintCategory() {
		return hintCategory;
	}

	public void setHintCategory( HintDto.Category hintCategory) {
		this.hintCategory = hintCategory;
	}

	public Boolean getShowToUser() {
		return showToUser;
	}

	public void setShowToUser(Boolean showToUser) {
		this.showToUser = showToUser;
	}

	public LocalDateTime getCreationDate() {
		return creationDate;
	}

	public void setCreationDate(LocalDateTime creationDate) {
		this.creationDate = creationDate;
	}

	public String getProcessId() {
		return processId;
	}

	public void setProcessId(String processId) {
		this.processId = processId;
	}

	public String getProcessVersion() {
		return processVersion;
	}

	public void setProcessVersion(String processVersion) {
		this.processVersion = processVersion;
	}

	public String getResourceId() {
		return resourceId;
	}

	public void setResourceId(String resourceId) {
		this.resourceId = resourceId;
	}

	public String getMongoUUID() {
		return mongoUUID;
	}

	public void setMongoUUID(String mongoUUID) {
		this.mongoUUID = mongoUUID;
	}

	/* BUILDER */
	public static Builder builder() {
		return new Builder();
	}

	private HintEntity(Builder builder) {
		this.id = builder.id;
		this.hintSource = builder.hintSource;
		this.message = builder.message;
		this.hintCategory = builder.hintCategory;
		this.showToUser = builder.showToUser;
		this.creationDate = builder.creationDate;
		this.processId = builder.processId;
		this.processVersion = builder.processVersion;
		this.resourceId = builder.resourceId;
	}

	public static final class Builder {
		private Long id;

		private String hintSource;

		private String message;

		private HintDto.Category hintCategory;

		private boolean showToUser;

		private String processId;

		private LocalDateTime creationDate;

		private String processVersion;

		private String resourceId;

		private Builder() {
		}

		public Builder id(long id) {
			this.id = id;
			return this;
		}
		public Builder hintSource(String hintSource) {
			this.hintSource = (hintSource == null || hintSource.isBlank()) ? null : hintSource;
			return this;
		}

		public Builder message(String message) {
			this.message = (message == null || message.isBlank()) ? null : message;
			return this;
		}

		public Builder hintCategory(HintDto.Category hintCategory) {
			this.hintCategory = hintCategory;
			return this;
		}

		public Builder showToUser(Boolean showToUser) {
			this.showToUser = showToUser;
			return this;
		}

		public Builder processId(String processId) {
			this.processId = (processId == null || processId.isBlank()) ? null : processId;
			return this;
		}

		public Builder creationDate(LocalDateTime creationDate) {
			this.creationDate = creationDate;
			return this;
		}

		public Builder processVersion(String processVersion) {
			this.processVersion = processVersion;
			return this;
		}

		public Builder resourceId(String resourceId) {
			this.resourceId = resourceId;
			return this;
		}

		public HintEntity build() {
			return new HintEntity(this);
		}
	}
}
