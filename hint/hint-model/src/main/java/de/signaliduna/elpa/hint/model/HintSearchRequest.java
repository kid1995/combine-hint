package de.signaliduna.elpa.hint.model;

import jakarta.annotation.Nullable;

/**
 * Defines a hint search request with several optional filter parameters.
 */
public record HintSearchRequest(
	@Nullable String hintSource,
	@Nullable String hintTextOriginal,
	@Nullable String hintCategory,
	@Nullable Boolean showToUser,
	@Nullable String processId,
	@Nullable String processVersion,
	@Nullable String resourceId
) {

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		@Nullable
		private String hintSource;
		@Nullable
		private String hintTextOriginal;
		@Nullable
		private String hintCategory;
		@Nullable
		private Boolean showToUser;
		@Nullable
		private String processId;
		@Nullable
		private String processVersion;
		@Nullable
		private String resourceId;

		private Builder() {
		}

		public Builder hintSource(String hintSource) {
			this.hintSource = hintSource;
			return this;
		}

		public Builder hintTextOriginal(String hintTextOriginal) {
			this.hintTextOriginal = hintTextOriginal;
			return this;
		}

		public Builder hintCategory(String hintCategory) {
			this.hintCategory = hintCategory;
			return this;
		}

		public Builder showToUser(Boolean showToUser) {
			this.showToUser = showToUser;
			return this;
		}

		public Builder processId(String processId) {
			this.processId = processId;
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

		public HintSearchRequest build() {
			return new HintSearchRequest(this.hintSource, this.hintTextOriginal,
				this.hintCategory, this.showToUser, this.processId, this.processVersion, this.resourceId);
		}
	}
}
