package sg.edu.nus.iss.c2csectrade.service.search;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ElasticsearchProductSearchQueryTest {

    @Mock private ElasticsearchOperations operations;
    @Mock private SearchHits<ProductSearchDocument> searchHits;

    private ElasticsearchProductSearchQuery searchQuery;

    @BeforeEach
    void setUp() {
        searchQuery = new ElasticsearchProductSearchQuery(operations);
    }

    @Test
    void returnsDocumentIdsInElasticsearchScoreOrder() {
        ProductSearchDocument first = document(7L);
        ProductSearchDocument second = document(3L);
        when(searchHits.stream()).thenReturn(List.of(hit(first, 10.0f), hit(second, 2.0f)).stream());
        when(operations.search(any(NativeQuery.class), eq(ProductSearchDocument.class)))
                .thenReturn(searchHits);

        List<Long> result = searchQuery.search(new ProductSearchCriteria(
                "textbook", null, null, null, null, null));

        assertEquals(List.of(7L, 3L), result);
    }

    @Test
    void buildsBoostedKeywordQueryWithCatalogueFilters() {
        when(searchHits.stream()).thenReturn(java.util.stream.Stream.empty());
        when(operations.search(any(NativeQuery.class), eq(ProductSearchDocument.class)))
                .thenReturn(searchHits);

        searchQuery.search(new ProductSearchCriteria(
                "phone", new BigDecimal("10.00"), new BigDecimal("500.00"),
                9, "Kent Ridge", "electronics, mobile"));

        ArgumentCaptor<NativeQuery> captor = ArgumentCaptor.forClass(NativeQuery.class);
        verify(operations).search(captor.capture(), eq(ProductSearchDocument.class));
        NativeQuery nativeQuery = captor.getValue();
        BoolQuery bool = nativeQuery.getQuery().bool();
        assertEquals(List.of("name^4", "category^2", "description"),
                bool.must().get(0).multiMatch().fields());
        assertEquals(6, bool.filter().size());
        BoolQuery categoryFilter = bool.filter().get(5).bool();
        assertEquals(2, categoryFilter.should().size());
        assertEquals("1", categoryFilter.minimumShouldMatch());
        assertEquals(ElasticsearchProductSearchQuery.MAX_RESULTS,
                nativeQuery.getPageable().getPageSize());
    }

    @Test
    void rejectsQueriesWithoutAKeyword() {
        assertThrows(IllegalArgumentException.class,
                () -> searchQuery.search(new ProductSearchCriteria(
                        "  ", null, null, null, null, null)));
    }

    private ProductSearchDocument document(Long id) {
        ProductSearchDocument document = new ProductSearchDocument();
        document.setId(id);
        return document;
    }

    private SearchHit<ProductSearchDocument> hit(ProductSearchDocument document, float score) {
        return new SearchHit<>("products", document.getId().toString(), null, score,
                new Object[0], Map.of(), Map.of(), null, null, List.of(), document);
    }
}
