package sg.edu.nus.iss.c2csectrade.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sg.edu.nus.iss.c2csectrade.entity.*;
import sg.edu.nus.iss.c2csectrade.event.OrderStateChangedEvent;
import sg.edu.nus.iss.c2csectrade.mapper.*;
import sg.edu.nus.iss.c2csectrade.service.payment.PaymentStrategy;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Paying for an order.
 *
 * The flow is the same whichever method the buyer picks — check the order is
 * payable, settle, record the movement, advance the state, turn the stock
 * reservation into a real deduction. Only the settlement step varies, and that
 * is delegated to a {@link PaymentStrategy}.
 */
@Service
public class PaymentService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final ProductMapper productMapper;
    private final TransactionMapper transactionMapper;
    private final ApplicationEventPublisher events;
    private final Map<PaymentMethod, PaymentStrategy> strategies;

    public PaymentService(OrderMapper orderMapper,
                          OrderItemMapper orderItemMapper,
                          ProductMapper productMapper,
                          TransactionMapper transactionMapper,
                          ApplicationEventPublisher events,
                          List<PaymentStrategy> strategies) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.productMapper = productMapper;
        this.transactionMapper = transactionMapper;
        this.events = events;
        // Spring injects every implementation; indexing them here means a new
        // method is picked up by adding a @Component, with nothing to register.
        this.strategies = strategies.stream()
                .collect(Collectors.toMap(PaymentStrategy::method, Function.identity()));
    }

    @Transactional
    public Order pay(Long buyerId, Long orderId, String methodCode, String paymentPassword) {
        Order order = orderMapper.selectById(orderId);
        if (order == null || !order.getBuyerId().equals(buyerId)) {
            throw new IllegalArgumentException("Order not found");
        }
        if (!OrderStatus.PENDING_PAYMENT.name().equals(order.getStatus())) {
            throw new IllegalStateException("This order is not awaiting payment");
        }

        PaymentMethod method = PaymentMethod.of(methodCode);
        PaymentStrategy strategy = strategies.get(method);
        if (strategy == null) {
            throw new IllegalArgumentException("Unsupported payment method: " + methodCode);
        }

        // Claim the order first. The conditional update means a second attempt
        // on the same order changes no row and is rejected here, before any
        // money moves, rather than after a double charge.
        if (orderMapper.transition(orderId,
                OrderStatus.PENDING_PAYMENT.name(),
                OrderStatus.PAID.name(),
                method.code()) == 0) {
            throw new IllegalStateException("This order is no longer awaiting payment");
        }

        strategy.settle(order, buyerId, paymentPassword);
        recordTransaction(order, method, TransactionType.PAYMENT);
        convertReservationToDeduction(orderId);

        // Observers pick this up after the commit, not inside it.
        events.publishEvent(new OrderStateChangedEvent(order.getId(), order.getOrderNo(),
                order.getBuyerId(), order.getSellerId(),
                OrderStatus.PENDING_PAYMENT.name(), OrderStatus.PAID.name(), order.getTotalAmount()));

        return orderMapper.selectById(orderId);
    }

    private void recordTransaction(Order order, PaymentMethod method, TransactionType type) {
        Transaction transaction = new Transaction();
        transaction.setOrderId(order.getId());
        transaction.setAmount(order.getTotalAmount());
        transaction.setPaymentMethod(method.code());
        transaction.setTransactionType(type.name());
        transaction.setStatus("SUCCESS");
        transactionMapper.insert(transaction);
    }

    /**
     * The stock has been held since checkout. Now that it is sold, release the
     * hold and take it off the shelf in one step, so the total never dips below
     * what is actually available in between.
     */
    private void convertReservationToDeduction(Long orderId) {
        for (OrderItem item : orderItemMapper.selectByOrderId(orderId)) {
            productMapper.releaseStock(item.getProductId(), item.getQuantity());
            if (productMapper.decreaseStock(item.getProductId(), item.getQuantity()) == 0) {
                throw new IllegalStateException(
                        "Stock could not be deducted for " + item.getProductName());
            }
        }
    }
}
