package sg.edu.nus.iss.c2csectrade.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sg.edu.nus.iss.c2csectrade.entity.*;
import sg.edu.nus.iss.c2csectrade.exception.InsufficientStockException;
import sg.edu.nus.iss.c2csectrade.mapper.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Checkout.
 *
 * Two rules drive this class:
 *
 * 1. An order belongs to exactly one seller. A cart spanning three sellers
 *    becomes three orders, because fulfilment, cancellation and payout all
 *    happen per seller and a mixed order has no single owner.
 *
 * 2. The unit price is copied onto the order line at creation. After that the
 *    seller may edit the listing freely without touching what the buyer owes.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final CartItemMapper cartItemMapper;
    private final ProductMapper productMapper;

    @Value("${order.payment-window-minutes:15}")
    private int paymentWindowMinutes;

    public OrderService(OrderMapper orderMapper,
                        OrderItemMapper orderItemMapper,
                        CartItemMapper cartItemMapper,
                        ProductMapper productMapper) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.cartItemMapper = cartItemMapper;
        this.productMapper = productMapper;
    }

    /**
     * Merged checkout: take the selected cart lines, group them by seller and
     * create one order per group.
     */
    @Transactional
    public List<Order> checkoutFromCart(Long buyerId, List<Long> cartItemIds) {
        if (cartItemIds == null || cartItemIds.isEmpty()) {
            throw new IllegalArgumentException("Select at least one cart item");
        }
        List<CartItem> selected = cartItemMapper.selectByIds(cartItemIds);
        if (selected.size() != cartItemIds.size()) {
            throw new IllegalArgumentException("Some cart items no longer exist");
        }
        for (CartItem item : selected) {
            if (!item.getUserId().equals(buyerId)) {
                throw new IllegalArgumentException("Cart item does not belong to you");
            }
        }

        List<Line> lines = selected.stream()
                .map(item -> new Line(item.getProduct(), item.getQuantity()))
                .collect(Collectors.toList());

        List<Order> orders = createOrders(buyerId, lines);
        cartItemMapper.deleteByIds(cartItemIds);
        return orders;
    }

    /** Buy now: one product, straight to an order, cart untouched. */
    @Transactional
    public Order checkoutDirect(Long buyerId, Long productId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be at least 1");
        }
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new IllegalArgumentException("Listing not found");
        }
        return createOrders(buyerId, List.of(new Line(product, quantity))).get(0);
    }

    private List<Order> createOrders(Long buyerId, List<Line> lines) {
        // Group first, so the split is decided before anything is reserved.
        Map<Long, List<Line>> bySeller = new LinkedHashMap<>();
        for (Line line : lines) {
            Product product = line.product();
            if (product == null) {
                throw new IllegalArgumentException("Listing not found");
            }
            if (product.getStatus() != 1) {
                throw new IllegalArgumentException("Listing is no longer for sale: " + product.getName());
            }
            if (product.getUserId().equals(buyerId)) {
                throw new IllegalArgumentException("You cannot buy your own listing");
            }
            bySeller.computeIfAbsent(product.getUserId(), k -> new ArrayList<>()).add(line);
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expireAt = now.plusMinutes(paymentWindowMinutes);
        List<Order> created = new ArrayList<>();

        for (Map.Entry<Long, List<Line>> entry : bySeller.entrySet()) {
            Order order = new Order();
            order.setOrderNo(nextOrderNo());
            order.setBuyerId(buyerId);
            order.setSellerId(entry.getKey());
            order.setStatus(OrderStatus.PENDING_PAYMENT.name());
            order.setExpireAt(expireAt);

            List<OrderItem> items = new ArrayList<>();
            BigDecimal total = BigDecimal.ZERO;

            for (Line line : entry.getValue()) {
                Product product = line.product();
                reserve(product, line.quantity());

                OrderItem item = new OrderItem();
                item.setProductId(product.getId());
                item.setProductName(product.getName());
                item.setQuantity(line.quantity());
                // The snapshot. Everything downstream prices off this, not the listing.
                item.setUnitPriceSnapshot(product.getPrice());
                items.add(item);
                total = total.add(item.getSubtotal());
            }

            order.setTotalAmount(total);
            orderMapper.insert(order);
            items.forEach(item -> item.setOrderId(order.getId()));
            orderItemMapper.batchInsert(items);
            order.setItems(items);
            created.add(order);
        }
        return created;
    }

    /**
     * Conditional update in the database, not a read-then-write here: two
     * buyers checking out the same single-item listing at the same moment both
     * see stock 1, and only the update that actually changes a row wins.
     */
    private void reserve(Product product, int quantity) {
        if (productMapper.reserveStock(product.getId(), quantity) == 0) {
            throw new InsufficientStockException("Not enough stock left for " + product.getName());
        }
    }

    @Transactional
    public Order cancel(Long buyerId, Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null || !order.getBuyerId().equals(buyerId)) {
            throw new IllegalArgumentException("Order not found");
        }
        if (!OrderStatus.PENDING_PAYMENT.name().equals(order.getStatus())) {
            throw new IllegalStateException("Only an unpaid order can be cancelled");
        }
        releaseAndMark(order, OrderStatus.CANCELLED);
        return orderMapper.selectById(orderId);
    }

    /**
     * Expire orders whose payment window has passed and hand their held stock
     * back. Sprint 3 schedules this; it is callable now so the behaviour can be
     * demonstrated and tested.
     */
    @Transactional
    public int expireOverdueOrders() {
        List<Order> overdue = orderMapper.selectExpiredPending(LocalDateTime.now());
        for (Order order : overdue) {
            releaseAndMark(order, OrderStatus.EXPIRED);
        }
        if (!overdue.isEmpty()) {
            log.info("Expired {} unpaid orders and released their reserved stock", overdue.size());
        }
        return overdue.size();
    }

    private void releaseAndMark(Order order, OrderStatus status) {
        for (OrderItem item : orderItemMapper.selectByOrderId(order.getId())) {
            productMapper.releaseStock(item.getProductId(), item.getQuantity());
        }
        orderMapper.updateStatus(order.getId(), status.name());
    }

    public List<Order> listForBuyer(Long buyerId) {
        return orderMapper.selectByBuyerId(buyerId);
    }

    public List<Order> listForSeller(Long sellerId) {
        return orderMapper.selectBySellerId(sellerId);
    }

    public Order getForUser(Long userId, Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null
                || (!order.getBuyerId().equals(userId) && !order.getSellerId().equals(userId))) {
            return null;
        }
        return order;
    }

    private String nextOrderNo() {
        return String.format("%s%04d",
                LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")),
                ThreadLocalRandom.current().nextInt(10000));
    }

    /** A product plus how many of it, before it is grouped into an order. */
    private record Line(Product product, int quantity) {
    }
}
