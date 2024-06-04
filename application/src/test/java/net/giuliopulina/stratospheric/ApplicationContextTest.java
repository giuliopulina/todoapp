package net.giuliopulina.stratospheric;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.EnabledIf;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.containers.DockerComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.File;
import java.time.Duration;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@EnabledIf(expression = "#{systemProperties['integrationTest'] != null}", loadContext = false)
//profile that will be set to the SpringBoot application that is going to be integration tested
@ActiveProfiles("local")
public class ApplicationContextTest {

    @Container
    public static DockerComposeContainer<?> dockerComposeEnvironment =
            new DockerComposeContainer<>(new File("docker-compose.yml"))
                    .withExposedService("postgres_1", 5432, Wait.forListeningPort())
                    .withExposedService("localstack_1", 4566)
                    .withExposedService("keycloak_1", 8080,
                            Wait.forHttp("/").forStatusCode(200)
                                    .withStartupTimeout(Duration.ofSeconds(45)))
                    .withOptions("--compatibility")
                    .withLocalCompose(true);

    static {
        dockerComposeEnvironment.start();
    }

    @Autowired
    private ApplicationContext context;

    @Test
    public void contextLoads() {
        assertThat(context).isNotNull();
    }

}