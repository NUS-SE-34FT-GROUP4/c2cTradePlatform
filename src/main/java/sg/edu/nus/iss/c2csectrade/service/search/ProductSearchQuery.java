package sg.edu.nus.iss.c2csectrade.service.search;

import java.util.List;

/** Read-side boundary for a relevance-ranked product search engine. */
public interface ProductSearchQuery {

    List<Long> search(ProductSearchCriteria criteria);
}
