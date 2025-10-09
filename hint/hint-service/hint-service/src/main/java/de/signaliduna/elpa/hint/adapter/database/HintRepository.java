package de.signaliduna.elpa.hint.adapter.database;

import de.signaliduna.elpa.hint.adapter.database.model.HintEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;
import java.util.List;

public interface HintRepository extends JpaRepository<HintEntity, Long>, JpaSpecificationExecutor<HintEntity> {
	List<HintEntity> findAllByProcessId(String processId);
	List<HintEntity> findAllByProcessIdAndHintSourceStartingWith(String processId, String hintSourcePrefix);
	boolean existsByMongoUUID(String mongoUUID);
	long countByMongoUUIDIsNotNull();
	Page<HintEntity> findByCreationDateBetweenAndMongoUUIDIsNotNull(LocalDateTime start, LocalDateTime end, Pageable pageable);
}

