package sg.edu.nus.iss.c2csectrade.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import sg.edu.nus.iss.c2csectrade.entity.CartItem;
import sg.edu.nus.iss.c2csectrade.mapper.OrderMapper;
import sg.edu.nus.iss.c2csectrade.security.JwtTokenProvider;
import sg.edu.nus.iss.c2csectrade.service.CartService;

import java.math.BigDecimal;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CheckoutIT extends MySqlIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired CartService carts;
    @Autowired OrderMapper orders;
    @Autowired JwtTokenProvider tokens;

    @Test
    void authenticatedCheckoutSplitsSellersAndPersistsSnapshots() throws Exception {
        CartItem first = carts.add(1L, 1L, 1);
        CartItem second = carts.add(1L, 4L, 1);
        mvc.perform(post("/api/orders/checkout")
                .header("Authorization", "Bearer " + tokens.generateTokenForUsername("admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cartItemIds\":[" + first.getId() + "," + second.getId() + "]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderCount").value(2));

        var saved = orders.selectByBuyerId(1L); // Executes XML, including nested order-line select.
        assertEquals(2, saved.size());
        assertTrue(saved.stream().allMatch(o -> "PENDING_PAYMENT".equals(o.getStatus())));
        assertTrue(saved.stream().allMatch(o -> o.getExpireAt() != null));
        assertEquals(2, saved.stream().map(o -> o.getSellerId()).distinct().count());
        assertTrue(carts.list(1L).isEmpty());
        assertEquals(1, jdbc.queryForObject("SELECT reserved_stock FROM pms_product WHERE id=1", Integer.class));
        jdbc.update("UPDATE pms_product SET price=1 WHERE id=1");
        var original = saved.stream().filter(o -> o.getSellerId().equals(101L)).findFirst().orElseThrow();
        assertEquals(new BigDecimal("2899.00"), orders.selectById(original.getId()).getItems().get(0).getUnitPriceSnapshot());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void failedCheckoutRollsBackEarlierReservationsAndKeepsCart() throws Exception {
        CartItem first = carts.add(1L, 1L, 1);
        CartItem second = carts.add(1L, 2L, 1);
        try {
            jdbc.update("UPDATE pms_product SET reserved_stock=stock WHERE id=2");
            mvc.perform(post("/api/orders/checkout")
                    .header("Authorization", "Bearer " + tokens.generateTokenForUsername("admin"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"cartItemIds\":[" + first.getId() + "," + second.getId() + "]}"))
                    .andExpect(status().isConflict());
            assertEquals(0, jdbc.queryForObject("SELECT reserved_stock FROM pms_product WHERE id=1", Integer.class));
            assertTrue(orders.selectByBuyerId(1L).isEmpty());
            assertEquals(2, carts.list(1L).size());
        } finally {
            jdbc.update("DELETE FROM oms_order_item WHERE order_id IN (SELECT id FROM oms_order WHERE buyer_id=1)");
            jdbc.update("DELETE FROM oms_order WHERE buyer_id=1");
            carts.clear(1L);
            jdbc.update("UPDATE pms_product SET reserved_stock=0 WHERE id IN (1,2)");
        }
    }

    @Test
    void checkoutWithoutAuthenticationIsRejected() throws Exception {
        mvc.perform(post("/api/orders/checkout").contentType(MediaType.APPLICATION_JSON)
                .content("{\"cartItemIds\":[1]}"))
                .andExpect(status().isUnauthorized());
        assertTrue(orders.selectByBuyerId(1L).isEmpty());
    }
}
