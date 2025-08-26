package de.signaliduna.elpa.hint.adapter.database.legacy;

import de.signaliduna.elpa.hint.adapter.database.legacy.model.HintDao;
import de.signaliduna.elpa.hint.model.HintParams;

import java.util.List;
import java.util.Map;

public interface HintRepositoryLegacyCustom {
	List<HintDao> findAllByQuery(Map<HintParams, Object> queryParams);

}
