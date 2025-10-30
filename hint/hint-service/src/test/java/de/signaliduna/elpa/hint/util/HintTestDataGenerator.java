package de.signaliduna.elpa.hint.util;

import de.signaliduna.elpa.hint.adapter.database.model.HintEntity;
import de.signaliduna.elpa.hint.adapter.mapper.HintMapper;
import de.signaliduna.elpa.hint.model.HintDto;
import org.mapstruct.factory.Mappers;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

public class HintTestDataGenerator {

	private static final Random random = new Random();
	private static final HintMapper hintMapper = Mappers.getMapper(HintMapper.class);

	private static final List<String> INFO_MESSAGES = Arrays.asList(
		"Kein Partner zu VNR 56330402 im ODS gefunden.",
		"PAID 'C000001243352665' zu PANR '590227672' zugeordnet",
		"Aktualisierung der Daten zur natürlichen Person im Partnersystem angefordert."
	);

	private static final List<String> WARNING_MESSAGES = Arrays.asList(
		"PLZ abweichend.",
		"Ort abweichend.",
		"Die Antrags-Adresse stimmt nicht mit der Adresse im Partnersystem überein."
	);

	private static final List<String> ERROR_MESSAGES = Arrays.asList(
		"Validation error occurred",
		"Field validation failed",
		"Data processing error"
	);

	private static final List<String> BLOCKER_MESSAGES = Arrays.asList(
		"Geburtsdatum muss gesetzt sein. VNR oder PANR konnte nicht geprüft werden.",
		"Ort kann nicht leer sein, wenn die PANR oder VNR nicht gesetzt sind!",
		"PLZ kann nicht leer sein, wenn die PANR oder VNR nicht gesetzt sind!"
	);

	private static final List<String> PASYNC_HINT_SOURCES = Arrays.asList(
		"PASYNC-Identifizierung",
		"PASYNC-Aktualisierung",
		"PASYNC-Aktualisierung asynchron"
	);

	private static final List<String> WARNING_HINT_SOURCES = Arrays.asList(
		"PASYNC-Adressabgleich",
		"PASYNC-VNR-Vergabe"
	);

	private static final List<String> ERROR_HINT_SOURCES = Arrays.asList(
		"Validierung",
		"Prozess-Engine"
	);

	private static final List<String> BLOCKER_HINT_SOURCES = List.of(
		"PASYNC-Validations"
	);

	private HintTestDataGenerator() {
	}

	public static HintDto createInfoHintDto() {
		return HintDto.builder()
			.hintSource(getRandomElement(PASYNC_HINT_SOURCES))
			.message(getRandomElement(INFO_MESSAGES))
			.hintCategory(HintDto.Category.INFO)
			.showToUser(random.nextBoolean())
			.processId(generateRandomProcessId())
			.processVersion("1")
			.resourceId(generateRandomResourceId())
			.creationDate(generateRandomCreationDate())
			.build();
	}

	public static HintDto createWarningHintDto() {
		return HintDto.builder()
			.hintSource(getRandomElement(WARNING_HINT_SOURCES))
			.message(getRandomElement(WARNING_MESSAGES))
			.hintCategory(HintDto.Category.WARNING)
			.showToUser(random.nextBoolean())
			.processId(generateRandomProcessId())
			.processVersion("1")
			.resourceId(generateRandomResourceId())
			.creationDate(generateRandomCreationDate())
			.build();
	}

	public static HintDto createErrorHintDto() {
		return HintDto.builder()
			.hintSource(getRandomElement(ERROR_HINT_SOURCES))
			.message(getRandomElement(ERROR_MESSAGES))
			.hintCategory(HintDto.Category.ERROR)
			.showToUser(random.nextBoolean())
			.processId(generateRandomProcessId())
			.processVersion("1")
			.resourceId(generateRandomResourceId())
			.creationDate(generateRandomCreationDate())
			.build();
	}

	public static HintDto createBlockerHintDto() {
		return HintDto.builder()
			.hintSource(getRandomElement(BLOCKER_HINT_SOURCES))
			.message(getRandomElement(BLOCKER_MESSAGES))
			.hintCategory(HintDto.Category.BLOCKER)
			.showToUser(random.nextBoolean())
			.processId(generateRandomProcessId())
			.processVersion("1")
			.resourceId(generateRandomResourceId())
			.creationDate(generateRandomCreationDate())
			.build();
	}

	public static HintEntity createInfoHintEntity() {
		return hintMapper.dtoToEntity(createInfoHintDto());
	}

	public static HintEntity createWarningHintEntity() {
		return hintMapper.dtoToEntity(createWarningHintDto());
	}

	public static HintEntity createErrorHintEntity() {
		return hintMapper.dtoToEntity(createErrorHintDto());
	}

	public static HintEntity createBlockerHintEntity() {
		return hintMapper.dtoToEntity(createBlockerHintDto());
	}

	public static List<HintEntity> createAllCategoryHintEntitys() {
		return Arrays.asList(
			createInfoHintEntity(),
			createWarningHintEntity(),
			createErrorHintEntity(),
			createBlockerHintEntity()
		);
	}

	private static <T> T getRandomElement(List<T> list) {
		return list.get(random.nextInt(list.size()));
	}

	private static String generateRandomProcessId() {
		return "test-processId-" + (random.nextInt(900) + 100);
	}

	private static String generateRandomResourceId() {
		return "test-resourceId-" + (random.nextInt(900) + 100);
	}

	private static LocalDateTime generateRandomCreationDate() {
		return LocalDateTime.now().minusDays(random.nextInt(6));
	}

	public static List<HintEntity> creatHintEntityWithSameProcessId(String s) {
		return createAllCategoryHintEntitys().stream().peek(
			hintEntity -> hintEntity.setProcessId(s)
		).toList();
	}
}


