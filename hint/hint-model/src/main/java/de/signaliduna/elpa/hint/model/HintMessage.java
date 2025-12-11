package de.signaliduna.elpa.hint.model;

public enum HintMessage {
	STRING_PLACEHOLDER("%s"),

	POSTAL_CODE_NEW("PLZ ergänzt."),
	POSTAL_CODE_DIFFERENT("PLZ abweichend."),

	CITY_NEW("Ort ergänzt."),
	CITY_DIFFERENT("Ort abweichend."),

	STREET_NEW("Straße ergänzt."),
	STREET_DIFFERENT("Straße / Hausnummer abweichend. Daten aus Partnersystem wurden übernommen."),

	ADDRESS_DIFFERENT("Die Antrags-Adresse stimmt nicht mit der Adresse im Partnersystem überein."),
	ADDRESS_MISSING("Die Adresse im Partnersystem fehlt."),

	SALUTATION_MISSING("Anredekennzeichen fehlt"),
	MARITAL_STATUS_MISSING("Familienstand fehlt"),

	COUNTRY_EXTERNAL_KEY_MISSING("Länderkennzeichen fehlt"),
	COUNTRY_ACRONYM_MISSING("Land fehlt"),

	PARTNER_VALIDATION_VNR_NOT_FOUND("Kein Partner zu VNR %s im ODS gefunden."),
	PARTNER_VALIDATION_VNR_MULTIPLE_PAIDS("Mehr als eine PAID für den VN bei Suche über die VNR %s gefunden."),
	PARTNER_VALIDATION_PANR_NOT_FOUND("Kein Panr gefunden, zur Paid gefunden: %s"),
	PARTNER_VALIDATION_FIRST_LASTNAME_NOT_EQUAL("Der Name des VN wich zwischen Antrag und Partnersystem (%s) ab. Es werden keine Aktualisierungen von Partnerdaten vorgenommen."),
	PARTNER_VALIDATION_FIRST_NAME_NOT_IN_PODS("Partner ist nicht im ODS identifizierbar mit der PAID %s"),
	PARTNER_VALIDATION_FIRST_NAME_ONLY_CONTAINS("Die Vornamen für PAID %s weichen auf dem Antrag und im Partnersystem teilweise ab. Bitte prüfen und ggf. korrigieren."),
	PARTNER_VALIDATION_PARTNER_UNIDENTIFIED("Partner konnte nicht identifiziert werden"),
	PARTNER_VALIDATION_NO_PAID("Keine PAID zu PANR '%s' möglich. PANR korrekt?"),
	PARTNER_VALIDATION_VNR_ASSIGNED("PAID '%s' zu VNR '%s' zugeordnet"),
	PARTNER_VALIDATION_PANR_ASSIGNED("PAID '%s' zu PANR '%s' zugeordnet"),

	HINT_PLZ_CHANGED("Die Postleitzahl wurde von %s in %s geaendert."),
	HINT_CITY_CHANGED("Die Stadt wurde von %s in %s geaendert."),
	HINT_STREET_CHANGED("Die Strasse wurde von %s in %s geaendert."),
	HINT_PARTNER_FOUND("Mit den Mindestdaten wurde die PAID %s und die Partnernummer %s im Partnersystem gefunden."),
	HINT_PARTNER_NOT_IDENTICAL("Der Partner mit der PAID %s wurde wegen abweichender Daten nicht ausgewählt, es erfolgt eine Partner-Neuanlage"),

	PARTNER_MINIMUM_DATA_AMBIGUOUS("Mehrere Partner mit Mindestdaten gefunden."),

	PARTNER_UPDATE_TIMEOUT("Partnerupdate für PAID %s und %s wurde in der vorgesehen Zeit nicht abgeschlossen"),
	PARTNER_UPDATE_ERROR("Partnerupdate für PAID %s und %s wurde mit einem Fehler abgeschlossen"),
	PARTNER_UPDATE_SUCCESS("Partnerupdate für PAID %s und %s wurde erfolgreich abgeschlossen"),
	PARTNER_UPDATE_MISSING("Aktualisierung %s im Partnersystem zum Zeitpunkt der Weitergabe an den Fachbereich noch nicht erfolgt. Bitte im Partnersystem prüfen und ggf. aktualisieren."),
	PARTNER_UPDATE_REQUESTED("Aktualisierung %s im Partnersystem angefordert."),

