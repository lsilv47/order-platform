package com.orderplatform.notification;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

    @Container
    static final RabbitMQContainer rabbit =
            new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    @Container
    static final GenericContainer<?> mailhog =
            new GenericContainer<>("mailhog/mailhog:latest")
                    .withExposedPorts(1025, 8025);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", rabbit::getHost);
        registry.add("spring.rabbitmq.port", rabbit::getAmqpPort);
        registry.add("spring.mail.host", mailhog::getHost);
        registry.add("spring.mail.port", () -> mailhog.getMappedPort(1025));
    }

    protected static String mailhogApiUrl() {
        return "http://" + mailhog.getHost() + ":" + mailhog.getMappedPort(8025);
    }
}
