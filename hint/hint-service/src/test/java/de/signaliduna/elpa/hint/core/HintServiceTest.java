package de.signaliduna.elpa.hint.core;

import de.signaliduna.elpa.hint.adapter.database.HintRepository;
import de.signaliduna.elpa.hint.adapter.database.HintSpecifications;
import de.signaliduna.elpa.hint.adapter.database.model.HintEntity;
import de.signaliduna.elpa.hint.adapter.mapper.HintMapper;
import de.signaliduna.elpa.hint.model.HintDto;
import de.signaliduna.elpa.hint.model.HintParams;
import de.signaliduna.elpa.hint.util.HintTestDataGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mapstruct.factory.Mappers;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("HintService Test")
class HintServiceTest {

	@Mock
	private HintRepository hintRepository;

	@Spy
	private HintMapper hintMapper = Mappers.getMapper(HintMapper.class);

	@InjectMocks
	private HintService hintService;

	@Mock
	private Specification<HintEntity> mockSpec;

	@Nested
	@DisplayName("test getHints")
	class GetHints {

		private static Stream<Arguments> provideArgs4FirstCondition() {
			return Stream.of(
				Arguments.of(
					"True: Only PROCESS_ID is present",
					Map.of(HintParams.PROCESS_ID, "E123"),
					true
				),
				Arguments.of(
					"False: PROCESS_ID key is missing",
					Map.of(), // Empty map
					false
				),
				Arguments.of(
					"False: Size is not 1",
					Map.of(HintParams.PROCESS_ID, "E123", HintParams.HINT_CATEGORY, "INFO"),
					false
				)
			);
		}

		@DisplayName("if queryParams contains only PROCESS_ID (PROCESS_ID Path)")
		@ParameterizedTest(name = "{0}")
		@MethodSource("provideArgs4FirstCondition")
		void testIfBranchWithAllCases(String caseName, Map<HintParams, Object> queryParams, boolean isConditionTrue) {
			if (isConditionTrue) {
				// Given - Test the successful path
				String processId = (String) queryParams.get(HintParams.PROCESS_ID);
				List<HintEntity> expectedEntities = List.of(HintTestDataGenerator.createInfoHintEntity());
				when(hintRepository.findAllByProcessId(processId)).thenReturn(expectedEntities);

				// When
				List<HintDto> result = hintService.getHints(queryParams);

				// Then
				assertThat(result).hasSize(expectedEntities.size());
				verify(hintRepository).findAllByProcessId(processId);
				verify(hintRepository, never()).findAll(mockSpec);

			} else {
				try (MockedStatic<HintSpecifications> mockedStatic = mockStatic(HintSpecifications.class)) {
					mockedStatic.when(() -> HintSpecifications.fromQuery(queryParams)).thenReturn(mockSpec);
					when(hintRepository.findAll(mockSpec)).thenReturn(Collections.emptyList());

					// When
					hintService.getHints(queryParams);

					// Then - Verify the logic fell through and did NOT use the findAllByProcessId method
					verify(hintRepository, never()).findAllByProcessId(anyString());
					verify(hintRepository).findAll(mockSpec); // Confirms it fell through to the end
				}
			}
		}

		private static Stream<Arguments> provideArgs4SecondCondition() {
			return Stream.of(
				Arguments.of(
					"True: All conditions met",
					Map.of(HintParams.PROCESS_ID, "E123", HintParams.HINT_SOURCE_PREFIX, "PASYNC"),
					true
				),
				Arguments.of(
					"False: PROCESS_ID is missing",
					Map.of(HintParams.HINT_SOURCE_PREFIX, "PASYNC"),
					false
				),
				Arguments.of(
					"False: queryParams.size() is not 2",
					Map.of(
						HintParams.PROCESS_ID, "E123",
						HintParams.HINT_SOURCE_PREFIX, "PASYNC",
						HintParams.HINT_CATEGORY, "INFO"
					),
					false
				)
			);
		}

