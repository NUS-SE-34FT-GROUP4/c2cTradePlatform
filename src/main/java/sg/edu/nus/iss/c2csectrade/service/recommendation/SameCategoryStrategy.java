package sg.edu.nus.iss.c2csectrade.service.recommendation;

import org.springframework.stereotype.Component;
import sg.edu.nus.iss.c2csectrade.entity.Product;
import sg.edu.nus.iss.c2csectrade.mapper.ProductMapper;
import java.util.List;

@Component
public class SameCategoryStrategy implements RecommendationStrategy {
    private final ProductMapper mapper;
    public SameCategoryStrategy(ProductMapper mapper) { this.mapper = mapper; }
    public String name() { return "same-category"; }
    public List<Product> recommend(Product current, int limit) {
        if (limit <= 0) return List.of();
        if (current.getCategory() == null) return List.of();
        return RecommendationCandidates.eligible(mapper.selectByCategory(current.getCategory()), current, limit);
    }
}
