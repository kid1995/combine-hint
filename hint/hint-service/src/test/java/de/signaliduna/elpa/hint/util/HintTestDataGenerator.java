package de.signaliduna.elpa.hint.util;

import de.signaliduna.elpa.hint.adapter.database.legacy.model.HintDao;
import de.signaliduna.elpa.hint.adapter.database.model.HintEntity;
import de.signaliduna.elpa.hint.adapter.mapper.HintMapper;
import de.signaliduna.elpa.hint.model.HintDto;
import org.bson.Document;
import org.mapstruct.factory.Mappers;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

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

	public static HintEntity createHintEntityWithMongoId(String mongoUUID){
		HintEntity hintEntity = createInfoHintEntity();
		hintEntity.setMongoUUID(mongoUUID);
		return hintEntity;
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

	public static HintDao createWarningHintDao() {
		return hintMapper.dtoToDao(createWarningHintDto());
	}

	public static HintDao createErrorHintDao() {
		return hintMapper.dtoToDao(createErrorHintDto());
	}

	public static HintDao createBlockerHintDao() {
		return hintMapper.dtoToDao(createBlockerHintDto());
	}

	public static HintDao createHintDaoWithId(String id) {
		HintDto dto = createBlockerHintDto();
		HintDao dao = hintMapper.dtoToDao(dto);
		return new HintDao(id, dao.hintSource(), dao.hintTextOriginal(), dao.hintCategory(), dao.showToUser(),
			dao.processId(), dao.creationDate(), dao.processVersion(), dao.resourceId());
	}

	public static List<HintDao> createMultipleHintDao(int numberOfHintDao){
		List<HintDao> hintDaos = new ArrayList<>(numberOfHintDao);
		while (hintDaos.size() < numberOfHintDao){
			hintDaos.add(HintTestDataGenerator.createHintDaoWithId(generateMongoId()));
		}
		return hintDaos;
	}

	public static List<HintEntity> createAllCategoryHintEntitys() {
		return Arrays.asList(
			createInfoHintEntity(),
			createWarningHintEntity(),
			createErrorHintEntity(),
			createBlockerHintEntity()
		);
	}

	public static Document createDocumentFromHintDao(HintDao hintDao) {
		Document doc = new Document();
		doc.append("_id", hintDao.id()); // @Id field maps to _id in MongoDB
		doc.append("hintSource", hintDao.hintSource());
		doc.append("hintTextOriginal", hintDao.hintTextOriginal());
		if (hintDao.hintCategory() != null) {
			doc.append("hintCategory", hintDao.hintCategory().name());
		}
		doc.append("showToUser", hintDao.showToUser());
		doc.append("processId", hintDao.processId());
		doc.append("creationDate", hintDao.creationDate());
		// Handle optional fields
		if (hintDao.processVersion() != null) {
			doc.append("processVersion", hintDao.processVersion());
		}
		if (hintDao.resourceId() != null) {
			doc.append("resourceId", hintDao.resourceId());
		}
		return doc;
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

	private static String generateMongoId() {
		// Generate a realistic MongoDB ObjectId (24 hex characters)
		StringBuilder sb = new StringBuilder();
		String hexChars = "0123456789abcdef";
		for (int i = 0; i < 24; i++) {
			sb.append(hexChars.charAt(random.nextInt(hexChars.length())));
		}
		return sb.toString();
	}
}


