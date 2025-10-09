package de.signaliduna.elpa.hint.adapter.database.legacy;

import com.c4_soft.springaddons.security.oauth2.test.webmvc.AutoConfigureAddonsWebmvcResourceServerSecurity;
import de.signaliduna.elpa.hint.adapter.database.legacy.model.HintDao;
import de.signaliduna.elpa.hint.config.WebSecurityConfig;
import de.signaliduna.elpa.hint.model.HintDto;
import de.signaliduna.elpa.hint.model.HintParams;
import de.signaliduna.elpa.hint.util.AbstractSingletonContainerTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@DataMongoTest
@AutoConfigureAddonsWebmvcResourceServerSecurity
@Import({WebSecurityConfig.class, HintRepositoryLegacyCustomImpl.class})
class HintRepositoryLegacyCustomTest extends AbstractSingletonContainerTest {

	@Autowired
	HintRepositoryLegacyCustom hintRepositoryCustomAdapter;
	@Autowired
	private MongoTemplate mongoTemplate;

	@AfterEach
	void tearDown() {
		mongoTemplate.dropCollection("Hint");
	}

	@Test
	void findAllByEmptyQuery() {
		final HintDao hintDao1 = new HintDao("12345", "ELISA", "Test1", HintDto.Category.INFO, true, "E12345", LocalDateTime.of(2023, 5, 22, 8, 0), "1", "resourceId-1");
		mongoTemplate.insert(hintDao1);
		final HintDao hintDao2 = new HintDao("23456", "ELISA", "Test2", HintDto.Category.INFO, true, "E23456", LocalDateTime.of(2023, 5, 22, 8, 0), "2", "resourceId-2");
		mongoTemplate.insert(hintDao2);
		Map<HintParams, Object> queryParams = Map.of();

		List<HintDao> hintDaos = hintRepositoryCustomAdapter.findAllByQuery(queryParams);

		assertThat(hintDaos).containsExactly(hintDao1, hintDao2);
	}

	@Test
	void findAllByFullQuery() {
		final HintDao hintDao = new HintDao("12345", "ELISA", "Test", HintDto.Category.INFO, true, "E12345", LocalDateTime.of(2023, 5, 22, 8, 0), "1", "resourceId-1");
		mongoTemplate.insert(hintDao);
		Map<HintParams, Object> queryParams = Map.of(
			HintParams.HINT_SOURCE, "ELISA",
			HintParams.HINT_TEXT_ORIGINAL, "Test",
			HintParams.HINT_CATEGORY, "INFO",
			HintParams.PROCESS_ID, "E12345",
			HintParams.SHOW_TO_USER, true,
			HintParams.PROCESS_VERSION, "1",
			HintParams.RESOURCE_ID, "resourceId-1"
		);

		List<HintDao> hintDaos = hintRepositoryCustomAdapter.findAllByQuery(queryParams);

		assertThat(hintDaos).containsExactly(hintDao);
	}

	@ParameterizedTest
	@ArgumentsSource(WrongQueryArgumentProvider.class)
	void findNoneByFullQueryWithOneWrongValue(Map<HintParams, Object> queryParams, HintDao hintDao) {
		mongoTemplate.insert(hintDao);

		List<HintDao> hintDaos = hintRepositoryCustomAdapter.findAllByQuery(queryParams);

		assertThat(hintDaos).isEmpty();
	}

	@ParameterizedTest
	@CsvSource({
		"id,12345",
		"hintSource,ELISA",
		"hintTextOriginal,Test",
		"hintCategory,INFO",
		"processId,E12345",
		"processVersion,1"
	})
	void findAllBySingleQuery(String key, String value) {
		final HintDao hintDao = new HintDao("12345", "ELISA", "Test", HintDto.Category.INFO, true, "E12345", LocalDateTime.of(2023, 5, 22, 8, 0), "1", "resourceId-1");
		mongoTemplate.insert(hintDao);
		Map<HintParams, Object> queryParams = Map.of(convertString2HintParams(key), value);

		List<HintDao> hintDaos = hintRepositoryCustomAdapter.findAllByQuery(queryParams);

		assertThat(hintDaos).containsExactly(hintDao);
	}

