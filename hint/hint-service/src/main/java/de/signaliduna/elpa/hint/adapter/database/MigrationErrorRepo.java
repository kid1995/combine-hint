package de.signaliduna.elpa.hint.adapter.database;

import de.signaliduna.elpa.hint.adapter.database.model.MigrationErrorEntity;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MigrationErrorRepo extends CrudRepository<MigrationErrorEntity, Long>{

}
