package sg.edu.nus.iss.c2csectrade.service.search;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import sg.edu.nus.iss.c2csectrade.entity.Product;
import sg.edu.nus.iss.c2csectrade.mapper.ProductMapper;

/**
 * Rebuilds the index at startup and applies listing changes only after their
 * database transaction commits, preventing Elasticsearch from getting ahead of MySQL.
 */
@Component
public class ProductIndexCoordinator {

    private static final Logger log = LoggerFactory.getLogger(ProductIndexCoordinator.class);

    private final ProductMapper productMapper;
    private final ProductSearchIndex searchIndex;

    public ProductIndexCoordinator(ProductMapper productMapper, ProductSearchIndex searchIndex) {
        this.productMapper = productMapper;
        this.searchIndex = searchIndex;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void rebuildAfterStartup() {
        try {
            searchIndex.rebuild(productMapper.selectAllActive());
        } catch (RuntimeException exception) {
            log.warn("Catalogue index bootstrap skipped: {}", exception.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void apply(ProductIndexEvent event) {
        if (event.change() == ProductIndexEvent.Change.DELETE) {
            searchIndex.delete(event.productId());
            return;
        }

        try {
            Product product = productMapper.selectById(event.productId());
            if (product == null || product.getStatus() != 1 || product.getAvailableStock() <= 0) {
                searchIndex.delete(event.productId());
            } else {
                searchIndex.upsert(product);
            }
        } catch (RuntimeException exception) {
            log.warn("Listing {} could not be prepared for indexing: {}",
                    event.productId(), exception.getMessage());
        }
    }
}
