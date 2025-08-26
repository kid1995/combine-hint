package de.signaliduna.elpa.hint.adapter.database;

import de.signaliduna.elpa.hint.adapter.database.model.HintEntity;
import de.signaliduna.elpa.hint.model.HintParams;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;


public final class HintSpecifications {

	private HintSpecifications() {}

	public static Specification<HintEntity> fromQuery(Map<HintParams, Object> queryParams) {
			return ((root, query, criteriaBuilder) ->  {
				List<Predicate> predicates = new ArrayList<>();
				queryParams.forEach((hintParams, value) -> {
					if (value == null) {
						return;
					}
					if (hintParams.equals(HintParams.HINT_SOURCE_PREFIX)) {
							predicates.add(criteriaBuilder.like(root.get(HintParams.HINT_SOURCE.getName()), String.format("%s%%", value)));
					} else{
						predicates.add(criteriaBuilder.equal(root.get(hintParams.getName()), value));
					}
				});
				return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
			});
	}
}
