package de.signaliduna.elpa.hint.model;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * Dto class for Hints. Used as REST model class as well as for asynchronous APIs (Kafka).
 *
 */
public record HintDto(
	@NotEmpty
	String hintSource,
	@NotEmpty
	String message,
	@NotNull
	HintDto.Category hintCategory,
	boolean showToUser,
	@NotEmpty
	String processId,
	@NotNull
	LocalDateTime creationDate,
	String processVersion,
	String resourceId

) {

	public HintDto withHintSource(String hintSource) {
		return new HintDto(hintSource, message, hintCategory, showToUser, processId, creationDate, processVersion, resourceId);
	}

	public HintDto withMessage(String message) {
		return new HintDto(hintSource, message, hintCategory, showToUser, processId, creationDate, processVersion, resourceId);
	}

	public HintDto withHintCategory(HintDto.Category hintCategory) {
		return new HintDto(hintSource, message, hintCategory, showToUser, processId, creationDate, processVersion, resourceId);
	}

	public HintDto withShowToUser(boolean showToUser) {
		return new HintDto(hintSource, message, hintCategory, showToUser, processId, creationDate, processVersion, resourceId);
	}

	public HintDto withProcessId(String processId) {
		return new HintDto(hintSource, message, hintCategory, showToUser, processId, creationDate, processVersion, resourceId);
	}

	public HintDto withCreationDate(LocalDateTime creationDate) {
		return new HintDto(hintSource, message, hintCategory, showToUser, processId, creationDate, processVersion, resourceId);
	}

	public HintDto withProcessVersion(String processVersion) {
		return new HintDto(hintSource, message, hintCategory, showToUser, processId, creationDate, processVersion, resourceId);
	}

	public HintDto withResourceId(String resourceId) {
		return new HintDto(hintSource, message, hintCategory, showToUser, processId, creationDate, processVersion, resourceId);
	}

	public enum Category {
		INFO, WARNING, ERROR, BLOCKER
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private String hintSource;

		private String message;

		private Category hintCategory;

		private boolean showToUser;

		private String processId;

		private LocalDateTime creationDate;

		private String processVersion;

		private String resourceId;

		private Builder() {
		}

		public Builder hintSource(String hintSource) {
			this.hintSource = (hintSource == null || hintSource.isBlank()) ? null : hintSource;
			return this;
		}

		public Builder message(String message) {
			this.message = (message == null || message.isBlank()) ? null : message;
			return this;
		}

		public Builder hintCategory(Category hintCategory) {
			this.hintCategory = hintCategory;
			return this;
		}

		public Builder showToUser(boolean showToUser) {
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

		public HintDto build() {
			return new HintDto(this.hintSource, this.message, this.hintCategory,
				this.showToUser, this.processId, this.creationDate, this.processVersion, this.resourceId);
		}
	}
}
