package sg.edu.nus.iss.c2csectrade.service;

import org.junit.jupiter.api.Test;
import sg.edu.nus.iss.c2csectrade.dto.CreditHistory;
import sg.edu.nus.iss.c2csectrade.mapper.CreditMapper;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CreditServiceTest {
    @Test void noHistoryIsUnrated() {
        assertNull(CreditService.calculate(0, 0, 0).score());
    }
    @Test void completedSaleWithoutReviewUsesNeutralRating() {
        assertEquals(49, CreditService.calculate(1, 0, 0).score());
    }
    @Test void ratingExtremesAndVolumeCap() {
        assertEquals(17, CreditService.calculate(1, 1, 1).score());
        assertEquals(81, CreditService.calculate(1, 1, 5).score());
        assertEquals(100, CreditService.calculate(20, 20, 100).score());
        assertEquals(100, CreditService.calculate(200, 200, 1000).score());
        assertEquals(50, CreditService.calculate(2, 2, 6).score());
        assertEquals(40, CreditService.calculate(3, 3, 7).score());
    }
    @Test void batchesSellersAndKeepsAccountsWithoutHistoryUnrated() {
        CreditMapper mapper = mock(CreditMapper.class);
        CreditHistory history = new CreditHistory();
        history.setSellerId(1L); history.setCompletedOrders(2); history.setReviewCount(1); history.setRatingSum(4);
        when(mapper.selectHistory(any())).thenReturn(List.of(history));
        var service = new CreditService(mapper);
        var result = service.forSellers(List.of(1L, 2L, 1L));
        assertEquals(66, result.get(1L).score());
        assertNull(result.get(2L).score());
        verify(mapper).selectHistory(java.util.Set.of(1L, 2L));
        assertTrue(service.forSellers(List.of()).isEmpty());
        assertNull(service.forSeller(null).score());
        verifyNoMoreInteractions(mapper);
    }
}
