package sg.edu.nus.iss.c2csectrade.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import sg.edu.nus.iss.c2csectrade.entity.*;
import sg.edu.nus.iss.c2csectrade.exception.InsufficientStockException;
import sg.edu.nus.iss.c2csectrade.mapper.*;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Sprint 2 acceptance checks for checkout: the per-seller split, the price
 * snapshot, and what happens when stock runs out mid-checkout.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private OrderMapper orderMapper;
    @Mock private OrderItemMapper orderItemMapper;
    @Mock private CartItemMapper cartItemMapper;
    @Mock private ProductMapper productMapper;

    @InjectMocks private OrderService orderService;

    private static final Long BUYER = 10L;
    private static final Long SELLER_A = 20L;
    private static final Long SELLER_B = 30L;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(orderService, "paymentWindowMinutes", 15);
        // Give every inserted order an id, as the database would.
        lenient().when(orderMapper.insert(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(System.nanoTime());
            return 1;
        });
        lenient().when(productMapper.reserveStock(anyLong(), anyInt())).thenReturn(1);
    }

    private Product product(Long id, Long sellerId, String name, String price, int stock) {
        Product product = new Product();
        product.setId(id);
        product.setUserId(sellerId);
        product.setName(name);
        product.setPrice(new BigDecimal(price));
        product.setStock(stock);
        product.setStatus(1);
        return product;
    }

    private CartItem cartLine(Long id, Product product, int quantity) {
        CartItem item = new CartItem();
        item.setId(id);
        item.setUserId(BUYER);
        item.setProductId(product.getId());
        item.setQuantity(quantity);
        item.setProduct(product);
        return item;
    }

    @Test
    @DisplayName("A cart spanning two sellers produces one order per seller")
    void splitsCartBySeller() {
        List<CartItem> cart = List.of(
                cartLine(1L, product(101L, SELLER_A, "Textbook", "30.00", 5), 1),
                cartLine(2L, product(102L, SELLER_A, "Lamp", "20.00", 5), 2),
                cartLine(3L, product(103L, SELLER_B, "Headphones", "80.00", 5), 1));
        when(cartItemMapper.selectByIds(anyList())).thenReturn(cart);

        List<Order> orders = orderService.checkoutFromCart(BUYER, List.of(1L, 2L, 3L));

        assertEquals(2, orders.size(), "two sellers means two orders");
        Order sellerAOrder = orders.stream().filter(o -> SELLER_A.equals(o.getSellerId())).findFirst().orElseThrow();
        Order sellerBOrder = orders.stream().filter(o -> SELLER_B.equals(o.getSellerId())).findFirst().orElseThrow();

        assertEquals(2, sellerAOrder.getItems().size(), "seller A's two lines stay in one order");
        assertEquals(new BigDecimal("70.00"), sellerAOrder.getTotalAmount(), "30.00 + 2 x 20.00");
        assertEquals(1, sellerBOrder.getItems().size());
        assertEquals(new BigDecimal("80.00"), sellerBOrder.getTotalAmount());
        assertTrue(orders.stream().allMatch(o -> OrderStatus.PENDING_PAYMENT.name().equals(o.getStatus())));
    }

    @Test
    @DisplayName("The order line keeps the price it was created with when the listing price changes later")
    void snapshotsUnitPrice() {
        Product listing = product(101L, SELLER_A, "Textbook", "30.00", 5);
        when(cartItemMapper.selectByIds(anyList())).thenReturn(List.of(cartLine(1L, listing, 1)));

        Order order = orderService.checkoutFromCart(BUYER, List.of(1L)).get(0);

        // The seller raises the price after the order exists.
        listing.setPrice(new BigDecimal("99.00"));

        assertEquals(new BigDecimal("30.00"), order.getItems().get(0).getUnitPriceSnapshot());
        assertEquals(new BigDecimal("30.00"), order.getTotalAmount());
        assertEquals("Textbook", order.getItems().get(0).getProductName());
    }

    @Test
    @DisplayName("Checkout is rejected when the reservation cannot be taken")
    void rejectsWhenStockCannotBeReserved() {
        Product listing = product(101L, SELLER_A, "Last one", "30.00", 1);
        when(cartItemMapper.selectByIds(anyList())).thenReturn(List.of(cartLine(1L, listing, 1)));
        // Someone else's checkout got there first, so the conditional update changes no row.
        when(productMapper.reserveStock(101L, 1)).thenReturn(0);

        assertThrows(InsufficientStockException.class,
                () -> orderService.checkoutFromCart(BUYER, List.of(1L)));
        verify(cartItemMapper, never()).deleteByIds(anyList());
    }

    @Test
    @DisplayName("A buyer cannot order their own listing")
    void rejectsSelfPurchase() {
        Product own = product(101L, BUYER, "My own thing", "30.00", 5);
        when(cartItemMapper.selectByIds(anyList())).thenReturn(List.of(cartLine(1L, own, 1)));

        assertThrows(IllegalArgumentException.class,
                () -> orderService.checkoutFromCart(BUYER, List.of(1L)));
    }

    @Test
    @DisplayName("Cancelling an unpaid order releases exactly what it reserved")
    void cancelReleasesReservedStock() {
        Order order = new Order();
        order.setId(500L);
        order.setBuyerId(BUYER);
        order.setSellerId(SELLER_A);
        order.setStatus(OrderStatus.PENDING_PAYMENT.name());
        when(orderMapper.selectById(500L)).thenReturn(order);

        OrderItem item = new OrderItem();
        item.setProductId(101L);
        item.setQuantity(3);
        when(orderItemMapper.selectByOrderId(500L)).thenReturn(List.of(item));

        orderService.cancel(BUYER, 500L);

        verify(productMapper).releaseStock(101L, 3);
        verify(orderMapper).updateStatus(500L, OrderStatus.CANCELLED.name());
    }

    @Test
    @DisplayName("Expiring overdue orders releases their stock and marks them EXPIRED")
    void expiryReleasesStock() {
        Order overdue = new Order();
        overdue.setId(600L);
        overdue.setStatus(OrderStatus.PENDING_PAYMENT.name());
        when(orderMapper.selectExpiredPending(any())).thenReturn(List.of(overdue));

        OrderItem item = new OrderItem();
        item.setProductId(202L);
        item.setQuantity(1);
        when(orderItemMapper.selectByOrderId(600L)).thenReturn(List.of(item));

        assertEquals(1, orderService.expireOverdueOrders());
        verify(productMapper).releaseStock(202L, 1);
        verify(orderMapper).updateStatus(600L, OrderStatus.EXPIRED.name());
    }

    @Test
    @DisplayName("Cart lines belonging to another user are refused")
    void refusesOtherPeoplesCartLines() {
        CartItem someoneElses = cartLine(1L, product(101L, SELLER_A, "Textbook", "30.00", 5), 1);
        someoneElses.setUserId(999L);
        when(cartItemMapper.selectByIds(anyList())).thenReturn(List.of(someoneElses));

        assertThrows(IllegalArgumentException.class,
                () -> orderService.checkoutFromCart(BUYER, List.of(1L)));
    }

    @Test
    @DisplayName("Successful checkout clears only the cart lines that were bought")
    void clearsOnlyCheckedOutLines() {
        when(cartItemMapper.selectByIds(anyList()))
                .thenReturn(List.of(cartLine(1L, product(101L, SELLER_A, "Textbook", "30.00", 5), 1)));

        orderService.checkoutFromCart(BUYER, List.of(1L));

        ArgumentCaptor<List<Long>> captor = ArgumentCaptor.forClass(List.class);
        verify(cartItemMapper).deleteByIds(captor.capture());
        assertEquals(List.of(1L), captor.getValue());
        verify(cartItemMapper, never()).deleteByUserId(anyLong());
    }
}
