package sg.edu.nus.iss.c2csectrade.service.recommendation;

import org.junit.jupiter.api.Test;
import sg.edu.nus.iss.c2csectrade.entity.Product;
import sg.edu.nus.iss.c2csectrade.mapper.ProductMapper;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecommendationStrategyTest {
    private Product product(long id, int status, int stock, int reserved) {
        Product p = new Product(); p.setId(id); p.setStatus(status); p.setStock(stock);
        p.setReservedStock(reserved); p.setCategory("books"); return p;
    }
    @Test void recentFiltersCurrentDelistedAndReservedListingsAndRespectsLimit() {
        var mapper = mock(ProductMapper.class);
        var current = product(1, 1, 1, 0);
        when(mapper.selectRecentProducts(3)).thenReturn(List.of(current, product(2, 0, 2, 0),
                product(3, 1, 2, 2), product(4, 1, 2, 1), product(5, 1, 1, 0)));
        assertEquals(List.of(4L), new MostRecentStrategy(mapper).recommend(current, 1).stream().map(Product::getId).toList());
    }
    @Test void categoryUsesOnlyItsCategoryAndDoesNotQueryWhenMissing() {
        var mapper = mock(ProductMapper.class);
        var current = product(1, 1, 1, 0);
        when(mapper.selectByCategory("books")).thenReturn(List.of(current, product(2, 1, 1, 0), product(3, 0, 1, 0)));
        var strategy = new SameCategoryStrategy(mapper);
        assertEquals(List.of(2L), strategy.recommend(current, 8).stream().map(Product::getId).toList());
        current.setCategory(null);
        assertTrue(strategy.recommend(current, 8).isEmpty());
        verify(mapper, times(1)).selectByCategory(any());
    }
    @Test void nonpositiveLimitsNeverQuery() {
        var mapper = mock(ProductMapper.class);
        var current = product(1, 1, 1, 0);
        assertTrue(new SameCategoryStrategy(mapper).recommend(current, 0).isEmpty());
        assertTrue(new MostRecentStrategy(mapper).recommend(current, -1).isEmpty());
        verifyNoInteractions(mapper);
    }
}
