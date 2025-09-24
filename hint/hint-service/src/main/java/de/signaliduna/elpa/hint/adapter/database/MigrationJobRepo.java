package de.signaliduna.elpa.hint.adapter.database;

import de.signaliduna.elpa.hint.adapter.database.model.MigrationJobEntity;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MigrationJobRepo extends CrudRepository<MigrationJobEntity, Long> {

}
