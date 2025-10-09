package de.signaliduna.elpa.hint.adapter.database;

import de.signaliduna.elpa.hint.adapter.database.model.MigrationErrorEntity;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MigrationErrorRepo extends CrudRepository<MigrationErrorEntity, Long>{
	List<MigrationErrorEntity> findByJob_IdAndResolved(Long jobID, Boolean resolved);
}