	@ParameterizedTest
	@CsvSource({
		"id,23456",
		"hintSource,ELPA",
		"hintTextOriginal,NotTest",
		"hintCategory,WARN",
		"processId,E23456",
		"processVersion,2"
	})
	void findAllNoneBySingleQueryWithWrongValue(String key, String value) {
		final HintDao hintDao = new HintDao("12345", "ELISA", "Test", HintDto.Category.INFO, true, "E12345", LocalDateTime.of(2023, 5, 22, 8, 0), "1", "resourceId-1");
		mongoTemplate.insert(hintDao);
		Assertions.assertNotNull(key);
		Map<HintParams, Object> queryParams = Map.of(convertString2HintParams(key), value);

		List<HintDao> hintDaos = hintRepositoryCustomAdapter.findAllByQuery(queryParams);

		assertThat(hintDaos).isEmpty();
	}

	@Test
	void findAllBySingleQueryShowToUser() {
		final HintDao hintDaoForUser = new HintDao("12345", "ELISA", "Test", HintDto.Category.INFO, true, "E12345", LocalDateTime.of(2023, 5, 22, 8, 0), "1", "resourceId-1");
		mongoTemplate.insert(hintDaoForUser);
		final HintDao hintDaoNotForUser = new HintDao("23456", "ELISA", "Test", HintDto.Category.INFO, false, "E23456", LocalDateTime.of(2023, 5, 22, 8, 0), "1", "resourceId-2");
		mongoTemplate.insert(hintDaoNotForUser);
		Map<HintParams, Object> queryParams = Map.of(HintParams.SHOW_TO_USER, true);

		List<HintDao> hintDaos = hintRepositoryCustomAdapter.findAllByQuery(queryParams);

		assertThat(hintDaos).containsExactly(hintDaoForUser);
	}

	@Test
	void findNoneByWrongQuery() {
		final HintDao hintDao = new HintDao("12345", "ELISA", "Test", HintDto.Category.INFO, true, "E12345", LocalDateTime.of(2023, 5, 22, 8, 0), "1", "resourceId-1");
		mongoTemplate.insert(hintDao);
		Map<HintParams, Object> queryParams = Map.of(HintParams.HINT_SOURCE, "SI");

		List<HintDao> hintDaos = hintRepositoryCustomAdapter.findAllByQuery(queryParams);

		assertThat(hintDaos).isEmpty();
	}

	@Test
	void findAllByHintSourcePrefix() {
		final HintDao matching = new HintDao("12345", "ELISA", "Test", HintDto.Category.INFO, true,
			"E12345", LocalDateTime.of(2023, 5, 22, 8, 0), "1", "resourceId-1");

		final HintDao notMatching = new HintDao("54321", "SIGNA", "Other", HintDto.Category.INFO, true,
			"E54321", LocalDateTime.of(2023, 5, 22, 8, 0), "1", "resourceId-2");

		mongoTemplate.insert(matching);
		mongoTemplate.insert(notMatching);

		Map<HintParams, Object> queryParams = Map.of(HintParams.HINT_SOURCE_PREFIX, "EL");

		List<HintDao> result = hintRepositoryCustomAdapter.findAllByQuery(queryParams);

		assertThat(result).containsExactly(matching);
	}

	@Test
	void findNoneByHintSourcePrefix() {
		final HintDao hintDao = new HintDao("12345", "ELISA", "Test", HintDto.Category.INFO, true,
			"E12345", LocalDateTime.of(2023, 5, 22, 8, 0), "1", "resourceId-1");

		mongoTemplate.insert(hintDao);

		Map<HintParams, Object> queryParams = Map.of(HintParams.HINT_SOURCE_PREFIX, "YX");

		List<HintDao> result = hintRepositoryCustomAdapter.findAllByQuery(queryParams);

		assertThat(result).isEmpty();
	}

