package de.signaliduna.elpa.hint.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Abstract base class for integration tests using Testcontainers that should share the same container instances
 * (See
 * <a href="https://testcontainers.com/guides/testcontainers-container-lifecycle/#_using_singleton_containers">
 *   https://testcontainers.com
 * </a>).
 */
public abstract class AbstractSingletonContainerTest {
	protected static final PostgreSQLContainer<?> POSTGRES_CONTAINER = new PostgreSQLContainer<>(
		DockerImageName.parse(ContainerImageNames.POSTGRES.getImageName()).asCompatibleSubstituteFor(PostgreSQLContainer.IMAGE)
	);
	protected static final MongoDBContainer MONGO_DB_CONTAINER = new MongoDBContainer
		(DockerImageName.parse(ContainerImageNames.MONGO.getImageName()).asCompatibleSubstituteFor("mongo"));

	private static final Logger log = LoggerFactory.getLogger(AbstractSingletonContainerTest.class);

	static {
		POSTGRES_CONTAINER.start();
		MONGO_DB_CONTAINER.start();
	}

	@DynamicPropertySource
	static void configureProperties(DynamicPropertyRegistry registry) {
		String jdbcUrl = POSTGRES_CONTAINER.getJdbcUrl();
		log.info("using 'spring.datasource.url' {}", jdbcUrl);
		registry.add("spring.datasource.url", () -> jdbcUrl);
		registry.add("spring.datasource.username", POSTGRES_CONTAINER::getUsername);
		registry.add("spring.datasource.password", POSTGRES_CONTAINER::getPassword);
		registry.add("spring.data.mongodb.uri", MONGO_DB_CONTAINER::getReplicaSetUrl);
	}
}
