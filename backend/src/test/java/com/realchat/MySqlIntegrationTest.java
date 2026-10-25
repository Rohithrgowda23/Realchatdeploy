package com.realchat;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.lang.annotation.*;

/**
 * Marks a test that runs the whole application against a REAL MySQL database. It only runs when TEST_DB_URL points at a
 * database whose name contains "realchat_test" (so it can never touch realchat_db); otherwise JUnit reports it as SKIPPED.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".*realchat_test.*")
@TestPropertySource(properties = {
        "spring.datasource.url=${TEST_DB_URL}",
        "spring.datasource.username=${TEST_DB_USERNAME:root}",
        "spring.datasource.password=${TEST_DB_PASSWORD:}",
        "app.jwt.secret=integration-test-secret-integration-test-1234",
        "app.jwt.expiration-minutes=60",
        "app.rate-limit.enabled=false",
        "app.scheduler.enabled=false",
        "app.mail.mode=console",
        "app.storage.dir=${java.io.tmpdir}/realchat-it-uploads"
})
public @interface MySqlIntegrationTest {}