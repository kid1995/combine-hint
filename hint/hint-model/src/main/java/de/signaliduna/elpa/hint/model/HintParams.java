package de.signaliduna.elpa.hint.model;

public enum HintParams {
	ID("id"),
	HINT_SOURCE("hintSource"),
	HINT_SOURCE_PREFIX("hintSourcePrefix"),
	HINT_TEXT_ORIGINAL("hintTextOriginal"),
	HINT_CATEGORY("hintCategory"),
	SHOW_TO_USER("showToUser"),
	PROCESS_ID("processId"),
	PROCESS_VERSION("processVersion"),
	RESOURCE_ID("resourceId"),
	UNKNOWN("unknown");

	private final String name;

	HintParams(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}
}
