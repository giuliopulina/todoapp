package com.giuliopulina.todo;

import org.aspectj.lang.annotation.Before;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.annotation.BeforeTestClass;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import static org.testcontainers.containers.localstack.LocalStackContainer.Service.SQS;

@SpringBootTest
@ActiveProfiles("local")
class TodoApplicationTests {

	@Test
	void contextLoads() {
	}

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", () -> database.getJdbcUrl());
		registry.add("spring.security.oauth2.client.provider.cognito.issuerUri", () -> "http://localhost:" + keycloak.getMappedPort(8080) + "/auth/realms/stratospheric");
		//registry.add("spring.cloud.aws.endpoint", () -> localStack.getEndpointOverride(SQS).toString());
		//registry.add("custom.web-socket-relay-endpoint", () -> "localhost:" + activeMq.getMappedPort(61613));
	}

	static PostgreSQLContainer<?> database = new PostgreSQLContainer<>("postgres:13.3")
			.withDatabaseName("stratospheric")
			.withUsername("stratospheric")
			.withPassword("stratospheric");

	static GenericContainer keycloak = new GenericContainer(DockerImageName.parse("quay.io/keycloak/keycloak:18.0.0-legacy"))
			.withExposedPorts(8080)
			.withClasspathResourceMapping("/keycloak", "/tmp", BindMode.READ_ONLY)
			.withEnv("JAVA_OPTS", "-Dkeycloak.migration.action=import -Dkeycloak.migration.provider=singleFile -Dkeycloak.migration.file=/tmp/stratospheric-realm.json")
			.withEnv("DB_VENDOR", "H2")
			.withEnv("KEYCLOAK_USER", "keycloak")
			.withEnv("KEYCLOAK_PASSWORD", "keycloak")
			.waitingFor(Wait.forHttp("/auth").forStatusCode(200));

	static {
		database.start();
		//localStack.start();
		keycloak.start();
		//activeMq.start();
	}
}
