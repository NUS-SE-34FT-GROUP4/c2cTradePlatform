package sg.edu.nus.iss.c2csectrade.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sg.edu.nus.iss.c2csectrade.dto.ReviewRequest;
import sg.edu.nus.iss.c2csectrade.dto.ReviewResponse;
import sg.edu.nus.iss.c2csectrade.entity.*;
import sg.edu.nus.iss.c2csectrade.mapper.*;

import java.net.URI;
import java.util.List;

/** Adapted from the supplied ReviewServiceImpl, without Sprint 4 credit scoring. */
@Service
public class ReviewService {
    private final ReviewMapper reviews;
    private final OrderMapper orders;
    private final OrderItemMapper items;
    private final UserMapper users;
    private final ProductMapper products;
    private final ObjectMapper json;

    public ReviewService(ReviewMapper reviews, OrderMapper orders, OrderItemMapper items,
                         UserMapper users, ProductMapper products, ObjectMapper json) {
        this.reviews = reviews;
        this.orders = orders;
        this.items = items;
        this.users = users;
        this.products = products;
        this.json = json;
    }

    @Transactional
    public ReviewResponse createReview(Long buyerId, ReviewRequest request) {
        if (request == null || request.getOrderId() == null) {
            throw new IllegalArgumentException("Order ID is required");
        }
        Order order = ownedOrder(buyerId, request.getOrderId());
        if (!OrderStatus.COMPLETED.name().equals(order.getStatus())) {
            throw new IllegalStateException("Only a completed order can be reviewed");
        }
        if (reviews.selectByOrderId(order.getId()) != null) {
            throw new IllegalStateException("This order has already been reviewed");
        }
        validateRating(request.getProductRating());
        validateRating(request.getSellerRating());
        if (request.getComment() != null && request.getComment().length() > 5000) {
            throw new IllegalArgumentException("Comment cannot exceed 5000 characters");
        }
        List<String> images = request.getReviewImages() == null ? List.of() : request.getReviewImages();
        if (images.size() > 5) throw new IllegalArgumentException("At most five review images are allowed");
        for (String image : images) {
            if (image == null || image.length() > 1024) throw new IllegalArgumentException("Invalid image URL");
            URI uri = URI.create(image);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null) {
                throw new IllegalArgumentException("Review images must use HTTP or HTTPS URLs");
            }
        }
        List<OrderItem> lines = items.selectByOrderId(order.getId());
        if (lines.isEmpty()) throw new IllegalStateException("Order has no items");
        Long productId = request.getProductId();
        if (productId == null && lines.size() == 1) productId = lines.get(0).getProductId();
        final Long selected = productId;
        if (selected == null || lines.stream().noneMatch(i -> selected.equals(i.getProductId()))) {
            throw new IllegalArgumentException("Choose a product from this order");
        }
        Review review = new Review();
        review.setOrderId(order.getId());
        review.setProductId(selected);
        review.setBuyerId(buyerId);
        review.setSellerId(order.getSellerId());
        review.setProductRating(request.getProductRating());
        review.setSellerRating(request.getSellerRating());
        review.setComment(HtmlSanitizer.clean(request.getComment()));
        review.setIsAnonymous(Boolean.TRUE.equals(request.getIsAnonymous()));
        try {
            review.setReviewImages(json.writeValueAsString(images));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid review images", e);
        }
        try {
            reviews.insert(review);
        } catch (DuplicateKeyException e) {
            // The database constraint also closes the simultaneous-submit race.
            throw new IllegalStateException("This order has already been reviewed", e);
        }
        return toResponse(reviews.selectByOrderId(order.getId()));
    }

    public boolean hasReviewed(Long buyerId, Long orderId) {
        ownedOrder(buyerId, orderId);
        return reviews.selectByOrderId(orderId) != null;
    }

    public List<ReviewResponse> getProductReviews(Long productId) {
        return reviews.selectByProductId(productId).stream().map(this::toResponse).toList();
    }

    private Order ownedOrder(Long buyerId, Long orderId) {
        Order order = orders.selectById(orderId);
        if (order == null) throw new IllegalArgumentException("Order not found");
        if (!order.getBuyerId().equals(buyerId)) throw new AccessDeniedException("Only the buyer can review this order");
        return order;
    }

    private void validateRating(Integer value) {
        if (value == null || value < 1 || value > 5) throw new IllegalArgumentException("Ratings must be between 1 and 5");
    }

    private ReviewResponse toResponse(Review review) {
        ReviewResponse response = new ReviewResponse();
        response.setId(review.getId());
        response.setProductId(review.getProductId());
        response.setSellerId(review.getSellerId());
        response.setProductRating(review.getProductRating());
        response.setSellerRating(review.getSellerRating());
        response.setComment(review.getComment());
        response.setIsAnonymous(review.getIsAnonymous());
        response.setCreatedAt(review.getCreatedAt());
        try {
            response.setReviewImages(review.getReviewImages() == null ? List.of()
                    : json.readValue(review.getReviewImages(), new TypeReference<List<String>>() {}));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored review images could not be read", e);
        }
        Product product = products.selectById(review.getProductId());
        if (product != null) response.setProductName(product.getName());
        if (Boolean.TRUE.equals(review.getIsAnonymous())) {
            // Do not expose buyer ID, avatar, or order ID through an anonymous response.
            response.setBuyerName("Anonymous buyer");
        } else {
            User buyer = users.selectById(review.getBuyerId());
            if (buyer != null) {
                response.setBuyerId(buyer.getId());
                response.setBuyerName(buyer.getDisplayName());
                response.setBuyerAvatar(buyer.getAvatarUrl());
            }
        }
        return response;
    }
}
