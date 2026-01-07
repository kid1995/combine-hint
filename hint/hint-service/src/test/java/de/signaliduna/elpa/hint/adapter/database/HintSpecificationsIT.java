package de.signaliduna.elpa.hint.adapter.database;

import de.signaliduna.elpa.hint.adapter.database.model.HintEntity;
import de.signaliduna.elpa.hint.model.HintDto;
import de.signaliduna.elpa.hint.model.HintParams;
import de.signaliduna.elpa.hint.util.ContainerImageNames;
import de.signaliduna.elpa.hint.util.HintTestDataGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@TestPropertySource(
	properties = {
		"spring.jpa.hibernate.ddl-auto=create-drop",
	}
)
class HintSpecificationsIT{

	@Autowired
	private HintRepository hintRepository;

	@Container
	@ServiceConnection
	static final PostgreSQLContainer POSTGRES_CONTAINER = new PostgreSQLContainer(
		DockerImageName.parse(ContainerImageNames.POSTGRES.getImageName()).asCompatibleSubstituteFor(PostgreSQLContainer.IMAGE)
	).withInitScript("db/init-hint-schema.sql");

	@BeforeEach
	void setUp() {
		hintRepository.deleteAll();
	}


	@Test
	@DisplayName("Should filter by exact hint source")
	void shouldFilterByExactHintSource() {
		// Given
		List<HintEntity> testHints = HintTestDataGenerator.createAllCategoryHintEntitys();
		hintRepository.saveAll(testHints);
		String targetHintSource = testHints.getFirst().getHintSource();

		// When
		Map<HintParams, Object> queryParams = new EnumMap<>(HintParams.class);
		queryParams.put(HintParams.HINT_SOURCE, targetHintSource);
		Specification<HintEntity> spec = HintSpecifications.fromQuery(queryParams);
		List<HintEntity> results = hintRepository.findAll(spec);

		// Then
		assertThat(results).hasSize(1);
		assertThat(results.getFirst().getHintSource()).isEqualTo(targetHintSource);
	}

	@Test
	@DisplayName("Should filter by hint source prefix")
	void shouldFilterByPasyncHintSourcePrefix() {
		// Given - Add more PASYNC hints for better testing
		List<HintEntity> pasyncHints = List.of(
			HintTestDataGenerator.createInfoHintEntity(),
			HintTestDataGenerator.createBlockerHintEntity()
		);
		hintRepository.saveAll(pasyncHints);

		// When
		Map<HintParams, Object> queryParams = new EnumMap<>(HintParams.class);
		queryParams.put(HintParams.HINT_SOURCE_PREFIX, "PASYNC");
		Specification<HintEntity> spec = HintSpecifications.fromQuery(queryParams);
		List<HintEntity> results = hintRepository.findAll(spec);

		// Then
		assertThat(results).hasSizeGreaterThanOrEqualTo(2);
		assertThat(results)
			.extracting(HintEntity::getHintSource)
			.allMatch(source -> source.startsWith("PASYNC"));
	}

	@Test
	@DisplayName("Should filter by hint category")
	void shouldFilterByBlockerHintCategory() {
		// Given
		List<HintEntity> testHints = HintTestDataGenerator.createAllCategoryHintEntitys();
		hintRepository.saveAll(testHints);

		// When
		Map<HintParams, Object> queryParams = new EnumMap<>(HintParams.class);
		queryParams.put(HintParams.HINT_CATEGORY, HintDto.Category.BLOCKER);
		Specification<HintEntity> spec = HintSpecifications.fromQuery(queryParams);
		List<HintEntity> results = hintRepository.findAll(spec);

		// Then
		assertThat(results).hasSize(1);
		assertThat(results).first().extracting(HintEntity::getHintCategory).isEqualTo(HintDto.Category.BLOCKER);

	}

	@Test
	@DisplayName("Should filter by process ID")
	void shouldFilterByProcessId() {
		// Given
		HintEntity matchingHint = HintEntity.builder()
			.hintSource("PASYNC-Identifizierung")
			.message("Specific process hint")
			.hintCategory(HintDto.Category.INFO)
			.showToUser(true)
			.processId("test-processId-specific")
			.processVersion("1")
			.resourceId("test-resource-specific")
			.creationDate(java.time.LocalDateTime.now())
			.build();
		hintRepository.save(matchingHint);

		// create 4 dummies HintEntity
		List<HintEntity> testHints = HintTestDataGenerator.creatHintEntityWithSameProcessId("other-test-processId");
		hintRepository.saveAll(testHints);

		// When
		Map<HintParams, Object> queryParams = new EnumMap<>(HintParams.class);
		queryParams.put(HintParams.PROCESS_ID, matchingHint.getProcessId());
		Specification<HintEntity> spec = HintSpecifications.fromQuery(queryParams);
		List<HintEntity> results = hintRepository.findAll(spec);

		// Then
		assertThat(results).hasSize(1);
		assertThat(results.getFirst().getProcessId()).isEqualTo(matchingHint.getProcessId());
	}

