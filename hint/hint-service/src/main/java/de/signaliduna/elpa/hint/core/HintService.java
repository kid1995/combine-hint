package de.signaliduna.elpa.hint.core;

import de.signaliduna.elpa.hint.adapter.database.HintRepository;
import de.signaliduna.elpa.hint.adapter.database.HintSpecifications;
import de.signaliduna.elpa.hint.adapter.database.model.HintEntity;
import de.signaliduna.elpa.hint.adapter.mapper.HintMapper;
import de.signaliduna.elpa.hint.model.HintDto;
import de.signaliduna.elpa.hint.model.HintParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;

import java.util.*;

public class HintService {
	private static final Logger log = LoggerFactory.getLogger(HintService.class);
	private final HintRepository hintRepository;
	private final HintMapper hintMapper;
	public HintService(HintRepository hintRepository, HintMapper hintMapper) {
		this.hintRepository = hintRepository;
		this.hintMapper = hintMapper;
	}

	public List<HintDto> getHints(Map<HintParams, Object> queryParams) {
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

	public void saveHints(List<HintDto> hints) {
		hints.forEach(hint -> log.debug("Saving hint: {}", hint));
		final List<HintEntity> hintEntities = hints.stream().map(hintMapper::dtoToEntity).toList();
		hintRepository.saveAll(hintEntities);
	}

	public Optional<HintDto> getHintById(Long id) {
		return hintRepository.findById(id).map(hintMapper::entityToDto);
	}
}

