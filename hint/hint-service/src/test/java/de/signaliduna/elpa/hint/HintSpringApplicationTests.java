package de.signaliduna.elpa.hint;

import de.signaliduna.elpa.hint.util.ContainerImageNames;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(
	properties = {
		"spring.jpa.hibernate.ddl-auto=create-drop",
	}
)
@Import(TestChannelBinderConfiguration.class)
class HintSpringApplicationTests{

	@Container
	@ServiceConnection
	static final PostgreSQLContainer POSTGRES_CONTAINER = new PostgreSQLContainer(
		DockerImageName.parse(ContainerImageNames.POSTGRES.getImageName()).asCompatibleSubstituteFor(PostgreSQLContainer.IMAGE)
	).withInitScript("db/init-hint-schema.sql");

	@Test
	void contextLoads(ApplicationContext context) {
		assertThat(context).isNotNull();
	}
}
