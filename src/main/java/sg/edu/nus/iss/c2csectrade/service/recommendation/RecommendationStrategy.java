package sg.edu.nus.iss.c2csectrade.service.recommendation;

import sg.edu.nus.iss.c2csectrade.entity.Product;
import java.util.List;

public interface RecommendationStrategy {
    String name();
    List<Product> recommend(Product current, int limit);
}
