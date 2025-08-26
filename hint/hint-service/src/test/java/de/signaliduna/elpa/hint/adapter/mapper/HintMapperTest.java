package de.signaliduna.elpa.hint.adapter.mapper;

import de.signaliduna.elpa.hint.adapter.database.legacy.model.HintDao;
import de.signaliduna.elpa.hint.adapter.database.model.HintEntity;
import de.signaliduna.elpa.hint.model.HintDto;
import de.signaliduna.elpa.hint.util.HintTestDataGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("HintMapper Test")
class HintMapperTest {

	private final HintMapper mapper = Mappers.getMapper(HintMapper.class);

	@Nested
	@DisplayName("DTO to Entity")
	class DtoToEntityMapping {
		@Test
		@DisplayName("should map correctly")
		void shouldMapDtoToEntity() {
			HintDto dto = HintTestDataGenerator.createInfoHintDto();
			HintEntity entity = mapper.dtoToEntity(dto);
			assertThat(entity).isNotNull();
			assertThat(entity.getId()).isNull();
			assertThat(entity.getHintSource()).isEqualTo(dto.hintSource());
			assertThat(entity.getMessage()).isEqualTo(dto.message());
			assertThat(entity.getHintCategory()).isEqualTo(dto.hintCategory());
			assertThat(entity.getShowToUser()).isEqualTo(dto.showToUser());
			assertThat(entity.getProcessId()).isEqualTo(dto.processId());
			assertThat(entity.getCreationDate()).isEqualTo(dto.creationDate());
			assertThat(entity.getProcessVersion()).isEqualTo(dto.processVersion());
			assertThat(entity.getResourceId()).isEqualTo(dto.resourceId());
		}
	}

	@Nested
	@DisplayName("Entity to DTO")
	class EntityToDtoMapping {
		@Test
		@DisplayName("should map correctly")
		void shouldMapEntityToDto() {
			HintEntity entity = HintTestDataGenerator.createWarningHintEntity();
			HintDto dto = mapper.entityToDto(entity);
			assertThat(dto).isNotNull();
			assertThat(dto.hintSource()).isEqualTo(entity.getHintSource());
			assertThat(dto.message()).isEqualTo(entity.getMessage());
			assertThat(dto.hintCategory()).isEqualTo(entity.getHintCategory());
			assertThat(dto.showToUser()).isEqualTo(entity.getShowToUser());
			assertThat(dto.processId()).isEqualTo(entity.getProcessId());
			assertThat(dto.creationDate()).isEqualTo(entity.getCreationDate());
			assertThat(dto.processVersion()).isEqualTo(entity.getProcessVersion());
			assertThat(dto.resourceId()).isEqualTo(entity.getResourceId());
		}

		@Test
		@DisplayName("should handle null 'showToUser'")
		void shouldMapEntityToDtoWhenShowToUserIsNull() {
			HintEntity entity = HintTestDataGenerator.createErrorHintEntity();
			entity.setShowToUser(null);
			HintDto dto = mapper.entityToDto(entity);
			assertThat(dto).isNotNull();
			assertThat(dto.showToUser()).isFalse();
			assertThat(dto.hintSource()).isEqualTo(entity.getHintSource());
		}
	}

	@Nested
	@DisplayName("DTO to DAO")
	class DtoToDaoMapping {
		@Test
		@DisplayName("should map correctly")
		void shouldMapDtoToDao() {
			HintDto dto = HintTestDataGenerator.createErrorHintDto();
			HintDao dao = mapper.dtoToDao(dto);
			assertThat(dao).isNotNull();
			assertThat(dao.id()).isNull();
			assertThat(dao.hintSource()).isEqualTo(dto.hintSource());
			assertThat(dao.hintTextOriginal()).isEqualTo(dto.message());
			assertThat(dao.hintCategory()).isEqualTo(dto.hintCategory());
			assertThat(dao.showToUser()).isEqualTo(dto.showToUser());
			assertThat(dao.processId()).isEqualTo(dto.processId());
			assertThat(dao.creationDate()).isEqualTo(dto.creationDate());
			assertThat(dao.processVersion()).isEqualTo(dto.processVersion());
			assertThat(dao.resourceId()).isEqualTo(dto.resourceId());
		}
	}

	@Nested
	@DisplayName("DAO to DTO")
	class DaoToDtoMapping {
		@Test
		@DisplayName("should map correctly")
		void shouldMapDaoToDto() {
			HintDao dao = HintTestDataGenerator.createBlockerHintDao();
			HintDto dto = mapper.daoToDto(dao);
			assertThat(dto).isNotNull();
			assertThat(dto.hintSource()).isEqualTo(dao.hintSource());
			assertThat(dto.message()).isEqualTo(dao.hintTextOriginal());
			assertThat(dto.hintCategory()).isEqualTo(dao.hintCategory());
			assertThat(dto.showToUser()).isEqualTo(dao.showToUser());
			assertThat(dto.processId()).isEqualTo(dao.processId());
			assertThat(dto.creationDate()).isEqualTo(dao.creationDate());
			assertThat(dto.processVersion()).isEqualTo(dao.processVersion());
			assertThat(dto.resourceId()).isEqualTo(dao.resourceId());
		}
	}

	@Nested
	@DisplayName("Nullability Checks")
	class NullabilityChecks {
		@Test
		@DisplayName("should return null for null DTO to Entity")
		void shouldReturnNullForNullDtoToEntity() {
			assertThat(mapper.dtoToEntity(null)).isNull();
		}

		@Test
		@DisplayName("should return null for null Entity to DTO")
		void shouldReturnNullForNullEntityToDto() {
			assertThat(mapper.entityToDto(null)).isNull();
		}

		@Test
		@DisplayName("should return null for null DTO to DAO")
		void shouldReturnNullForNullDtoToDao() {
			assertThat(mapper.dtoToDao(null)).isNull();
		}

		@Test
		@DisplayName("should return null for null DAO to DTO")
		void shouldReturnNullForNullDaoToDto() {
			assertThat(mapper.daoToDto(null)).isNull();
		}
	}
}



