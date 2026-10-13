package sg.edu.nus.iss.c2csectrade.service;

import org.springframework.stereotype.Service;
import sg.edu.nus.iss.c2csectrade.dto.CreditHistory;
import sg.edu.nus.iss.c2csectrade.dto.CreditSummary;
import sg.edu.nus.iss.c2csectrade.mapper.CreditMapper;
import java.util.*;

@Service
public class CreditService {
    public static final CreditSummary UNRATED = new CreditSummary(null, 0, 0);
    private final CreditMapper mapper;

    public CreditService(CreditMapper mapper) { this.mapper = mapper; }

    public CreditSummary forSeller(Long sellerId) {
        return sellerId == null ? UNRATED : forSellers(List.of(sellerId)).get(sellerId);
    }

    public Map<Long, CreditSummary> forSellers(Collection<Long> sellerIds) {
        Map<Long, CreditSummary> result = new HashMap<>();
        sellerIds.stream().filter(Objects::nonNull).forEach(id -> result.put(id, UNRATED));
        if (!result.isEmpty()) {
            for (CreditHistory history : mapper.selectHistory(result.keySet())) {
                result.put(history.getSellerId(), calculate(history.getCompletedOrders(),
                        history.getReviewCount(), history.getRatingSum()));
            }
        }
        return result;
    }

    /** 80 points for average seller rating, 20 for up to 20 completed sales. */
    public static CreditSummary calculate(long completedOrders, long reviewCount, long ratingSum) {
        if (completedOrders == 0) return UNRATED;
        double average = reviewCount == 0 ? 3.0 : (double) ratingSum / reviewCount;
        int score = (int) Math.round(16 * average + Math.min(completedOrders, 20));
        return new CreditSummary(score, completedOrders, reviewCount);
    }
}
