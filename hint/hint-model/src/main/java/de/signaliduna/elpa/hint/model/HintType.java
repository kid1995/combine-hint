package de.signaliduna.elpa.hint.model;

public enum HintType {
	PAPERWAIVER("Kennzeichen Papierverzicht", "Aktualisierung des Kennzeichen Papierverzicht im Partnersystem angefordert."),
	PROFESSION("Beruf- / Branchendaten", "Aktualisierung der Beruf- / Branchendaten im Partnersystem angefordert."),
	NATURAL_PERSON("Daten zur natürlichen Person", "Aktualisierung der Daten zur natürlichen Person im Partnersystem angefordert."),
	AD_CONSENT("UWG-Daten", "Aktualisierung der UWG-Daten im Partnersystem angefordert."),
	COMMUNICATION_ADDRESS("Telekommunikationsdaten", "Aktualisierung der Telekommunikationsdaten für %s im Partnersystem angefordert.");
	private final String key;
	private final String message;

	HintType(String key, String message) {
		this.key = key;
		this.message = message;
	}

	public String getKey() {
		return key;
	}

	public String getMessage() {
		return message;
	}
}
