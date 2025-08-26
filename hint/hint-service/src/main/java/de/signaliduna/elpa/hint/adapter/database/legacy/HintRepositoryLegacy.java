package de.signaliduna.elpa.hint.adapter.database.legacy;

import de.signaliduna.elpa.hint.adapter.database.legacy.model.HintDao;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Component;

@Component
public interface HintRepositoryLegacy extends MongoRepository<HintDao, String> {
}