	private HintParams convertString2HintParams(String key) {
		return switch (key) {
			case "id" -> HintParams.ID;
			case "hintSource" -> HintParams.HINT_SOURCE;
			case "hintCategory" -> HintParams.HINT_CATEGORY;
			case "hintSourcePrefix" -> HintParams.HINT_SOURCE_PREFIX;
			case "hintTextOriginal" -> HintParams.HINT_TEXT_ORIGINAL;
			case "showToUser" -> HintParams.SHOW_TO_USER;
			case "processId" -> HintParams.PROCESS_ID;
			case "processVersion" -> HintParams.PROCESS_VERSION;
			case "resourceId" -> HintParams.RESOURCE_ID;
			default -> HintParams.UNKNOWN;
		};
	}

	static class WrongQueryArgumentProvider implements ArgumentsProvider {
		@Override
		public Stream<? extends Arguments> provideArguments(ExtensionContext extensionContext) {
			final HintDao hintDao = new HintDao("12345", "ELISA", "Test", HintDto.Category.INFO, true, "E12345", LocalDateTime.of(2023, 5, 22, 8, 0), "1", "resourceId-1");
			return Stream.of(
				Arguments.of(Map.of(HintParams.ID, "23456", HintParams.HINT_SOURCE, "ELISA", HintParams.HINT_TEXT_ORIGINAL, "Test", HintParams.HINT_CATEGORY, "INFO", HintParams.PROCESS_ID, "E12345", HintParams.SHOW_TO_USER, true, HintParams.PROCESS_VERSION, "1", HintParams.RESOURCE_ID, "resourceId-1"), hintDao),
				Arguments.of(Map.of(HintParams.ID, "12345", HintParams.HINT_SOURCE, "ELISA!", HintParams.HINT_TEXT_ORIGINAL, "Test", HintParams.HINT_CATEGORY, "INFO", HintParams.PROCESS_ID, "E12345", HintParams.SHOW_TO_USER, true, HintParams.PROCESS_VERSION, "1", HintParams.RESOURCE_ID, "resourceId-1"), hintDao),
				Arguments.of(Map.of(HintParams.ID, "12345", HintParams.HINT_SOURCE, "ELISA", HintParams.HINT_TEXT_ORIGINAL, "Test!", HintParams.HINT_CATEGORY, "INFO", HintParams.PROCESS_ID, "E12345", HintParams.SHOW_TO_USER, true, HintParams.PROCESS_VERSION, "1", HintParams.RESOURCE_ID, "resourceId-1"), hintDao),
				Arguments.of(Map.of(HintParams.ID, "12345", HintParams.HINT_SOURCE, "ELISA", HintParams.HINT_TEXT_ORIGINAL, "Test", HintParams.HINT_CATEGORY, "INFO!", HintParams.PROCESS_ID, "E12345", HintParams.SHOW_TO_USER, true, HintParams.PROCESS_VERSION, "1", HintParams.RESOURCE_ID, "resourceId-1"), hintDao),
				Arguments.of(Map.of(HintParams.ID, "12345", HintParams.HINT_SOURCE, "ELISA", HintParams.HINT_TEXT_ORIGINAL, "Test", HintParams.HINT_CATEGORY, "INFO", HintParams.PROCESS_ID, "E12345!", HintParams.SHOW_TO_USER, true, HintParams.PROCESS_VERSION, "1", HintParams.RESOURCE_ID, "resourceId-1"), hintDao),
				Arguments.of(Map.of(HintParams.ID, "12345", HintParams.HINT_SOURCE, "ELISA", HintParams.HINT_TEXT_ORIGINAL, "Test", HintParams.HINT_CATEGORY, "INFO", HintParams.PROCESS_ID, "E12345", HintParams.SHOW_TO_USER, false, HintParams.PROCESS_VERSION, "1", HintParams.RESOURCE_ID, "resourceId-1"), hintDao),
				Arguments.of(Map.of(HintParams.ID, "12345", HintParams.HINT_SOURCE, "ELISA", HintParams.HINT_TEXT_ORIGINAL, "Test", HintParams.HINT_CATEGORY, "INFO", HintParams.PROCESS_ID, "E12345", HintParams.SHOW_TO_USER, true, HintParams.PROCESS_VERSION, "2", HintParams.RESOURCE_ID, "resourceId-1"), hintDao)
			);
		}
	}
}
