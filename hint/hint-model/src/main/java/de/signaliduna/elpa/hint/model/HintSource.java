package de.signaliduna.elpa.hint.model;

public enum HintSource {
	VALIDATIONS(HintSourcePrefix.PARTNER_SYNC.message +  "-Validations"),
	IDENTIFICATION(HintSourcePrefix.PARTNER_SYNC.message +  "-Identifizierung"),
	CREATION(HintSourcePrefix.PARTNER_SYNC.message +  "-Partneranlage"),
	UPDATE(HintSourcePrefix.PARTNER_SYNC.message +  "-Aktualisierung"),
	ADRESSSYNC(HintSourcePrefix.PARTNER_SYNC.message +  "-Adressabgleich"),
	COMPLETION(HintSourcePrefix.PARTNER_SYNC.message +  "-Anreicherung-Eingangsdaten"),
	VNR_ASSIGNMENT(HintSourcePrefix.PARTNER_SYNC.message +  "-VNR-Vergabe"),
	UPDATE_ASYNC(HintSourcePrefix.PARTNER_SYNC.message +  "-Aktualisierung asynchron"),
	PARTNER_RINR(HintSourcePrefix.PARTNER_SYNC.message +  "-RINR-Ermittlung");

	public final String message;

	HintSource(String message) {
		this.message = message;
	}
}
