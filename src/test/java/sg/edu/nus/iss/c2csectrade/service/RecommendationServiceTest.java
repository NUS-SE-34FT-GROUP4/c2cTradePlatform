package sg.edu.nus.iss.c2csectrade.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sg.edu.nus.iss.c2csectrade.entity.Product;
import sg.edu.nus.iss.c2csectrade.mapper.ProductMapper;
import sg.edu.nus.iss.c2csectrade.mapper.ViewHistoryMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Sprint 2 acceptance checks for the related-items rules. */
@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    @Mock private ProductMapper productMapper;
    @Mock private ViewHistoryMapper viewHistoryMapper;
    @InjectMocks private RecommendationService recommendationService;

    private Product product(Long id, int status, int stock, int reserved) {
        Product product = new Product();
        product.setId(id);
        product.setCategory("books");
        product.setStatus(status);
        product.setStock(stock);
        product.setReservedStock(reserved);
        return product;
    }

    @Test
    @DisplayName("The item being viewed never appears among its own related items")
    void excludesTheCurrentProduct() {
        when(productMapper.selectById(1L)).thenReturn(product(1L, 1, 5, 0));
        when(productMapper.selectByCategory("books"))
                .thenReturn(List.of(product(1L, 1, 5, 0), product(2L, 1, 5, 0)));

        List<Product> result = recommendationService.similarTo(1L, 8);

        assertEquals(1, result.size());
        assertEquals(2L, result.get(0).getId());
    }

    @Test
    @DisplayName("Delisted items and items with nothing available are filtered out")
    void excludesUnbuyableProducts() {
        when(productMapper.selectById(1L)).thenReturn(product(1L, 1, 5, 0));
        when(productMapper.selectByCategory("books")).thenReturn(List.of(
                product(2L, 0, 5, 0),   // delisted
                product(3L, 1, 2, 2),   // every unit held by unpaid orders
                product(4L, 1, 5, 1))); // buyable

        List<Product> result = recommendationService.similarTo(1L, 8);

        assertEquals(1, result.size());
        assertEquals(4L, result.get(0).getId());
    }

    @Test
    @DisplayName("An empty category falls back to recent listings rather than an empty strip")
    void fallsBackWhenCategoryIsExhausted() {
        when(productMapper.selectById(1L)).thenReturn(product(1L, 1, 5, 0));
        when(productMapper.selectByCategory("books")).thenReturn(List.of(product(1L, 1, 5, 0)));
        when(productMapper.selectRecentProducts(anyInt())).thenReturn(List.of(product(9L, 1, 5, 0)));

        List<Product> result = recommendationService.similarTo(1L, 8);

        assertEquals(1, result.size());
        assertEquals(9L, result.get(0).getId());
    }

    @Test
    @DisplayName("A missing product yields no recommendations instead of an error")
    void unknownProductIsEmpty() {
        when(productMapper.selectById(99L)).thenReturn(null);
        assertTrue(recommendationService.similarTo(99L, 8).isEmpty());
    }

    @Test
    @DisplayName("A signed-out visitor gets recent listings")
    void anonymousGetsRecent() {
        when(productMapper.selectRecentProducts(anyInt())).thenReturn(List.of(product(5L, 1, 3, 0)));

        List<Product> result = recommendationService.forUser(null, 8);

        assertEquals(1, result.size());
        verifyNoInteractions(viewHistoryMapper);
    }

    @Test
    @DisplayName("Suggestions follow the categories the user has browsed, without duplicates")
    void usesBrowsedCategories() {
        when(viewHistoryMapper.recentCategories(7L, 3)).thenReturn(List.of("books", "electronics"));
        when(productMapper.selectByCategory("books")).thenReturn(List.of(product(2L, 1, 5, 0)));
        when(productMapper.selectByCategory("electronics")).thenReturn(List.of(product(2L, 1, 5, 0), product(3L, 1, 5, 0)));
        when(productMapper.selectRecentProducts(anyInt())).thenReturn(List.of());

        List<Product> result = recommendationService.forUser(7L, 8);

        assertEquals(List.of(2L, 3L), result.stream().map(Product::getId).toList());
    }
}
