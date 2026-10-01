package sg.edu.nus.iss.c2csectrade.integration;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import sg.edu.nus.iss.c2csectrade.service.FileStorageService;

import java.security.SecureRandom;
import java.util.Base64;

/** Real Spring context and production schema; only external file storage is stubbed. */
@SpringBootTest(properties = "captcha.store=memory")
@AutoConfigureMockMvc
@Transactional
public abstract class MySqlIntegrationTest {
    // One container for the cached Spring context, not one restarted per subclass.
    // Ryuk removes it when the test JVM exits. No Docker means a failure, not a skipped test.
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("trade")
            .withUsername("integration")
            .withPassword("integration")
            .withInitScript("init.sql");
    static {
        MYSQL.start();
    }

    private static final String JWT_KEY;
    static {
        byte[] bytes = new byte[64];
        new SecureRandom().nextBytes(bytes);
        JWT_KEY = Base64.getEncoder().encodeToString(bytes);
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("app.jwtSecret", () -> JWT_KEY);
        registry.add("jwt.secret", () -> JWT_KEY);
    }

    @MockBean
    protected FileStorageService fileStorageService;
}
