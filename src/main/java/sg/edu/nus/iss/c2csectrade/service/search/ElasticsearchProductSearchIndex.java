package sg.edu.nus.iss.c2csectrade.service.search;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.stereotype.Component;
import sg.edu.nus.iss.c2csectrade.entity.Product;

import java.util.Collection;
import java.util.List;

/**
 * Best-effort Elasticsearch index writer. Search is an optional projection, so
 * an indexing outage is logged but never rolls back a successful MySQL write.
 */
@Component
public class ElasticsearchProductSearchIndex implements ProductSearchIndex {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchProductSearchIndex.class);

    private final ElasticsearchOperations operations;

    public ElasticsearchProductSearchIndex(ElasticsearchOperations operations) {
        this.operations = operations;
    }

    @Override
    public void rebuild(Collection<Product> products) {
        try {
            recreateIndex();
            List<ProductSearchDocument> documents = products.stream()
                    .filter(this::isSearchable)
                    .map(ProductSearchDocument::from)
                    .toList();
            if (!documents.isEmpty()) {
                operations.save(documents);
            }
            log.info("Indexed {} catalogue listings in Elasticsearch", documents.size());
        } catch (RuntimeException exception) {
            log.warn("Elasticsearch rebuild skipped; SQL search remains available: {}", exception.getMessage());
        }
    }

    @Override
    public void upsert(Product product) {
        if (!isSearchable(product)) {
            delete(product.getId());
            return;
        }
        try {
            ensureIndex();
            operations.save(ProductSearchDocument.from(product));
        } catch (RuntimeException exception) {
            log.warn("Could not index listing {}; SQL search remains available: {}",
                    product.getId(), exception.getMessage());
        }
    }

    @Override
    public void delete(Long productId) {
        try {
            IndexOperations index = operations.indexOps(ProductSearchDocument.class);
            if (index.exists()) {
                operations.delete(productId.toString(), ProductSearchDocument.class);
            }
        } catch (RuntimeException exception) {
            log.warn("Could not remove listing {} from Elasticsearch: {}", productId, exception.getMessage());
        }
    }

    private void ensureIndex() {
        IndexOperations index = operations.indexOps(ProductSearchDocument.class);
        if (!index.exists()) {
            boolean created = index.createWithMapping();
            if (!created && !index.exists()) {
                throw new IllegalStateException("products index could not be created");
            }
        }
    }

    /** A real rebuild removes stale documents left behind during an earlier outage. */
    private void recreateIndex() {
        IndexOperations index = operations.indexOps(ProductSearchDocument.class);
        if (index.exists()) {
            index.delete();
        }
        if (!index.createWithMapping()) {
            throw new IllegalStateException("products index could not be recreated");
        }
    }

    private boolean isSearchable(Product product) {
        return product != null
                && product.getId() != null
                && product.getStatus() == 1
                && product.getAvailableStock() > 0;
    }
}
