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

/** 004 runs on every deployment, so it must upgrade a Sprint 3 database and then be a no-op. */
class ModerationMigrationIT extends MySqlIntegrationTest {
    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void upgradesAnExistingDatabaseAndCanRunAgainWithoutLosingData() {
        try {
            // Simulate the schema before moderation.
            jdbc.execute("DROP TABLE admin_audit_log");
            jdbc.execute("DROP TABLE report");
            jdbc.execute("ALTER TABLE users DROP COLUMN status, DROP COLUMN ban_reason, DROP COLUMN banned_at");

            var migration = new ResourceDatabasePopulator(new FileSystemResource("scripts/migrations/004_moderation.sql"));
            migration.execute(dataSource);
            assertEquals("ACTIVE", jdbc.queryForObject("SELECT status FROM users WHERE id=101", String.class));

            jdbc.update("UPDATE users SET status='BANNED', ban_reason='kept' WHERE id=102");
            jdbc.update("INSERT INTO report(reporter_id, target_type, target_id, reason) VALUES (101, 'USER', 102, 'SPAM')");
            migration.execute(dataSource);

            assertEquals("kept", jdbc.queryForObject("SELECT ban_reason FROM users WHERE id=102", String.class));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM report WHERE target_id=102", Integer.class));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit_log", Integer.class));
        } finally {
            jdbc.update("DELETE FROM report WHERE target_id=102");
            jdbc.update("UPDATE users SET status='ACTIVE', ban_reason=NULL, banned_at=NULL WHERE id=102");
        }
    }
}
