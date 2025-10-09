package de.signaliduna.elpa.hint.adapter.database.legacy.model;


import de.signaliduna.elpa.hint.model.HintDto;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Document("Hint")
public record HintDao(
	@Id String id,
	String hintSource,
	String hintTextOriginal,
	HintDto.Category hintCategory,
	boolean showToUser,
	String processId,
	LocalDateTime creationDate,
	String processVersion,
	String resourceId
) { }
