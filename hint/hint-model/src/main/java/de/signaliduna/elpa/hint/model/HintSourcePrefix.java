package de.signaliduna.elpa.hint.model;

public enum HintSourcePrefix {
	PARTNER_SYNC("PASYNC");

	public final String message;

	HintSourcePrefix(String message) {
		this.message = message;
	}
}
