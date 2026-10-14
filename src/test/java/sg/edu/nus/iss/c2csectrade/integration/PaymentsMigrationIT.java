package sg.edu.nus.iss.c2csectrade.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 002 brings a database created before Sprint 3 (staging) up to the payment
 * schema, so that 006 and the application find the ledger, and then is a no-op.
 */
class PaymentsMigrationIT extends MySqlIntegrationTest {
    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void upgradesAPreSprint3DatabaseSoTheLaterMigrationsRunAndCanRunAgain() {
        // Simulate staging: no ledger, no notifications, no payment method column,
        // no seeded payment password.
        jdbc.execute("DROP TABLE oms_transaction");
        jdbc.execute("DROP TABLE system_notification");
        jdbc.execute("ALTER TABLE oms_order DROP COLUMN payment_method");
        jdbc.update("UPDATE users SET payment_password_hash = NULL WHERE id = 101");
        jdbc.update("UPDATE users SET payment_password_hash = 'kept' WHERE id = 102");
        try {
            for (String file : new String[] {"002_payments.sql", "006_escrow.sql", "002_payments.sql"}) {
                new ResourceDatabasePopulator(new FileSystemResource("scripts/migrations/" + file)).execute(dataSource);
            }
            assertEquals(1, count("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() "
                    + "AND TABLE_NAME = 'oms_order' AND COLUMN_NAME = 'payment_method'"));
            assertEquals(0, count("SELECT COUNT(*) FROM oms_transaction"));
            assertEquals(0, count("SELECT COUNT(*) FROM system_notification"));
            assertEquals(1, count("SELECT COUNT(*) FROM users WHERE id = 101 AND payment_password_hash LIKE '$2a$%'"));
            assertEquals("kept", jdbc.queryForObject("SELECT payment_password_hash FROM users WHERE id = 102", String.class));
        } finally {
            jdbc.update("UPDATE users SET payment_password_hash = "
                    + "(SELECT h FROM (SELECT payment_password_hash h FROM users WHERE id = 103) t) WHERE id = 102");
        }
    }
}
