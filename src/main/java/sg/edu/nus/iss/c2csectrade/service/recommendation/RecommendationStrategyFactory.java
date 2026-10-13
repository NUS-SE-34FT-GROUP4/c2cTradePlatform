package sg.edu.nus.iss.c2csectrade.service.recommendation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import sg.edu.nus.iss.c2csectrade.entity.Product;
import java.util.*;

/** Spring discovers strategy components; each supplies its own registration name. */
@Component
public class RecommendationStrategyFactory {
    private static final Logger log = LoggerFactory.getLogger(RecommendationStrategyFactory.class);
    private static final String DEFAULT = "most-recent";
    private final Map<String, RecommendationStrategy> strategies;

    public RecommendationStrategyFactory(List<RecommendationStrategy> discovered) {
        Map<String, RecommendationStrategy> registry = new HashMap<>();
        for (RecommendationStrategy strategy : discovered) {
            if (registry.putIfAbsent(strategy.name(), strategy) != null) {
                throw new IllegalArgumentException("Duplicate recommendation strategy: " + strategy.name());
            }
        }
        if (!registry.containsKey(DEFAULT)) throw new IllegalArgumentException("Default strategy is required");
        strategies = Map.copyOf(registry);
    }

    public RecommendationStrategy resolve(String name) {
        return name == null ? strategies.get(DEFAULT) : strategies.getOrDefault(name, strategies.get(DEFAULT));
    }

    public List<Product> recommend(String name, Product current, int limit) {
        if (limit <= 0) return List.of();
        RecommendationStrategy selected = resolve(name);
        List<Product> result = attempt(selected, current, limit);
        if (result.isEmpty() && selected != strategies.get(DEFAULT)) {
            return attempt(strategies.get(DEFAULT), current, limit);
        }
        return result;
    }

    private List<Product> attempt(RecommendationStrategy strategy, Product current, int limit) {
        try {
            return strategy.recommend(current, limit);
        } catch (RuntimeException ex) {
            log.warn("Recommendation strategy {} unavailable", strategy.name(), ex);
            return List.of();
        }
    }
}
