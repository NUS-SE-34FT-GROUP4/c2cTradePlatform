package sg.edu.nus.iss.c2csectrade.service.search;

import org.junit.jupiter.api.Test;
import sg.edu.nus.iss.c2csectrade.entity.Product;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProductSearchDocumentTest {

    @Test
    void mapsCatalogueFieldsAndAvailableStock() {
        Product product = new Product();
        product.setId(12L);
        product.setUserId(7L);
        product.setName("Distributed Systems Textbook");
        product.setDescription("Clean copy with notes");
        product.setPrice(new BigDecimal("42.50"));
        product.setConditionLevel(8);
        product.setLocation("Kent Ridge");
        product.setCategory("books");
        product.setStock(5);
        product.setReservedStock(2);
        product.setStatus(1);
        product.setCreatedAt(LocalDateTime.of(2026, 9, 30, 9, 0));

        ProductSearchDocument document = ProductSearchDocument.from(product);

        assertEquals(12L, document.getId());
        assertEquals(7L, document.getSellerId());
        assertEquals("Distributed Systems Textbook", document.getName());
        assertEquals(42.50, document.getPrice());
        assertEquals(3, document.getAvailableStock());
        assertEquals("2026-09-30T09:00", document.getCreatedAt());
    }
}
