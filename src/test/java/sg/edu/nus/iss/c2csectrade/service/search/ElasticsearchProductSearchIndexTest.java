package sg.edu.nus.iss.c2csectrade.service.search;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import sg.edu.nus.iss.c2csectrade.entity.Product;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ElasticsearchProductSearchIndexTest {

    @Mock private ElasticsearchOperations operations;
    @Mock private IndexOperations indexOperations;

    private ElasticsearchProductSearchIndex searchIndex;

    @BeforeEach
    void setUp() {
        searchIndex = new ElasticsearchProductSearchIndex(operations);
        when(operations.indexOps(ProductSearchDocument.class)).thenReturn(indexOperations);
    }

    @Test
    void createsTheIndexMappingBeforeSavingAListing() {
        when(indexOperations.exists()).thenReturn(false, true);
        when(indexOperations.createWithMapping()).thenReturn(true);

        searchIndex.upsert(searchableProduct(10L));

        verify(indexOperations).createWithMapping();
        verify(operations).save(any(ProductSearchDocument.class));
    }

    @Test
    void rebuildRecreatesTheIndexSoStaleDocumentsAreRemoved() {
        when(indexOperations.exists()).thenReturn(true);
        when(indexOperations.createWithMapping()).thenReturn(true);

        searchIndex.rebuild(java.util.List.of(searchableProduct(14L)));

        verify(indexOperations).delete();
        verify(indexOperations).createWithMapping();
        verify(operations, times(1)).save(any(Iterable.class));
    }

    @Test
    void removesAListingWhenItIsDelisted() {
        when(indexOperations.exists()).thenReturn(true);

        searchIndex.delete(11L);

        verify(operations).delete("11", ProductSearchDocument.class);
    }

    @Test
    void inactiveListingsAreNotSaved() {
        Product product = searchableProduct(12L);
        product.setStatus(0);
        when(indexOperations.exists()).thenReturn(false);

        searchIndex.upsert(product);

        verify(operations, never()).save(any(ProductSearchDocument.class));
    }

    @Test
    void anElasticsearchOutageDoesNotEscapeIntoCatalogueWrites() {
        when(indexOperations.exists()).thenThrow(new IllegalStateException("offline"));

        assertDoesNotThrow(() -> searchIndex.upsert(searchableProduct(13L)));
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