	// Example: Aktualisierung des Kennzeichen Papierverzicht im Partnersystem angefordert. (Source: Elpa-Classic)
	PARTNER_UPDATE_PAPER_WAIVER_REQUESTED(PARTNER_UPDATE_REQUESTED.message.formatted("des Kennzeichen Papierverzicht")),
	PARTNER_UPDATE_PROFESSION_REQUESTED(PARTNER_UPDATE_REQUESTED.message.formatted("der Beruf- / Branchendaten")),
	PARTNER_UPDATE_NAT_PERSON_REQUESTED(PARTNER_UPDATE_REQUESTED.message.formatted("der Daten zur natürlichen Person")),
	PARTNER_UPDATE_AD_CONSENT_REQUESTED(PARTNER_UPDATE_REQUESTED.message.formatted("der UWG-Daten")),

	PARTNER_UPDATE_COMMUNICATION_REQUESTED("Aktualisierung der Telekommunikationsdaten"),
	// Example: Aktualisierung der Telekommunikationsdaten für E-Mail im Partnersystem angefordert. (Source: PartnerSync)
	PARTNER_UPDATE_COMMUNICATION_WITH_EMAIL_REQUESTED(PARTNER_UPDATE_REQUESTED.message.formatted("der Telekommunikationsdaten für E-Mail")),
	PARTNER_UPDATE_COMMUNICATION_WITH_FAX_REQUESTED(PARTNER_UPDATE_REQUESTED.message.formatted("der Telekommunikationsdaten für Fax")),
	PARTNER_UPDATE_COMMUNICATION_WITH_MOBILE_REQUESTED(PARTNER_UPDATE_REQUESTED.message.formatted("der Telekommunikationsdaten für Mobil")),
	PARTNER_UPDATE_COMMUNICATION_WITH_TELEPHONE_REQUESTED(PARTNER_UPDATE_REQUESTED.message.formatted("der Telekommunikationsdaten für Telefon")),

	// Special case of  paper waiver, which do not follow the pattern
	PAPERWAIVER_UPDATE_SUCCESS("Kennzeichen fuer digitale Zustellung App/Portal in Partner fuer PAID '%s' gesetzt."),
	//Example: "Partnerupdate für PAID C000000615704355 und Beruf- / Branchendaten wurde erfolgreich abgeschlossen" (Source: PartnerSync)
	PARTNER_UPDATE_PROFESSION_SUCCESS(PARTNER_UPDATE_SUCCESS.message.formatted(STRING_PLACEHOLDER.message, "Beruf- / Branchendaten")),
	PARTNER_UPDATE_NAT_PERSON_SUCCESS(PARTNER_UPDATE_SUCCESS.message.formatted(STRING_PLACEHOLDER.message, "Daten zur natürlichen Person")),
	PARTNER_UPDATE_AD_CONSENT_SUCCESS(PARTNER_UPDATE_SUCCESS.message.formatted(STRING_PLACEHOLDER.message, "UWG-Daten")),
	PARTNER_UPDATE_COMMUNICATION_SUCCESS(PARTNER_UPDATE_SUCCESS.message.formatted(STRING_PLACEHOLDER.message, "Telekommunikationsdaten")),

	// Special case of  paper waiver, which do not follow the pattern
	PAPERWAIVER_UPDATE_ERROR("Setzen des Kennzeichen fuer digitale Zustellung App/Portal in Partner fuer PAID '%s' nicht erfolgreich."),
	//Example: "Partnerupdate für PAID C000000615704351 und Beruf- / Branchendaten wurde mit einem Fehler abgeschlossen" (Source: Elpa-Classic)
	PARTNER_UPDATE_PROFESSION_ERROR(PARTNER_UPDATE_ERROR.message.formatted(STRING_PLACEHOLDER.message, "Beruf- / Branchendaten")),
	PARTNER_UPDATE_NAT_PERSON_ERROR(PARTNER_UPDATE_ERROR.message.formatted(STRING_PLACEHOLDER.message, "Daten zur natürlichen Person")),
	PARTNER_UPDATE_AD_CONSENT_ERROR(PARTNER_UPDATE_ERROR.message.formatted(STRING_PLACEHOLDER.message, "UWG-Daten")),
	PARTNER_UPDATE_COMMUNICATION_ERROR(PARTNER_UPDATE_ERROR.message.formatted(STRING_PLACEHOLDER.message, "Telekommunikationsdaten")),

