package de.signaliduna.elpa.hint.adapter.database;

import de.signaliduna.elpa.hint.adapter.database.model.MigrationErrorEntity;
import de.signaliduna.elpa.hint.adapter.database.model.MigrationJobEntity;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MigrationJobRepo extends CrudRepository<MigrationJobEntity, Long> {
	List<MigrationJobEntity> findByState(MigrationJobEntity.STATE state);
	List<MigrationJobEntity> findByStateOrderByCreationDateDesc(MigrationJobEntity.STATE state);
}
