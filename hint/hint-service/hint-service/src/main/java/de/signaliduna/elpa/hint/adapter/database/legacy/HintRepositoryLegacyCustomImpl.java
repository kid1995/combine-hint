package de.signaliduna.elpa.hint.adapter.database.legacy;

import de.signaliduna.elpa.hint.adapter.database.legacy.model.HintDao;
import de.signaliduna.elpa.hint.model.HintParams;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class HintRepositoryLegacyCustomImpl implements HintRepositoryLegacyCustom {

	private final MongoTemplate mongoTemplate;

	public HintRepositoryLegacyCustomImpl(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	public List<HintDao> findAllByQuery(Map<HintParams, Object> queryParams) {
		Criteria criteria = new Criteria();

		queryParams.forEach((key, value) -> {
			if (HintParams.HINT_SOURCE_PREFIX.equals(key)) {
				criteria.and(HintParams.HINT_SOURCE.getName()).regex("^" + Pattern.quote(value.toString()));
			} else {
				criteria.and(key.getName()).is(value);
			}
		});

		return this.mongoTemplate.find(new Query(criteria), HintDao.class);
	}
}
