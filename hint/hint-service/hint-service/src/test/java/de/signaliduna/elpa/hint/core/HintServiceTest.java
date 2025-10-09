package de.signaliduna.elpa.hint.core;

import de.signaliduna.elpa.hint.adapter.database.HintRepository;
import de.signaliduna.elpa.hint.adapter.database.HintSpecifications;
import de.signaliduna.elpa.hint.adapter.database.legacy.HintRepositoryLegacy;
import de.signaliduna.elpa.hint.adapter.database.legacy.HintRepositoryLegacyCustom;
import de.signaliduna.elpa.hint.adapter.database.legacy.model.HintDao;
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
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("HintService Test")
class HintServiceTest {

	@Mock
	private HintRepository hintRepository;

	@Mock
	private HintRepositoryLegacy hintRepositoryLegacy;

	@Mock
	private HintRepositoryLegacyCustom hintRepositoryLegacyCustom;

	@Spy
	private HintMapper hintMapper = Mappers.getMapper(HintMapper.class);

	@InjectMocks
	private HintService hintService;

	@Mock
	private Specification<HintEntity> mockSpec;

	@Nested
	@DisplayName("test getHints")
	class GetHints {

		@Nested
		@DisplayName("with postgres")
		class FindHintsInPostgresDB {

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
					List<HintEntity> expectedEntities = List.of(HintTestDataGenerator.createWarningHintEntity());
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
						when(hintRepositoryLegacyCustom.findAllByQuery(queryParams)).thenReturn(Collections.emptyList());

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
						when(hintRepositoryLegacyCustom.findAllByQuery(queryParams)).thenReturn(Collections.emptyList());


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
					verifyNoInteractions(hintRepositoryLegacyCustom);
				}
			}
		}

		@Nested
		@DisplayName("with mongo (legacy db)")
		class FallbackToLegacyDB {

			@Test
			@DisplayName("should call legacy repository when no hints are found in Postgres")
			void shouldFallbackToLegacyWhenPostgresReturnsEmpty() {
				// Given
				String processId = "E456";
				Map<HintParams, Object> queryParams = Map.of(HintParams.PROCESS_ID, processId);
				List<HintDao> legacyDaos = List.of(HintTestDataGenerator.createWarningHintDao());

				when(hintRepository.findAllByProcessId(processId)).thenReturn(Collections.emptyList());
				when(hintRepositoryLegacyCustom.findAllByQuery(queryParams)).thenReturn(legacyDaos);

				// When
				List<HintDto> result = hintService.getHints(queryParams);

				// Then
				assertThat(result).hasSize(legacyDaos.size());
				verify(hintRepository).findAllByProcessId(processId);
				verify(hintRepositoryLegacyCustom).findAllByQuery(queryParams);
			}

			@Test
			@DisplayName("should return empty list when both repositories find no hints")
			void shouldReturnEmptyWhenBothRepositoriesAreEmpty() {
				// Given
				String processId = "p-not-exist";
				Map<HintParams, Object> queryParams = Map.of(HintParams.PROCESS_ID, processId);

				when(hintRepository.findAllByProcessId(processId)).thenReturn(Collections.emptyList());
				when(hintRepositoryLegacyCustom.findAllByQuery(queryParams)).thenReturn(Collections.emptyList());

				// When
				List<HintDto> result = hintService.getHints(queryParams);

				// Then
				assertThat(result).isEmpty();
				verify(hintRepository).findAllByProcessId(processId);
				verify(hintRepositoryLegacyCustom).findAllByQuery(queryParams);
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

		private static Stream<Arguments> provideGetHintByIdArguments() {
			return Stream.of(
				Arguments.of("12345", true, true, "existed in postgres"),
				Arguments.of("507f1f77bcf86cd799439011", false, true, "existed in mongo"),
				Arguments.of("99999", true, false, "not existed postgres"), //
				Arguments.of("507f1f77bcf86cd799439022", false, false, "not existed mongo")
			);
		}

		@DisplayName("with valid id")
		@ParameterizedTest(name = "{3}")
		@MethodSource("provideGetHintByIdArguments")
		void shouldFindHintById(String id, boolean isPostgresId, boolean shouldBePresent, String testTitle) {
			// Given
			if (isPostgresId) {
				Optional<HintEntity> entity = shouldBePresent ? Optional.of(HintTestDataGenerator.createInfoHintEntity()) : Optional.empty();
				when(hintRepository.findById(Long.parseLong(id))).thenReturn(entity);
			} else {
				Optional<HintDao> dao = shouldBePresent ? Optional.of(HintTestDataGenerator.createErrorHintDao()) : Optional.empty();
				when(hintRepositoryLegacy.findById(id)).thenReturn(dao);
			}

			// When
			Optional<HintDto> result = hintService.getHintById(id);

			// Then
			assertThat(result.isPresent()).isEqualTo(shouldBePresent);
			if (isPostgresId) {
				verify(hintRepository).findById(Long.parseLong(id));
				verifyNoInteractions(hintRepositoryLegacy);
			} else {
				verify(hintRepositoryLegacy).findById(id);
				verifyNoInteractions(hintRepository);
			}
		}

		private static Stream<Arguments> provideInvalidIdArguments() {
			String invalidFormatId = "invalid-id-format-123";
			return Stream.of(
				Arguments.of(null, "ID cannot be null or empty", "null-value"),
				Arguments.of("  ", "ID cannot be null or empty", "empty-value"),
				Arguments.of(invalidFormatId, "Unknown hint ID: " + invalidFormatId, "invalid-format-value")
			);
		}

		@DisplayName("with invalid id")
		@ParameterizedTest(name = "{2}")
		@MethodSource("provideInvalidIdArguments")
		void shouldThrowExceptionForInvalidId(String invalidId, String expectedMessage, String testTitle) {
			// When & Then
			assertThatThrownBy(() -> hintService.getHintById(invalidId))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage(expectedMessage);
		}
	}
}

