package sg.edu.nus.iss.c2csectrade.service.recommendation;

import sg.edu.nus.iss.c2csectrade.entity.Product;
import java.util.List;

final class RecommendationCandidates {
    private RecommendationCandidates() {}
    static List<Product> eligible(List<Product> candidates, Product current, int limit) {
        if (limit <= 0) return List.of();
        return candidates.stream()
                .filter(p -> !p.getId().equals(current.getId()))
                .filter(p -> Integer.valueOf(1).equals(p.getStatus()) && p.getAvailableStock() > 0)
                .limit(limit).toList();
    }
}
