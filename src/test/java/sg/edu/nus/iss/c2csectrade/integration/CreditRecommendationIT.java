package sg.edu.nus.iss.c2csectrade.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import sg.edu.nus.iss.c2csectrade.security.JwtTokenProvider;
import sg.edu.nus.iss.c2csectrade.service.OrderService;
import sg.edu.nus.iss.c2csectrade.service.CreditService;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

class CreditRecommendationIT extends MySqlIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired OrderService orders;
    @Autowired CreditService credits;
    @Autowired JwtTokenProvider tokens;
    private String token(String name) { return "Bearer " + tokens.generateTokenForUsername(name); }

    @Test void reviewImmediatelyChangesAccountAndListingCreditWithoutRecomputation() throws Exception {
        mvc.perform(get("/api/users/me").header("Authorization", token("seller_lvl1")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.credit.score").value(nullValue()));
        long id = orders.checkoutDirect(1L, 1L, 1).getId();
        // Fulfilment is exercised end-to-end in TransactionLifecycleIT; here isolate its read-side postcondition.
        jdbc.update("UPDATE oms_order SET status='COMPLETED' WHERE id=?", id);
        assertEquals(49, credits.forSeller(101L).score());
        mvc.perform(post("/api/reviews").header("Authorization", token("admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":" + id + ",\"productRating\":3,\"sellerRating\":5,\"comment\":\"Great seller\"}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/users/me").header("Authorization", token("seller_lvl1")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.credit.score").value(81))
                .andExpect(jsonPath("$.credit.completedOrders").value(1))
                .andExpect(jsonPath("$.credit.reviewCount").value(1));
        mvc.perform(get("/api/products/1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sellerCredit.score").value(81));
        mvc.perform(get("/api/products"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.userId == 101)].sellerCredit.score", everyItem(is(81))));
        // One order with several lines still counts once; another seller's history is unrelated.
        jdbc.update("INSERT INTO oms_order_item(order_id,product_id,product_name,quantity,unit_price_snapshot) VALUES (?,2,'Second line',1,10)", id);
        assertEquals(1, credits.forSeller(101L).completedOrders());
        assertNull(credits.forSeller(102L).score());
    }

    @Test void pendingCancelledAndRefundedOrdersDoNotCountAsCompletedSales() {
        long id = orders.checkoutDirect(1L, 1L, 1).getId();
        for (String state : new String[]{"PENDING_PAYMENT", "PAID", "SHIPPED", "CANCELLED", "EXPIRED", "REFUNDED"}) {
            jdbc.update("UPDATE oms_order SET status=? WHERE id=?", state, id);
            assertNull(credits.forSeller(101L).score(), state);
        }
    }

    @Test void anonymousRecommendationsResolveRegisteredStrategiesAndUnknownNamesFallBack() throws Exception {
        for (String name : new String[]{"same-category", "most-recent", "missing-plugin"}) {
            mvc.perform(get("/api/recommendations/products/1/similar").param("strategy", name).param("limit", "2"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.length()", lessThanOrEqualTo(2)))
                    .andExpect(jsonPath("$[*].id", not(hasItem(1))))
                    .andExpect(jsonPath("$[*].status", everyItem(is(1))));
        }
        mvc.perform(get("/api/recommendations/products/1/similar").param("limit", "0"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/api/recommendations/products/999999/similar"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/api/recommendations/for-you").header("Authorization", token("admin")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()", greaterThan(0)));
    }
}
