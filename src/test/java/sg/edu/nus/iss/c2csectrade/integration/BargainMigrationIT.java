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

/** 005 runs on every deployment: it must create the bargain tables on a Sprint 3 database and then be a no-op. */
class BargainMigrationIT extends MySqlIntegrationTest {
    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void upgradesAnExistingDatabaseAndCanRunAgainWithoutLosingData() {
        try {
            // Simulate the schema before the bargain module.
            jdbc.execute("DROP TABLE bargain_offer");
            jdbc.execute("DROP TABLE bargain_campaign_helper");
            jdbc.execute("DROP TABLE bargain_campaign");

            var migration = new ResourceDatabasePopulator(new FileSystemResource("scripts/migrations/005_bargain.sql"));
            migration.execute(dataSource);
            jdbc.update("INSERT INTO bargain_campaign(product_id, buyer_id, start_price, current_price, floor_price, deadline) "
                    + "VALUES (1, 102, 100, 100, 80, DATE_ADD(NOW(), INTERVAL 1 DAY))");
            migration.execute(dataSource);

            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM bargain_campaign", Integer.class));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM bargain_offer", Integer.class));
            assertEquals(12, jdbc.queryForObject("SELECT COUNT(*) FROM pms_product", Integer.class));
        } finally {
            jdbc.update("DELETE FROM bargain_campaign WHERE buyer_id=102");
        }
    }
}