	//Example: "Aktualisierung des Papierverzichts im Partnersystem zum Zeitpunkt der Weitergabe an den Fachbereich noch nicht erfolgt. Bitte im Partnersystem prüfen und ggf. aktualisieren." (Source: Elpa-Classic)
	PARTNER_UPDATE_PAPER_WAIVER_MISSING(PARTNER_UPDATE_MISSING.message.formatted("des Papierverzichts")),
	PARTNER_UPDATE_PROFESSION_MISSING(PARTNER_UPDATE_MISSING.message.formatted("der Beruf- / Branchendaten")),
	PARTNER_UPDATE_NAT_PERSON_MISSING(PARTNER_UPDATE_MISSING.message.formatted("der Daten zur natürlichen Person")),
	PARTNER_UPDATE_AD_CONSENT_MISSING(PARTNER_UPDATE_MISSING.message.formatted("der UWG-Daten")),
	PARTNER_UPDATE_COMMUNICATION_MISSING(PARTNER_UPDATE_MISSING.message.formatted("der Telekommunikationsdaten")),

	EMAIL_PAPERWAIVER_UPDATE("Kennzeichen fuer E-Mailversand in Partner fuer PAID %s gesetzt"),
	EMAIL_MISSING_PAPERWAIVER_UPDATE("Kennzeichen digitaler Versand fuer PAID %s angefordert, E-Mailadresse fehlt"),
	EMAIL_PAPERWAIVER_UPDATE_WITHOUT_MOBILE("Kennzeichen digitaler Versand fuer PAID %s angefordert und keine Mobilfunknummer vorhanden"),

	NAME1_IS_NOT_NULL("Name1 darf nicht gesetzt sein, wenn Firmenname leer ist"),
	FIRMENNAME_IS_NULL("Firmenname muss gesetzt sein"),

	FIRSTNAME_IS_NULL("Vorname muss gesetzt sein!"),
	LASTNAME_IS_NULL("Nachname muss gesetzt sein!"),
	BIRTHDATE_IS_NULL("Geburtsdatum muss gesetzt sein. VNR oder PANR konnte nicht geprüft werden."),
	CITY_IS_NULL("Ort kann nicht leer sein, wenn die PANR oder VNR nicht gesetzt sind!"),
	POSTAL_CODE_IS_NULL("PLZ kann nicht leer sein, wenn die PANR oder VNR nicht gesetzt sind!"),
	STREET_IS_NULL("Strasse kann nicht leer sein, wenn die PANR oder VNR nicht gesetzt sind!"),
	COUNTRY_ACRONYM_IS_NULL("Land kann nicht leer sein, wenn die PANR oder VNR nicht gesetzt sind!"),
	COUNTRY_EXTERNAL_KEY_IS_NULL("Länderkennzeichen kann nicht leer sein, wenn die PANR oder VNR nicht gesetzt sind!"),
	ADDRESS_NOT_TESTABLE("Adresse konnte nicht validiert werden."),
	PANR_IS_NULL_FOR_PAID("Zur PAID %s konnte keine PANR ermittelt werden."),
	PANR_IS_INVALID("PANR muss 9-stellig sein."),

	HINT_PARTNER_COMPLETED("Personendaten zu PAID %s wurden nachgelesen."),
	HINT_PARTNER_CREATED("Partner mit Partnernummer %s und Partner-ID %s neu angelegt"),

	VNR_ASSIGNED("VNR '%s' wurde zugeordnet."),
	VNR_NEW("VNR '%s' wurde neu vergeben."),
	VNR_DIFFERENT("VNR zugeordnet '%s', abweichend von Antrags-VNR '%s'."),
	VNR_ASSIGNED_FROM_APPLICATION("VNR '%s' vom Antrag wurde zugeordnet."),
	VNR_ASSIGNED_FROM_APPLICATION_DUE_TO_PA06_CHANGE_LOCK("VNR '%s' vom Antrag wurde zugeordnet aufgrund aktiver PA06 Änderungssperre."),
	VNR_NEW_FOR_SECTOR("VNR '%s' wurde zur '%s' neu vergeben.");

	public final String message;

	HintMessage(String message) {
		this.message = message;
	}

	public String format(Object... args) {
		return String.format(message, args);
	}

}
