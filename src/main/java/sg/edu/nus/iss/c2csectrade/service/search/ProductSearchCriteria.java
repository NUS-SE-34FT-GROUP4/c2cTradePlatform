package sg.edu.nus.iss.c2csectrade.service.search;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

/** Filters shared by the Elasticsearch query and the existing SQL fallback. */
public record ProductSearchCriteria(
        String keyword,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Integer conditionLevel,
        String location,
        String categories) {

    public ProductSearchCriteria {
        keyword = trimToNull(keyword);
        location = trimToNull(location);
        categories = trimToNull(categories);
    }

    public boolean hasKeyword() {
        return keyword != null;
    }

    public List<String> categoryValues() {
        if (categories == null) {
            return List.of();
        }
        return Arrays.stream(categories.split(","))
                .map(String::strip)
                .filter(value -> !value.isEmpty())
                .distinct()
                .toList();
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }
}
