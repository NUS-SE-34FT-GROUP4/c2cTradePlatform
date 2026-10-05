package sg.edu.nus.iss.c2csectrade.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import sg.edu.nus.iss.c2csectrade.dto.ReviewRequest;
import sg.edu.nus.iss.c2csectrade.security.JwtTokenProvider;
import sg.edu.nus.iss.c2csectrade.service.OrderService;
import sg.edu.nus.iss.c2csectrade.service.ReviewService;

import java.util.List;
import java.util.concurrent.*;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ReviewIT extends MySqlIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired OrderService orders;
    @Autowired ReviewService reviews;
    @Autowired JwtTokenProvider tokens;
    @Autowired ObjectMapper json;

    // Fulfilment is another story. Set up its completed-order postcondition directly.
    private Long completedOrder() {
        Long id = orders.checkoutDirect(1L, 1L, 1).getId();
        jdbc.update("UPDATE oms_order SET status='COMPLETED' WHERE id=?", id);
        return id;
    }

    private ReviewRequest request(Long orderId) {
        ReviewRequest request = new ReviewRequest();
        request.setOrderId(orderId);
        request.setProductRating(5);
        request.setSellerRating(3);
        request.setComment("<script>alert('x')</script><b>Works well</b>");
        request.setReviewImages(List.of("https://example.test/photo,a.png"));
        request.setIsAnonymous(false);
        return request;
    }

    private String token(String user) { return "Bearer " + tokens.generateTokenForUsername(user); }

    @Test
    void savesSeparateRatingsSanitizesCommentAndShowsOnProductPage() throws Exception {
        Long id = completedOrder();
        mvc.perform(post("/api/reviews").header("Authorization", token("admin"))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request(id))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.productRating").value(5))
                .andExpect(jsonPath("$.data.sellerRating").value(3))
                .andExpect(jsonPath("$.data.comment").value("Works well"));
        assertEquals("Works well", jdbc.queryForObject("SELECT comment FROM review WHERE order_id=?", String.class, id));
        mvc.perform(get("/api/reviews/product/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalReviews").value(1))
                .andExpect(jsonPath("$.averageRating").value(5.0))
                .andExpect(jsonPath("$.reviews[0].buyerId").value(1))
                .andExpect(jsonPath("$.reviews[0].reviewImages[0]").value("https://example.test/photo,a.png"));
        mvc.perform(get("/api/reviews/check/" + id).header("Authorization", token("admin")))
                .andExpect(jsonPath("$.hasReviewed").value(true));
    }

    @Test
    void anonymousReviewDoesNotLeakIdentityInCreationOrPublicResponse() throws Exception {
        ReviewRequest request = request(completedOrder());
        request.setIsAnonymous(true);
        mvc.perform(post("/api/reviews").header("Authorization", token("admin"))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.buyerName").value("Anonymous buyer"))
                .andExpect(jsonPath("$.data.buyerId").doesNotExist())
                .andExpect(jsonPath("$.data.buyerAvatar").doesNotExist())
                .andExpect(jsonPath("$.data.orderId").doesNotExist());
        mvc.perform(get("/api/reviews/product/1"))
                .andExpect(jsonPath("$.reviews[0].buyerName").value("Anonymous buyer"))
                .andExpect(jsonPath("$.reviews[0].buyerId").doesNotExist())
                .andExpect(jsonPath("$.reviews[0].buyerAvatar").doesNotExist())
                .andExpect(jsonPath("$.reviews[0].orderId").doesNotExist());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING_PAYMENT", "PAID", "SHIPPED", "CANCELLED", "EXPIRED", "DELIVERED"})
    void onlyCompletedOrdersMayBeReviewed(String state) throws Exception {
        Long id = completedOrder();
        jdbc.update("UPDATE oms_order SET status=? WHERE id=?", state, id);
        mvc.perform(post("/api/reviews").header("Authorization", token("admin"))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request(id))))
                .andExpect(status().isConflict());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM review", Integer.class));
    }

    @Test
    void refusesSecondReview() throws Exception {
        ReviewRequest request = request(completedOrder());
        reviews.createReview(1L, request);
        mvc.perform(post("/api/reviews").header("Authorization", token("admin"))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)))
                .andExpect(status().isConflict());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM review", Integer.class));
    }

    @Test
    void sellerAndOtherBuyersCannotReviewOrCheckAnotherBuyersOrder() throws Exception {
        Long id = completedOrder();
        for (String user : List.of("seller_lvl1", "seller_lvl2")) {
            mvc.perform(post("/api/reviews").header("Authorization", token(user))
                            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request(id))))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/api/reviews/check/" + id).header("Authorization", token(user)))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/reviews").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(request(id)))).andExpect(status().isUnauthorized());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM review", Integer.class));
    }

    @Test
    void validatesBothRatingsAndImageLimit() {
        ReviewRequest request = request(completedOrder());
        for (Integer bad : new Integer[]{null, 0, 6}) {
            request.setProductRating(bad);
            assertThrows(IllegalArgumentException.class, () -> reviews.createReview(1L, request));
        }
        request.setProductRating(1);
        for (Integer bad : new Integer[]{null, 0, 6}) {
            request.setSellerRating(bad);
            assertThrows(IllegalArgumentException.class, () -> reviews.createReview(1L, request));
        }
        request.setSellerRating(5);
        request.setReviewImages(IntStream.range(0, 6).mapToObj(i -> "https://example.test/" + i + ".png").toList());
        assertThrows(IllegalArgumentException.class, () -> reviews.createReview(1L, request));
        request.setReviewImages(request.getReviewImages().subList(0, 5));
        assertEquals(5, reviews.createReview(1L, request).getReviewImages().size());
    }

    @Test
    void refusesUnsafeUrlsAndOverlongComments() {
        ReviewRequest request = request(completedOrder());
        for (String bad : List.of("javascript:alert(1)", "data:image/svg+xml,bad", "//evil.test/a.png", "http://user:pass@example.test/a.png")) {
            request.setReviewImages(List.of(bad));
            assertThrows(IllegalArgumentException.class, () -> reviews.createReview(1L, request));
        }
        request.setReviewImages(null);
        request.setComment("x".repeat(5001));
        assertThrows(IllegalArgumentException.class, () -> reviews.createReview(1L, request));
        request.setComment(null);
        assertTrue(reviews.createReview(1L, request).getReviewImages().isEmpty());
    }

    @Test
    void multiItemOrderRequiresExplicitPurchasedProduct() {
        Long id = completedOrder();
        jdbc.update("INSERT INTO oms_order_item(order_id,product_id,product_name,quantity,unit_price_snapshot) VALUES (?,2,'Laptop',1,4200)", id);
        ReviewRequest request = request(id);
        assertThrows(IllegalArgumentException.class, () -> reviews.createReview(1L, request));
        request.setProductId(4L);
        assertThrows(IllegalArgumentException.class, () -> reviews.createReview(1L, request));
        request.setProductId(2L);
        assertEquals(2L, reviews.createReview(1L, request).getProductId());
        assertTrue(reviews.getProductReviews(1L).isEmpty());
        assertEquals(1, reviews.getProductReviews(2L).size());
    }

    @Test
    void uploadUsesSharedStorageAndRejectsNonImages() throws Exception {
        when(fileStorageService.upload(any(), eq("review"))).thenReturn("https://example.test/review.png");
        mvc.perform(multipart("/api/reviews/images").file(new MockMultipartFile("file", "review.png", "image/png", new byte[]{1}))
                        .header("Authorization", token("admin")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.url").value("https://example.test/review.png"));
        mvc.perform(multipart("/api/reviews/images").file(new MockMultipartFile("file", "review.html", "text/html", new byte[]{1}))
                        .header("Authorization", token("admin"))).andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/reviews/images").file(new MockMultipartFile("file", "review.png", "image/png", new byte[5 * 1024 * 1024 + 1]))
                        .header("Authorization", token("admin"))).andExpect(status().isBadRequest());
        verify(fileStorageService, times(1)).upload(any(), eq("review"));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void simultaneousSubmissionsPersistExactlyOneReview() throws Exception {
        Long id = completedOrder();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> submit = () -> {
            start.await();
            try { reviews.createReview(1L, request(id)); return true; }
            catch (IllegalStateException expected) { return false; }
        };
        try {
            Future<Boolean> first = pool.submit(submit);
            Future<Boolean> second = pool.submit(submit);
            start.countDown();
            assertNotEquals(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM review WHERE order_id=?", Integer.class, id));
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(10, TimeUnit.SECONDS);
            jdbc.update("DELETE FROM review WHERE order_id=?", id);
            jdbc.update("DELETE FROM oms_order_item WHERE order_id=?", id);
            jdbc.update("DELETE FROM oms_order WHERE id=?", id);
            jdbc.update("UPDATE pms_product SET reserved_stock=0 WHERE id=1");
        }
    }
}
