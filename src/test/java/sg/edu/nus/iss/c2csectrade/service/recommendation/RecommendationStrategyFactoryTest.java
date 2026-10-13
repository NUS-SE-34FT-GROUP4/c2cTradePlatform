package sg.edu.nus.iss.c2csectrade.service.recommendation;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import sg.edu.nus.iss.c2csectrade.entity.Product;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecommendationStrategyFactoryTest {
    private RecommendationStrategy strategy(String name) {
        var strategy = mock(RecommendationStrategy.class);
        when(strategy.name()).thenReturn(name); return strategy;
    }
    @Test void discoveredThirdStrategyNeedsNoFactoryOrCallerChange() {
        var recent = strategy("most-recent");
        var third = strategy("third-party");
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean("recent", RecommendationStrategy.class, () -> recent);
            context.registerBean("plugin", RecommendationStrategy.class, () -> third);
            context.register(RecommendationStrategyFactory.class);
            context.refresh();
            var factory = context.getBean(RecommendationStrategyFactory.class);
            assertSame(third, factory.resolve("third-party"));
            assertSame(recent, factory.resolve("unknown"));
            assertSame(recent, factory.resolve(null));
        }
    }
    @Test void unavailableAndEmptyStrategyFallBackWithoutBreakingThePage() {
        var recent = strategy("most-recent"); var category = strategy("same-category");
        var current = new Product(); var other = new Product();
        when(recent.recommend(current, 8)).thenReturn(List.of(other));
        when(category.recommend(current, 8)).thenReturn(List.of()).thenThrow(new IllegalStateException("offline"));
        var factory = new RecommendationStrategyFactory(List.of(recent, category));
        assertEquals(List.of(other), factory.recommend("same-category", current, 8));
        assertEquals(List.of(other), factory.recommend("same-category", current, 8));
        when(recent.recommend(current, 8)).thenThrow(new IllegalStateException("offline"));
        assertTrue(factory.recommend("unknown", current, 8).isEmpty());
        assertTrue(factory.recommend("same-category", current, 0).isEmpty());
    }
    @Test void duplicateNamesAndMissingDefaultFailAtStartup() {
        assertThrows(IllegalArgumentException.class, () -> new RecommendationStrategyFactory(List.of(strategy("same-category"))));
        assertThrows(IllegalArgumentException.class, () -> new RecommendationStrategyFactory(List.of(strategy("most-recent"), strategy("most-recent"))));
    }
}
