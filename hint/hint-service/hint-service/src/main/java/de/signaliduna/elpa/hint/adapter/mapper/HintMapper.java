package de.signaliduna.elpa.hint.adapter.mapper;


import de.signaliduna.elpa.hint.adapter.database.legacy.model.HintDao;
import de.signaliduna.elpa.hint.adapter.database.model.HintEntity;
import de.signaliduna.elpa.hint.model.HintDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface HintMapper {

	@Mapping(target = "id", ignore = true)
	HintEntity dtoToEntity(HintDto dto);


	HintDto entityToDto(HintEntity entity);


	@Mapping(source = "message", target = "hintTextOriginal")
	@Mapping(target = "id", ignore = true)
	HintDao dtoToDao(HintDto dto);


	@Mapping(source = "hintTextOriginal", target = "message")
	HintDto daoToDto(HintDao hintDao);
}



