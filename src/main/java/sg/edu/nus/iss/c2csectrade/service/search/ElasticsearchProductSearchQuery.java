package sg.edu.nus.iss.c2csectrade.service.search;

import co.elastic.clients.json.JsonData;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Component;

import java.util.List;

/** Elasticsearch implementation of keyword matching and relevance ordering. */
@Component
public class ElasticsearchProductSearchQuery implements ProductSearchQuery {

    // The current catalogue endpoint is not paginated. Keep the old API behaviour
    // while staying below Elasticsearch's default max_result_window of 10,000.
    static final int MAX_RESULTS = 1_000;

    private final ElasticsearchOperations operations;

    public ElasticsearchProductSearchQuery(ElasticsearchOperations operations) {
        this.operations = operations;
    }

    @Override
    public List<Long> search(ProductSearchCriteria criteria) {
        if (criteria == null || !criteria.hasKeyword()) {
            throw new IllegalArgumentException("A keyword is required for Elasticsearch search");
        }

        SearchHits<ProductSearchDocument> hits = operations.search(
                buildQuery(criteria), ProductSearchDocument.class);
        return hits.stream()
                .map(SearchHit::getContent)
                .map(ProductSearchDocument::getId)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    NativeQuery buildQuery(ProductSearchCriteria criteria) {
        return NativeQuery.builder()
                .withQuery(query -> query.bool(bool -> {
                    // Default score ordering ranks name matches ahead of category and
                    // description matches while still accepting a hit in any field.
                    bool.must(must -> must.multiMatch(multiMatch -> multiMatch
                            .query(criteria.keyword())
                            .fields("name^4", "category^2", "description")));
                    bool.filter(filter -> filter.term(term -> term.field("status").value(1)));
                    bool.filter(filter -> filter.range(range -> range
                            .field("availableStock")
                            .gt(JsonData.of(0))));

                    if (criteria.minPrice() != null || criteria.maxPrice() != null) {
                        bool.filter(filter -> filter.range(range -> {
                            range.field("price");
                            if (criteria.minPrice() != null) {
                                range.gte(JsonData.of(criteria.minPrice()));
                            }
                            if (criteria.maxPrice() != null) {
                                range.lte(JsonData.of(criteria.maxPrice()));
                            }
                            return range;
                        }));
                    }
                    if (criteria.conditionLevel() != null) {
                        bool.filter(filter -> filter.term(term -> term
                                .field("conditionLevel")
                                .value(criteria.conditionLevel())));
                    }
                    if (criteria.location() != null) {
                        bool.filter(filter -> filter.match(match -> match
                                .field("location")
                                .query(criteria.location())));
                    }
                    List<String> categories = criteria.categoryValues();
                    if (!categories.isEmpty()) {
                        bool.filter(filter -> filter.bool(categoryFilter -> {
                            categories.forEach(category -> categoryFilter.should(should -> should
                                    .term(term -> term
                                            .field("category.keyword")
                                            .value(category)
                                            .caseInsensitive(true))));
                            return categoryFilter.minimumShouldMatch("1");
                        }));
                    }
                    return bool;
                }))
                .withPageable(PageRequest.of(0, MAX_RESULTS))
                .build();
    }
}
