package sg.edu.nus.iss.c2csectrade.service.recommendation;

import org.springframework.stereotype.Component;
import sg.edu.nus.iss.c2csectrade.entity.Product;
import sg.edu.nus.iss.c2csectrade.mapper.ProductMapper;
import java.util.List;

@Component
public class MostRecentStrategy implements RecommendationStrategy {
    private final ProductMapper mapper;
    public MostRecentStrategy(ProductMapper mapper) { this.mapper = mapper; }
    public String name() { return "most-recent"; }
    public List<Product> recommend(Product current, int limit) {
        if (limit <= 0) return List.of();
        return RecommendationCandidates.eligible(mapper.selectRecentProducts(limit * 3), current, limit);
    }
}
