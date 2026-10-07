package sg.edu.nus.iss.c2csectrade.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sg.edu.nus.iss.c2csectrade.dto.ProductDTO;
import sg.edu.nus.iss.c2csectrade.entity.Product;
import sg.edu.nus.iss.c2csectrade.service.search.ProductSearchCriteria;
import sg.edu.nus.iss.c2csectrade.service.search.ProductSearchQuery;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock private ProductSearchQuery searchQuery;
    @Mock private ProductService productService;

    private SearchService searchService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        searchService = new SearchService(searchQuery, productService);
    }

    private ProductDTO dto(String name, String description) {
        ProductDTO dto = new ProductDTO();
        dto.setName(name);
        dto.setDescription(description);
        return dto;
    }

    @Test
    @DisplayName("Matched terms are marked, case-insensitively")
    void marksMatches() {
        List<ProductDTO> results = new ArrayList<>(List.of(dto("Algorithm Textbook", "An algorithm reference")));

        searchService.highlight(results, "algorithm");

        assertEquals("<mark>Algorithm</mark> Textbook", results.get(0).getHighlightedName());
        assertTrue(results.get(0).getHighlightedDescription().contains("<mark>algorithm</mark>"));
    }

    @Test
    @DisplayName("A blank keyword leaves the results untouched")
    void blankKeywordIsANoOp() {
        List<ProductDTO> results = new ArrayList<>(List.of(dto("Textbook", "Description")));
        searchService.highlight(results, "  ");
        assertNull(results.get(0).getHighlightedName());
    }

    @Test
    @DisplayName("Regex characters in the keyword are treated as literal text")
    void keywordIsNotTreatedAsRegex() {
        List<ProductDTO> results = new ArrayList<>(List.of(dto("C++ Primer", "About C++")));
        assertDoesNotThrow(() -> searchService.highlight(results, "C++"));
        assertEquals("<mark>C++</mark> Primer", results.get(0).getHighlightedName());
    }

    @Test
    @DisplayName("Keyword searches hydrate Elasticsearch hits in relevance order")
    void usesElasticsearchRankingForKeywordSearch() {
        Product second = product(2L);
        Product first = product(1L);
        List<Product> rankedProducts = List.of(second, first);
        List<ProductDTO> rankedDtos = new ArrayList<>(List.of(
                dto("Algorithm handbook", "Reference"),
                dto("Data structures", "Includes algorithm examples")));
        when(searchQuery.search(any(ProductSearchCriteria.class))).thenReturn(List.of(2L, 1L));
        when(productService.listProductsByIdsInOrder(List.of(2L, 1L))).thenReturn(rankedProducts);
        when(productService.convertToDTOList(rankedProducts)).thenReturn(rankedDtos);

        List<ProductDTO> result = searchService.search(
                "algorithm", null, null, null, null, null);

        assertSame(rankedDtos, result);
        assertTrue(result.get(0).getHighlightedName().startsWith("<mark>Algorithm</mark>"));
        assertTrue(result.get(1).getHighlightedDescription().contains("<mark>algorithm</mark>"));
        verify(productService, never()).listProductsWithFilters(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("An Elasticsearch outage falls back to the existing SQL query")
    void fallsBackToSqlWhenElasticsearchIsUnavailable() {
        Product product = product(3L);
        List<Product> sqlProducts = List.of(product);
        List<ProductDTO> sqlDtos = new ArrayList<>(List.of(dto("Phone charger", "USB-C charger")));
        when(searchQuery.search(any(ProductSearchCriteria.class)))
                .thenThrow(new IllegalStateException("connection refused"));
        when(productService.listProductsWithFilters(
                "charger", new BigDecimal("5.00"), new BigDecimal("50.00"),
                8, "Kent Ridge", "electronics"))
                .thenReturn(sqlProducts);
        when(productService.convertToDTOList(sqlProducts)).thenReturn(sqlDtos);

        List<ProductDTO> result = searchService.search(
                "charger", new BigDecimal("5.00"), new BigDecimal("50.00"),
                8, "Kent Ridge", "electronics");

        assertEquals(1, result.size());
        assertEquals("Phone <mark>charger</mark>", result.get(0).getHighlightedName());
        verify(productService).listProductsWithFilters(
                "charger", new BigDecimal("5.00"), new BigDecimal("50.00"),
                8, "Kent Ridge", "electronics");
    }

    @Test
    @DisplayName("A MySQL hydration failure is not mislabeled as an Elasticsearch outage")
    void doesNotHideMysqlHydrationFailure() {
        when(searchQuery.search(any(ProductSearchCriteria.class))).thenReturn(List.of(3L));
        when(productService.listProductsByIdsInOrder(List.of(3L)))
                .thenThrow(new IllegalStateException("database unavailable"));

        assertThrows(IllegalStateException.class, () -> searchService.search(
                "charger", null, null, null, null, null));

        verify(productService, never()).listProductsWithFilters(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("A listing request without a keyword stays on MySQL")
    void blankKeywordSkipsElasticsearch() {
        when(productService.listProductsWithFilters(null, null, null, null, null, null))
                .thenReturn(List.of());
        when(productService.convertToDTOList(List.of())).thenReturn(new ArrayList<>());

        searchService.search("  ", null, null, null, "  ", null);

        verifyNoInteractions(searchQuery);
        verify(productService).listProductsWithFilters(null, null, null, null, null, null);
    }

    private Product product(Long id) {
        Product product = new Product();
        product.setId(id);
        return product;
    }
}
