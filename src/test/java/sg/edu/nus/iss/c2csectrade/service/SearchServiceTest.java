package sg.edu.nus.iss.c2csectrade.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import sg.edu.nus.iss.c2csectrade.dto.ProductDTO;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SearchServiceTest {

    private final SearchService searchService = new SearchService();

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
}
