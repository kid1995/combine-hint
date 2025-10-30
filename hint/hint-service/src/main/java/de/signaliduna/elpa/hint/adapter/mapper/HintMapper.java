package de.signaliduna.elpa.hint.adapter.mapper;

import de.signaliduna.elpa.hint.adapter.database.model.HintEntity;
import de.signaliduna.elpa.hint.model.HintDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface HintMapper {

	@Mapping(target = "id", ignore = true)
	HintEntity dtoToEntity(HintDto dto);

	HintDto entityToDto(HintEntity entity);
}



