package de.signaliduna.elpa.hint.adapter.database;

import de.signaliduna.elpa.hint.adapter.database.model.HintEntity;
import de.signaliduna.elpa.hint.model.HintDto;
import de.signaliduna.elpa.hint.util.ContainerImageNames;
import de.signaliduna.elpa.hint.util.HintTestDataGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@DisplayName("HintRepository Integration Tests")
class HintRepositoryIT {

	@Autowired
	private HintRepository hintRepository;

	@Container
	@ServiceConnection
	static final PostgreSQLContainer<?> POSTGRES_CONTAINER = new PostgreSQLContainer<>(
		DockerImageName.parse(ContainerImageNames.POSTGRES.getImageName()).asCompatibleSubstituteFor(PostgreSQLContainer.IMAGE)
	);

	@BeforeEach
	void setUp() {
		hintRepository.deleteAll();
	}

	@Nested
	@DisplayName("CRUD Operations")
	class CrudTests {

		@Test
		@DisplayName("Should create and save a new hint")
		void shouldCreateAndSaveHint() {
			HintEntity hint = HintTestDataGenerator.createInfoHintEntity();
			HintEntity saved = hintRepository.save(hint);
			assertThat(saved.getId()).isNotNull();
			assertThat(hintRepository.findById(saved.getId())).isPresent();
		}

		@Test
		@DisplayName("Should read an existing hint")
		void shouldReadHint() {
			HintEntity hint = HintTestDataGenerator.createWarningHintEntity();
			HintEntity saved = hintRepository.save(hint);

			Optional<HintEntity> found = hintRepository.findById(saved.getId());

			assertThat(found).isPresent();
			assertThat(found.get().getHintSource()).isEqualTo(hint.getHintSource());
		}

		@Test
		@DisplayName("Should update an existing hint")
		void shouldUpdateHint() {
			HintEntity hint = HintTestDataGenerator.createErrorHintEntity();
			HintEntity saved = hintRepository.save(hint);

			saved.setMessage("Updated message");
			HintEntity updated = hintRepository.save(saved);

			assertThat(updated.getMessage()).isEqualTo("Updated message");
		}

		@Test
		@DisplayName("Should delete an existing hint")
		void shouldDeleteHint() {
			HintEntity hint = HintTestDataGenerator.createBlockerHintEntity();
			HintEntity saved = hintRepository.save(hint);

			hintRepository.delete(saved);
			Optional<HintEntity> deleted = hintRepository.findById(saved.getId());

			assertThat(deleted).isEmpty();
		}
	}

	@Nested
	@DisplayName("Find by ID")
	class FindByIdTests {

		@Test
		@DisplayName("Should find a hint when it exists")
		void shouldFindById_whenHintExists() {
			HintEntity hint = HintTestDataGenerator.createWarningHintEntity();
			HintEntity saved = hintRepository.save(hint);

			Optional<HintEntity> result = hintRepository.findById(saved.getId());

			assertThat(result).isPresent();
			assertThat(result.get().getHintCategory()).isEqualTo(HintDto.Category.WARNING);
		}

		@Test
		@DisplayName("Should return empty when hint does not exist")
		void shouldReturnEmpty_whenHintNotFound() {
			Optional<HintEntity> notFound = hintRepository.findById(999L);
			assertThat(notFound).isEmpty();
		}
	}

	@Nested
	@DisplayName("Find all by Process ID")
	class FindAllByProcessIdTests {

		@Test
		@DisplayName("Should find all hints for a given process ID")
		void shouldFindAllByProcessId_whenHintsExist() {
			String processId = "test-processId-123";
			List<HintEntity> testHintEntityList = HintTestDataGenerator.creatHintEntityWithSameProcessId(processId);
			hintRepository.saveAll(testHintEntityList);

			List<HintEntity> results = hintRepository.findAllByProcessId(processId);

			assertThat(results).hasSize(testHintEntityList.size());
			assertThat(results).extracting(HintEntity::getProcessId).containsOnly(processId);
		}

		@Test
		@DisplayName("Should return an empty list for a nonexistent process ID")
		void shouldReturnEmptyList_whenProcessIdNotFound() {
			List<HintEntity> emptyResults = hintRepository.findAllByProcessId("nonexistent-process-id");
			assertThat(emptyResults).isEmpty();
		}
	}

	@Nested
	@DisplayName("Find by Process ID and Hint Source Prefix")
	class FindByProcessIdAndHintSourcePrefixTests {
		private final String processId = "test-processId-456";

		@BeforeEach
		void setupHints() {
			HintEntity pasyncHint1 = HintTestDataGenerator.createInfoHintEntity();
			pasyncHint1.setProcessId(processId);

			HintEntity pasyncHint2 = HintTestDataGenerator.createBlockerHintEntity();
			pasyncHint2.setProcessId(processId);

			HintEntity pdcHint = HintEntity.builder()
				.hintSource("PDC-Antragspruefung")
				.message("Der Antrag konnte erfolgreich durch PDC geprüft werden.")
				.hintCategory(HintDto.Category.INFO)
				.showToUser(true)
				.processId(processId)
				.processVersion("1")
				.resourceId("test-resource-pdc")
				.creationDate(LocalDateTime.now())
				.build();

			hintRepository.saveAll(List.of(pasyncHint1, pasyncHint2, pdcHint));
		}

		@Test
		@DisplayName("Should find hints with 'PASYNC' prefix")
		void shouldFindAllByProcessIdAndPasyncHintSourceStartingWith() {
			List<HintEntity> pasyncResults = hintRepository.findAllByProcessIdAndHintSourceStartingWith(processId, "PASYNC");
			assertThat(pasyncResults).hasSize(2);
			assertThat(pasyncResults).extracting(HintEntity::getHintSource).allMatch(source -> source.startsWith("PASYNC"));
		}

		@Test
		@DisplayName("Should find hints with 'PDC' prefix")
		void shouldFindAllByProcessIdAndPdcHintSourceStartingWith() {
			List<HintEntity> pdcResults = hintRepository.findAllByProcessIdAndHintSourceStartingWith(processId, "PDC");
			assertThat(pdcResults).hasSize(1);
			assertThat(pdcResults.getFirst().getHintSource()).isEqualTo("PDC-Antragspruefung");
		}

		@Test
		@DisplayName("Should return empty for non-matching prefix")
		void shouldReturnEmptyForNonMatchingPrefix() {
			List<HintEntity> emptyResults = hintRepository.findAllByProcessIdAndHintSourceStartingWith(processId, "NONEXISTENT");
			assertThat(emptyResults).isEmpty();
		}
	}
}

