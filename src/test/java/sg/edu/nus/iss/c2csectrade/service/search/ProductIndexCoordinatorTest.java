package sg.edu.nus.iss.c2csectrade.service.search;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sg.edu.nus.iss.c2csectrade.entity.Product;
import sg.edu.nus.iss.c2csectrade.mapper.ProductMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductIndexCoordinatorTest {

    @Mock private ProductMapper productMapper;
    @Mock private ProductSearchIndex searchIndex;

    private ProductIndexCoordinator coordinator;

    @BeforeEach
    void setUp() {
        coordinator = new ProductIndexCoordinator(productMapper, searchIndex);
    }

    @Test
    void rebuildsExistingListingsAtStartup() {
        Product product = searchableProduct(1L);
        when(productMapper.selectAllActive()).thenReturn(List.of(product));

        coordinator.rebuildAfterStartup();

        verify(searchIndex).rebuild(List.of(product));
    }

    @Test
    void upsertsTheCommittedProduct() {
        Product product = searchableProduct(2L);
        when(productMapper.selectById(2L)).thenReturn(product);

        coordinator.apply(ProductIndexEvent.upsert(2L));

        verify(searchIndex).upsert(product);
    }

    @Test
    void removesDelistedOrMissingProducts() {
        coordinator.apply(ProductIndexEvent.delete(3L));
        verify(searchIndex).delete(3L);

        when(productMapper.selectById(4L)).thenReturn(null);
        coordinator.apply(ProductIndexEvent.upsert(4L));
        verify(searchIndex).delete(4L);
    }

    @Test
    void startupIndexFailureDoesNotStopTheApplication() {
        when(productMapper.selectAllActive()).thenReturn(List.of(searchableProduct(5L)));
        doThrow(new IllegalStateException("offline")).when(searchIndex).rebuild(org.mockito.ArgumentMatchers.any());

        assertDoesNotThrow(coordinator::rebuildAfterStartup);
    }

    private Product searchableProduct(Long id) {
        Product product = new Product();
        product.setId(id);
        product.setStatus(1);
        product.setStock(1);
        product.setReservedStock(0);
        return product;
    }
}
