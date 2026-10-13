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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Keyword search over the catalogue.
 *
 * Elasticsearch supplies relevance ordering when a keyword is present. MySQL
 * determines membership on every request; Elasticsearch only reorders those
 * rows. This preserves the existing SQL matching contract even when tokenisation,
 * index lag or the ES result window would otherwise hide or add listings.
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

        // Keep database failures outside the ES fallback boundary. A failed
        // authoritative query must not be reported as successful degradation.
        List<Product> products = sqlSearch(criteria);
        String path = "sql_browse";
        int rankedCount = 0;
        int missingCount = 0;
        int ignoredCount = 0;
        if (criteria.hasKeyword()) {
            List<Long> rankedIds;
            try {
                rankedIds = searchQuery.search(criteria);
            } catch (RuntimeException exception) {
                // Do not log query text or client exception messages (which can
                // include query bodies). Each request retries ES: no sticky outage.
                log.warn("catalogue_search path=sql_fallback results={} reason={}",
                        products.size(), exception.getClass().getSimpleName());
                return highlight(productService.convertToDTOList(products), criteria.keyword());
            }
            Map<Long, Product> remaining = new LinkedHashMap<>();
            products.forEach(product -> remaining.put(product.getId(), product));
            List<Product> ordered = new ArrayList<>(products.size());
            for (Long id : rankedIds) {
                Product product = remaining.remove(id);
                if (product != null) {
                    ordered.add(product);
                } else {
                    ignoredCount++;
                }
            }
            rankedCount = ordered.size();
            missingCount = remaining.size();
            // Append unindexed/tokenisation-missed rows in their SQL order.
            ordered.addAll(remaining.values());
            products = ordered;
            path = "elasticsearch_ranked";
        }
        log.info("catalogue_search path={} results={} ranked={} sql_only={} ignored_es_hits={}",
                path, products.size(), rankedCount, missingCount, ignoredCount);
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
