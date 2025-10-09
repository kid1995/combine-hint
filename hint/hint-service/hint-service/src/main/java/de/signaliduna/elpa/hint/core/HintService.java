package de.signaliduna.elpa.hint.core;

import de.signaliduna.elpa.hint.adapter.database.HintRepository;
import de.signaliduna.elpa.hint.adapter.database.HintSpecifications;
import de.signaliduna.elpa.hint.adapter.database.legacy.HintRepositoryLegacy;
import de.signaliduna.elpa.hint.adapter.database.legacy.HintRepositoryLegacyCustom;
import de.signaliduna.elpa.hint.adapter.database.model.HintEntity;
import de.signaliduna.elpa.hint.adapter.mapper.HintMapper;
import de.signaliduna.elpa.hint.model.HintDto;
import de.signaliduna.elpa.hint.model.HintParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;

import java.util.*;
import java.util.regex.Pattern;

public class HintService {
	private static final Logger log = LoggerFactory.getLogger(HintService.class);
	// objectId always have 24 digits
	public static final Pattern MONGODB_OBJECTID_PATTERN = Pattern.compile("^[0-9a-fA-F]{24}$");
	// max bigint have 19 digits
	public static final Pattern POSTGRES_LONG_ID_PATTERN = Pattern.compile("^[1-9]\\d{0,18}$");


	private final HintRepository hintRepository;
	private final HintRepositoryLegacy hintRepositoryLegacy;
	private final HintRepositoryLegacyCustom hintRepositoryLegacyCustom;
	private final HintMapper hintMapper;

	public HintService(HintRepository hintRepository,
										 HintRepositoryLegacy hintRepositoryLegacy,
										 HintRepositoryLegacyCustom hintRepositoryLegacyCustom,
										 HintMapper hintMapper) {
		this.hintRepository = hintRepository;
		this.hintRepositoryLegacy = hintRepositoryLegacy;
		this.hintRepositoryLegacyCustom = hintRepositoryLegacyCustom;
		this.hintMapper = hintMapper;
	}

	public List<HintDto> getHints(Map<HintParams, Object> queryParams) {
		List<HintDto> foundHints = this.findHintsInPostgresDB(queryParams);
		if (foundHints.isEmpty()) {
			return this.hintRepositoryLegacyCustom.findAllByQuery(queryParams).stream().map(hintMapper::daoToDto).toList();
		}
		return foundHints;
	}

	public void saveHints(List<HintDto> hints) {
		hints.forEach(hint -> log.debug("Saving hint: {}", hint));
		final List<HintEntity> hintEntities = hints.stream().map(hintMapper::dtoToEntity).toList();
		hintRepository.saveAll(hintEntities);
	}

	public Optional<HintDto> getHintById(String id) {
		if (id == null || id.trim().isEmpty()) {
			throw new IllegalArgumentException("ID cannot be null or empty");
		}

		String trimmedId = id.trim();

		if (isPostgresLongId(trimmedId)) {
			log.debug("Searching for hint in PostgreSQL database with ID: {}", trimmedId);
			Long longId = Long.parseLong(trimmedId);
			return hintRepository.findById(longId)
				.map(hintMapper::entityToDto);
		} else if (isMongoDbObjectId(trimmedId)) {
			log.debug("Searching for hint in MongoDB database with ID: {}", trimmedId);
			return hintRepositoryLegacy.findById(id)
				.map(hintMapper::daoToDto);
		} else {
			throw new IllegalArgumentException(String.format("Unknown hint ID: %s", trimmedId));
		}
	}

	public List<HintDto> findHintsInPostgresDB(Map<HintParams, Object> queryParams) {
		List<HintEntity> hintEntities = new ArrayList<>();
		if (queryParams.containsKey(HintParams.PROCESS_ID) && queryParams.size() == 1) {
			hintEntities.addAll(
				this.hintRepository.findAllByProcessId(queryParams.get(HintParams.PROCESS_ID).toString()));
		} else if (queryParams.containsKey(HintParams.PROCESS_ID) && queryParams.containsKey(HintParams.HINT_SOURCE_PREFIX) && queryParams.size() == 2) {
			String processId = queryParams.get(HintParams.PROCESS_ID).toString();
			String hintSourcePrefix = queryParams.get(HintParams.HINT_SOURCE_PREFIX).toString();
			hintEntities.addAll(
				this.hintRepository.findAllByProcessIdAndHintSourceStartingWith(processId, hintSourcePrefix));
		} else {
			Specification<HintEntity> hintEntitySpecification = HintSpecifications.fromQuery(queryParams);
			hintEntities.addAll(
				this.hintRepository.findAll(hintEntitySpecification)
			);
		}
		return hintEntities.stream().map(hintMapper::entityToDto).toList();
	}

	private boolean isMongoDbObjectId(String id) {
		return MONGODB_OBJECTID_PATTERN.matcher(id).matches();
	}

	private boolean isPostgresLongId(String id) {
		return POSTGRES_LONG_ID_PATTERN.matcher(id).matches();
	}

}

