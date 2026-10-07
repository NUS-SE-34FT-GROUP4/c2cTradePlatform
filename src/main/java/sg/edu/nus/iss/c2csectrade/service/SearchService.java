package sg.edu.nus.iss.c2csectrade.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import sg.edu.nus.iss.c2csectrade.dto.ProductDTO;
import sg.edu.nus.iss.c2csectrade.entity.Product;
import sg.edu.nus.iss.c2csectrade.service.search.ProductSearchCriteria;
import sg.edu.nus.iss.c2csectrade.service.search.ProductSearchQuery;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Keyword search over the catalogue.
 *
 * Elasticsearch supplies relevance ordering when a keyword is present. MySQL
 * remains authoritative and is also the fallback if the search projection is
 * unavailable. Highlighting stays engine-independent so both paths render the
 * same response shape.
 */
@Service
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    private final ProductSearchQuery searchQuery;
    private final ProductService productService;

    public SearchService(ProductSearchQuery searchQuery, ProductService productService) {
        this.searchQuery = searchQuery;
        this.productService = productService;
    }

    public List<ProductDTO> search(String keyword, BigDecimal minPrice, BigDecimal maxPrice,
            Integer conditionLevel, String location, String categories) {
        ProductSearchCriteria criteria = new ProductSearchCriteria(
                keyword, minPrice, maxPrice, conditionLevel, location, categories);

        List<Product> products;
        if (!criteria.hasKeyword()) {
            products = sqlSearch(criteria);
        } else {
            List<Long> rankedIds;
            try {
                rankedIds = searchQuery.search(criteria);
            } catch (RuntimeException exception) {
                log.warn("Elasticsearch query unavailable; using SQL search: {}", exception.getMessage());
                return highlight(productService.convertToDTOList(sqlSearch(criteria)), criteria.keyword());
            }
            products = productService.listProductsByIdsInOrder(rankedIds);
        }

        return highlight(productService.convertToDTOList(products), criteria.keyword());
    }

    private List<Product> sqlSearch(ProductSearchCriteria criteria) {
        return productService.listProductsWithFilters(
                criteria.keyword(), criteria.minPrice(), criteria.maxPrice(),
                criteria.conditionLevel(), criteria.location(), criteria.categories());
    }

    public List<ProductDTO> highlight(List<ProductDTO> results, String keyword) {
        if (keyword == null || keyword.isBlank() || results == null) {
            return results;
        }
        Pattern pattern = Pattern.compile(Pattern.quote(keyword.strip()), Pattern.CASE_INSENSITIVE);
        for (ProductDTO dto : results) {
            dto.setHighlightedName(mark(dto.getName(), pattern));
            dto.setHighlightedDescription(mark(snippet(dto.getDescription(), pattern), pattern));
        }
        return results;
    }

    private String mark(String text, Pattern pattern) {
        if (text == null) {
            return null;
        }
        Matcher matcher = pattern.matcher(text);
        return matcher.replaceAll(match -> "<mark>" + Matcher.quoteReplacement(match.group()) + "</mark>");
    }

    /** Show the text around the first hit rather than the whole description. */
    private String snippet(String text, Pattern pattern) {
        if (text == null) {
            return null;
        }
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return text.length() <= 120 ? text : text.substring(0, 120) + "...";
        }
        int start = Math.max(0, matcher.start() - 40);
        int end = Math.min(text.length(), matcher.end() + 80);
        return (start > 0 ? "..." : "") + text.substring(start, end) + (end < text.length() ? "..." : "");
    }
}
