package de.signaliduna.elpa.hint.model;

import java.util.EnumMap;
import java.util.Map;

public record HintQueryRequest(
	String hintSourcePrefix,
	String hintSource,
	String hintTextOriginal,
	String hintCategory,
	Boolean showToUser,
	String processId,
	String processVersion,
	String resourceId
) {
	public Map<HintParams, Object> toQueryParams() {
		Map<HintParams, Object> queryParams = new EnumMap<>(
			HintParams.class
		);
		if (hintSource() != null) queryParams.put(HintParams.HINT_SOURCE, hintSource());
		if (hintSourcePrefix() != null) {
			queryParams.remove(HintParams.HINT_SOURCE);
			queryParams.put(HintParams.HINT_SOURCE_PREFIX, hintSourcePrefix());
		}
		if (hintTextOriginal() != null) queryParams.put(HintParams.HINT_TEXT_ORIGINAL, hintTextOriginal());
		if (hintCategory() != null) queryParams.put(HintParams.HINT_CATEGORY, hintCategory().toUpperCase());
		if (showToUser() != null) queryParams.put(HintParams.SHOW_TO_USER, showToUser());
		if (processId() != null) queryParams.put(HintParams.PROCESS_ID, processId());
		if (processVersion() != null) queryParams.put(HintParams.PROCESS_VERSION, processVersion());
		if (resourceId() != null) queryParams.put(HintParams.RESOURCE_ID, resourceId());
		return queryParams;
	}
}


