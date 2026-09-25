package sg.edu.nus.iss.c2csectrade.service;

import org.springframework.stereotype.Service;
import sg.edu.nus.iss.c2csectrade.entity.Product;
import sg.edu.nus.iss.c2csectrade.mapper.ProductMapper;
import sg.edu.nus.iss.c2csectrade.mapper.ViewHistoryMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Category-based related items.
 *
 * Three rules decide what a buyer may be shown, and all three exist because
 * breaking them produces something visibly wrong on the page:
 *
 *  - never the item being viewed, which would be a link back to itself
 *  - never something that cannot be bought (delisted, or nothing available)
 *  - never an empty strip where a section header already promised content, so
 *    a category with nothing left falls back to recent listings
 */
@Service
public class RecommendationService {

    private final ProductMapper productMapper;
    private final ViewHistoryMapper viewHistoryMapper;

    public RecommendationService(ProductMapper productMapper, ViewHistoryMapper viewHistoryMapper) {
        this.productMapper = productMapper;
        this.viewHistoryMapper = viewHistoryMapper;
    }

    public void recordView(Long userId, Long productId) {
        if (userId != null && productId != null) {
            viewHistoryMapper.record(userId, productId);
        }
    }

    /** Items in the same category as the one being viewed. */
    public List<Product> similarTo(Long productId, int limit) {
        Product current = productMapper.selectById(productId);
        if (current == null) {
            return List.of();
        }
        List<Product> candidates = current.getCategory() == null
                ? List.of()
                : productMapper.selectByCategory(current.getCategory());

        List<Product> picked = filter(candidates, productId, limit);
        if (picked.isEmpty()) {
            // Nothing left in this category; show something rather than a gap.
            picked = filter(productMapper.selectRecentProducts(limit * 3), productId, limit);
        }
        return picked;
    }

    /**
     * Sprint 2 keeps this deliberately simple: the categories the user has
     * browsed, and recent listings for anyone with no history. The cold-start
     * branching and the personalised algorithm are Sprint 5.
     */
    public List<Product> forUser(Long userId, int limit) {
        List<String> categories = userId == null
                ? List.of()
                : viewHistoryMapper.recentCategories(userId, 3);

        Map<Long, Product> picked = new LinkedHashMap<>();
        for (String category : categories) {
            for (Product product : filter(productMapper.selectByCategory(category), null, limit)) {
                picked.putIfAbsent(product.getId(), product);
                if (picked.size() >= limit) {
                    return new ArrayList<>(picked.values());
                }
            }
        }
        for (Product product : filter(productMapper.selectRecentProducts(limit * 3), null, limit)) {
            picked.putIfAbsent(product.getId(), product);
            if (picked.size() >= limit) {
                break;
            }
        }
        return new ArrayList<>(picked.values());
    }

    private List<Product> filter(List<Product> candidates, Long excludeId, int limit) {
        List<Product> result = new ArrayList<>();
        for (Product product : candidates) {
            if (excludeId != null && excludeId.equals(product.getId())) {
                continue;
            }
            if (product.getStatus() != 1 || product.getAvailableStock() <= 0) {
                continue;
            }
            result.add(product);
            if (result.size() >= limit) {
                break;
            }
        }
        return result;
    }
}
