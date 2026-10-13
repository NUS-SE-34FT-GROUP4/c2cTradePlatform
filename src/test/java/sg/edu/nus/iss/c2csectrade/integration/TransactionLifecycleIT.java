package sg.edu.nus.iss.c2csectrade.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import sg.edu.nus.iss.c2csectrade.mapper.OrderMapper;
import sg.edu.nus.iss.c2csectrade.security.JwtTokenProvider;
import sg.edu.nus.iss.c2csectrade.service.OrderService;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** HTTP -> security -> controller -> services -> production MySQL mappers. */
class TransactionLifecycleIT extends MySqlIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired JwtTokenProvider tokens;
    @Autowired OrderMapper orders;
    @Autowired OrderService orderService;

    @BeforeEach void fundBuyer() {
        jdbc.update("UPDATE users SET balance=500, payment_password_hash=? WHERE id=1",
                "$2a$12$qKa0QH..hifzPlSsE6U.Q.4gGjw513N7.xWFpmr5Q.cp12YhGY7lG");
    }
    private String token(String username) { return "Bearer " + tokens.generateTokenForUsername(username); }
    private JsonNode postJson(String path, String user, String body, int expected) throws Exception {
        var result = mvc.perform(post(path).header("Authorization", token(user))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().is(expected)).andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }
    private long buy() throws Exception {
        return postJson("/api/orders/buy-now", "admin", "{\"productId\":4,\"quantity\":1}", 201).get("id").asLong();
    }
    private void pay(long id) throws Exception {
        postJson("/api/orders/" + id + "/pay", "admin",
                "{\"paymentMethod\":\"balance\",\"paymentPassword\":\"123456\"}", 200);
    }
    private int reserved() { return jdbc.queryForObject("SELECT reserved_stock FROM pms_product WHERE id=4", Integer.class); }

    @Test void buyerPaysSellerShipsBuyerConfirmsAndReadsBothOrderViews() throws Exception {
        long id = buy();
        assertEquals(1, reserved());
        pay(id);
        assertEquals("PAID", orders.selectById(id).getStatus());
        assertEquals(0, reserved());
        assertEquals(new BigDecimal("445.00"), jdbc.queryForObject("SELECT balance FROM users WHERE id=1", BigDecimal.class));
        postJson("/api/orders/" + id + "/ship", "admin", "{}", 400);
        postJson("/api/orders/" + id + "/ship", "seller_lvl2", "{}", 200);
        assertEquals("SHIPPED", orders.selectById(id).getStatus());
        postJson("/api/orders/" + id + "/confirm-receipt", "seller_lvl2", "{}", 400);
        postJson("/api/orders/" + id + "/confirm-receipt", "admin", "{}", 200);
        assertEquals("COMPLETED", orders.selectById(id).getStatus());
        mvc.perform(get("/api/orders/" + id).header("Authorization", token("admin")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.items[0].unitPriceSnapshot").value(55));
        mvc.perform(get("/api/orders?role=seller").header("Authorization", token("seller_lvl2")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(id));
        mvc.perform(get("/api/orders/" + id).header("Authorization", token("seller_lvl1")))
                .andExpect(status().isNotFound());
        postJson("/api/orders/" + id + "/confirm-receipt", "admin", "{}", 409);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM oms_transaction WHERE order_id=?", Integer.class, id));
    }

    @Test void cancelledOrderReleasesStockExactlyOnceAndCannotBePaid() throws Exception {
        long id = buy();
        postJson("/api/orders/" + id + "/cancel", "seller_lvl1", "{}", 400);
        assertEquals(1, reserved());
        postJson("/api/orders/" + id + "/cancel", "admin", "{}", 200);
        assertEquals("CANCELLED", orders.selectById(id).getStatus());
        assertEquals(0, reserved());
        postJson("/api/orders/" + id + "/cancel", "admin", "{}", 409);
        postJson("/api/orders/" + id + "/pay", "admin", "{\"paymentMethod\":\"balance\"}", 409);
        assertEquals(0, reserved());
    }

    @Test void expirySweepReleasesOnlyOverdueReservationsAndIsIdempotent() throws Exception {
        long id = buy();
        jdbc.update("UPDATE oms_order SET expire_at=DATE_SUB(NOW(), INTERVAL 1 MINUTE) WHERE id=?", id);
        assertEquals(1, orderService.expireOverdueOrders());
        assertEquals("EXPIRED", orders.selectById(id).getStatus());
        assertEquals(0, reserved());
        assertEquals(0, orderService.expireOverdueOrders());
        long fresh = buy();
        assertEquals(0, orderService.expireOverdueOrders());
        assertEquals("PENDING_PAYMENT", orders.selectById(fresh).getStatus());
        assertEquals(1, reserved());
    }

    @Test void refundThroughHttpRestoresBalanceStockAndKeepsBothLedgerRows() throws Exception {
        long id = buy(); pay(id);
        postJson("/api/orders/" + id + "/refund", "seller_lvl2", "{}", 200);
        assertEquals("REFUNDED", orders.selectById(id).getStatus());
        assertEquals(new BigDecimal("500.00"), jdbc.queryForObject("SELECT balance FROM users WHERE id=1", BigDecimal.class));
        assertEquals(1, jdbc.queryForObject("SELECT stock FROM pms_product WHERE id=4", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM oms_transaction WHERE order_id=?", Integer.class, id));
    }

    @Test void acceptedBargainCreatesPayableOrderAtNegotiatedPrice() throws Exception {
        long offer = postJson("/api/bargain/offers", "admin", "{\"productId\":4,\"price\":40}", 201).get("id").asLong();
        postJson("/api/bargain/offers/" + offer + "/accept", "admin", "{}", 400);
        long id = postJson("/api/bargain/offers/" + offer + "/accept", "seller_lvl2", "{}", 200).get("orderId").asLong();
        assertEquals(new BigDecimal("40.00"), orders.selectById(id).getTotalAmount());
        assertEquals(new BigDecimal("40.00"), orders.selectById(id).getItems().get(0).getUnitPriceSnapshot());
        assertEquals(1, reserved());
        postJson("/api/bargain/offers/" + offer + "/accept", "seller_lvl2", "{}", 409);
        mvc.perform(get("/api/bargain/offers?productId=4").header("Authorization", token("admin")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].threadStatus").value("ACCEPTED"));
        pay(id);
        assertEquals(new BigDecimal("460.00"), jdbc.queryForObject("SELECT balance FROM users WHERE id=1", BigDecimal.class));
        assertEquals("PAID", orders.selectById(id).getStatus());
    }
}