		@DisplayName("if queryParams contains only PROCESS_ID and HINT_SOURCE_PREFIX (HINT_SOURCE_PREFIX Path)")
		@ParameterizedTest(name = "{0}")
		@MethodSource("provideArgs4SecondCondition")
		void testElseIfBranchWithAllCases(String caseName, Map<HintParams, Object> queryParams, boolean isConditionTrue) {
			if (isConditionTrue) {
				// Given - Test the successful path
				String processId = (String) queryParams.get(HintParams.PROCESS_ID);
				String prefix = (String) queryParams.get(HintParams.HINT_SOURCE_PREFIX);
				List<HintEntity> expectedEntities = List.of(HintTestDataGenerator.createInfoHintEntity());
				when(hintRepository.findAllByProcessIdAndHintSourceStartingWith(processId, prefix)).thenReturn(expectedEntities);

				// When
				List<HintDto> result = hintService.getHints(queryParams);

				// Then
				assertThat(result).hasSize(expectedEntities.size());
				verify(hintRepository).findAllByProcessIdAndHintSourceStartingWith(processId, prefix);
				verify(hintRepository, never()).findAll(mockSpec);

			} else {
				try (MockedStatic<HintSpecifications> mockedStatic = mockStatic(HintSpecifications.class)) {
					mockedStatic.when(() -> HintSpecifications.fromQuery(queryParams)).thenReturn(mockSpec);
					when(hintRepository.findAll(mockSpec)).thenReturn(Collections.emptyList());

					// When
					hintService.getHints(queryParams);

					// Then
					verify(hintRepository, never()).findAllByProcessIdAndHintSourceStartingWith(anyString(), anyString());
					verify(hintRepository).findAll(mockSpec);
				}
			}
		}

		@Test
		@DisplayName("if queryParams contains any combinations (Specification Path)")
		void shouldUseSpecificationForOtherCombinations() {
			// Given
			Map<HintParams, Object> queryParams = Map.of(
				HintParams.PROCESS_ID, "E123",
				HintParams.HINT_CATEGORY, HintDto.Category.ERROR
			);
			List<HintEntity> expectedEntities = List.of(HintTestDataGenerator.createErrorHintEntity());

			try (MockedStatic<HintSpecifications> mockedStatic = mockStatic(HintSpecifications.class)) {
				mockedStatic.when(() -> HintSpecifications.fromQuery(queryParams)).thenReturn(mockSpec);
				when(hintRepository.findAll(mockSpec)).thenReturn(expectedEntities);

				// When
				List<HintDto> result = hintService.getHints(queryParams);

				// Then
				assertThat(result).hasSize(expectedEntities.size());
				verify(hintRepository).findAll(mockSpec);
				verify(hintRepository, never()).findAllByProcessId(anyString());
				verify(hintRepository, never()).findAllByProcessIdAndHintSourceStartingWith(anyString(), anyString());
			}
		}

	}

	@Nested
	@DisplayName("test saveHints")
	class SaveHints {
		@Test
		@DisplayName("successfully")
		void shouldMapDtosToEntitiesAndSave() {
			// Given
			List<HintDto> dtosToSave = List.of(HintTestDataGenerator.createInfoHintDto(), HintTestDataGenerator.createBlockerHintDto());
			@SuppressWarnings("unchecked")
			ArgumentCaptor<List<HintEntity>> argumentCaptor = ArgumentCaptor.forClass(List.class);

			// When
			hintService.saveHints(dtosToSave);

			// Then
			verify(hintRepository).saveAll(argumentCaptor.capture());
			List<HintEntity> savedEntities = argumentCaptor.getValue();
			assertThat(savedEntities).hasSize(2);
			assertThat(savedEntities.get(0).getMessage()).isEqualTo(dtosToSave.get(0).message());
			assertThat(savedEntities.get(1).getHintCategory()).isEqualTo(dtosToSave.get(1).hintCategory());
		}
	}

	@Nested
	@DisplayName("test getHintById")
	class GetHintById {

		@Test
		@DisplayName("successfully returns a hint when found")
		void shouldReturnHintWhenFound() {
			// Given
			Long hintId = 1L;
			HintEntity hintEntity = HintTestDataGenerator.createInfoHintEntity();
			hintEntity.setId(hintId);

			when(hintRepository.findById(hintId)).thenReturn(java.util.Optional.of(hintEntity));
			when(hintMapper.entityToDto(any(HintEntity.class))).thenCallRealMethod();

			// When
			java.util.Optional<HintDto> result = hintService.getHintById(hintId);

			// Then
			assertThat(result).isPresent();
			assertThat(result.get().message()).isEqualTo(hintEntity.getMessage());
			verify(hintRepository).findById(hintId);
			verify(hintMapper).entityToDto(hintEntity);
		}

		@Test
		@DisplayName("returns empty optional when hint not found")
		void shouldReturnEmptyOptionalWhenNotFound() {
			// Given
			Long hintId = 99L;
			when(hintRepository.findById(hintId)).thenReturn(java.util.Optional.empty());

			// When
			java.util.Optional<HintDto> result = hintService.getHintById(hintId);

			// Then
			assertThat(result).isNotPresent();
			verify(hintRepository).findById(hintId);
			verify(hintMapper, never()).entityToDto(any(HintEntity.class));
		}
	}
}