	@Test
	@DisplayName("Should apply multiple filters with AND logic")
	void shouldApplyMultipleFiltersWithAndLogic() {
		// Given - add some test data to prove the query
		HintEntity matchingHint = HintEntity.builder()
			.hintSource("PASYNC-Validations")
			.message("Matching hint")
			.hintCategory(HintDto.Category.BLOCKER)
			.showToUser(true)
			.processId("test-processId-matching")
			.processVersion("1")
			.resourceId("test-resource-matching")
			.creationDate(java.time.LocalDateTime.now())
			.build();

		hintRepository.saveAll(
			List.of(
				HintTestDataGenerator.createInfoHintEntity(),
				HintTestDataGenerator.createWarningHintEntity(),
				HintTestDataGenerator.createErrorHintEntity(),
				matchingHint
			)
		);

		// When
		Map<HintParams, Object> queryParams = new EnumMap<>(HintParams.class);
		queryParams.put(HintParams.HINT_SOURCE_PREFIX, "PASYNC");
		queryParams.put(HintParams.HINT_CATEGORY, HintDto.Category.BLOCKER);
		queryParams.put(HintParams.PROCESS_ID, matchingHint.getProcessId());
		Specification<HintEntity> spec = HintSpecifications.fromQuery(queryParams);
		List<HintEntity> results = hintRepository.findAll(spec);

		// Then
		assertThat(results).hasSize(1);
		HintEntity result = results.getFirst();
		assertThat(result.getHintCategory()).isEqualTo(HintDto.Category.BLOCKER);
		assertThat(result.getHintSource()).isEqualTo(matchingHint.getHintSource());
		assertThat(result.getShowToUser()).isTrue();
		assertThat(result.getProcessId()).isEqualTo(matchingHint.getProcessId());
	}

	@Test
	@DisplayName("Should return all handle empty query parameters")
	void shouldHandleEmptyQueryParameters() {
		// Given
		hintRepository.saveAll(
			HintTestDataGenerator.createAllCategoryHintEntitys()
		);

		// When
		Map<HintParams, Object> queryParams = new EnumMap<>(HintParams.class);
		Specification<HintEntity> spec = HintSpecifications.fromQuery(queryParams);
		List<HintEntity> results = hintRepository.findAll(spec);

		// Verify
		assertThat(results).hasSize(4);
	}

	@Test
	@DisplayName("Should ignore null values in query parameters")
	void shouldIgnoreNullValuesInQueryParameters() {
		// Given
		HintEntity matchingHint = HintEntity.builder()
			.hintSource("PASYNC-Validations")
			.message("Matching hint")
			.hintCategory(HintDto.Category.BLOCKER)
			.showToUser(true)
			.processId("test-processId-matching")
			.processVersion("1")
			.resourceId("test-resource-matching")
			.creationDate(java.time.LocalDateTime.now())
			.build();
		hintRepository.save(matchingHint);



		// When
		Map<HintParams, Object> queryParams = new EnumMap<>(HintParams.class);
		queryParams.put(HintParams.HINT_SOURCE_PREFIX, "PASYNC");
		queryParams.put(HintParams.HINT_CATEGORY, null); // Should be ignored
		queryParams.put(HintParams.SHOW_TO_USER, true);
		Specification<HintEntity> spec = HintSpecifications.fromQuery(queryParams);
		List<HintEntity> results = hintRepository.findAll(spec);

		assertThat(results).hasSize(1);
		assertThat(results.getFirst().getHintCategory()).isEqualTo(HintDto.Category.BLOCKER);
		assertThat(results.getFirst().getShowToUser()).isTrue();
	}

	@Test
	@DisplayName("Should handle complex query combinations")
	void shouldHandleComplexQueryCombinations() {
		// Given - Create additional test data for complex scenario
		String targetProcessId = "test-processId-specific";
		HintEntity targetHintEntity = HintTestDataGenerator.createInfoHintEntity();
		targetHintEntity.setProcessId(targetProcessId);
		targetHintEntity.setShowToUser(true);
		List<HintEntity> testHints = List.of(
			HintTestDataGenerator.createInfoHintEntity(),
			HintTestDataGenerator.createWarningHintEntity(),
			HintTestDataGenerator.createBlockerHintEntity(),
                targetHintEntity
		);
		hintRepository.saveAll(testHints);

		// When
		Map<HintParams, Object> queryParams = new EnumMap<>(HintParams.class);
		queryParams.put(HintParams.PROCESS_ID, targetProcessId);
		queryParams.put(HintParams.HINT_CATEGORY, HintDto.Category.INFO);
		queryParams.put(HintParams.SHOW_TO_USER, true);

		Specification<HintEntity> spec = HintSpecifications.fromQuery(queryParams);
		List<HintEntity> results = hintRepository.findAll(spec);

		// Then
		assertThat(results)
			.isNotEmpty()
			.allMatch(hint -> hint.getHintCategory() == HintDto.Category.INFO &&
				hint.getShowToUser() &&
				hint.getProcessVersion().equals("1"));
	}
}
