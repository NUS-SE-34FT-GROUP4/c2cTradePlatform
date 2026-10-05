package sg.edu.nus.iss.c2csectrade.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import sg.edu.nus.iss.c2csectrade.service.OrderService;

import javax.sql.DataSource;
import static org.junit.jupiter.api.Assertions.*;

class ReviewMigrationIT extends MySqlIntegrationTest {
    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;
    @Autowired OrderService orders;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void upgradesAnExistingDatabaseAndCanRunAgainWithoutLosingData() {
        Long id = orders.checkoutDirect(1L, 1L, 1).getId();
        try {
            jdbc.execute("DROP TABLE review"); // Simulate the pre-review schema.
            var migration = new ResourceDatabasePopulator(new FileSystemResource("scripts/migrations/003_reviews.sql"));
            migration.execute(dataSource);
            jdbc.update("INSERT INTO review(order_id,product_id,buyer_id,seller_id,product_rating,seller_rating) VALUES (?,1,1,101,4,5)", id);
            migration.execute(dataSource);
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM review WHERE order_id=?", Integer.class, id));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM oms_order WHERE id=?", Integer.class, id));
            assertEquals(12, jdbc.queryForObject("SELECT COUNT(*) FROM pms_product", Integer.class));
        } finally {
            jdbc.update("DELETE FROM review WHERE order_id=?", id);
            jdbc.update("DELETE FROM oms_order_item WHERE order_id=?", id);
            jdbc.update("DELETE FROM oms_order WHERE id=?", id);
            jdbc.update("UPDATE pms_product SET reserved_stock=0 WHERE id=1");
        }
    }
}
