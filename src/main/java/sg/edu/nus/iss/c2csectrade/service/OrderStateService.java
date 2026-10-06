package sg.edu.nus.iss.c2csectrade.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sg.edu.nus.iss.c2csectrade.entity.*;
import sg.edu.nus.iss.c2csectrade.mapper.OrderItemMapper;
import sg.edu.nus.iss.c2csectrade.mapper.OrderMapper;
import sg.edu.nus.iss.c2csectrade.mapper.ProductMapper;
import sg.edu.nus.iss.c2csectrade.event.OrderStateChangedEvent;

/**
 * Moving an order through its lifecycle.
 *
 * Which action each state permits, and who may perform it, live on
 * {@link OrderStatus} itself rather than in a chain of status comparisons here.
 * This class only does what every transition has in common: look the order up,
 * ask the state whether the action is allowed, check the caller is the right
 * party, apply it, and release the reservation when the order closes unpaid.
 */
@Service
public class OrderStateService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final ProductMapper productMapper;
    private final ApplicationEventPublisher events;

    public OrderStateService(OrderMapper orderMapper,
                             OrderItemMapper orderItemMapper,
                             ProductMapper productMapper,
                             ApplicationEventPublisher events) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.productMapper = productMapper;
        this.events = events;
    }

    @Transactional
    public Order apply(Long callerId, Long orderId, OrderAction action) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new IllegalArgumentException("Order not found");
        }

        OrderStatus current = order.statusAsEnum();
        OrderStatus next = current.after(action);
        if (next == null) {
            throw new IllegalStateException(
                    "Cannot " + action.name().toLowerCase().replace('_', ' ')
                            + " an order that is " + describe(current));
        }
        requireCaller(order, current.actor(action), callerId);

        // The WHERE clause names the state being left, so two concurrent
        // attempts at the same transition cannot both succeed.
        if (orderMapper.transition(orderId, current.name(), next.name(), null) == 0) {
            throw new IllegalStateException("This order has already moved on");
        }

        if (current.holdsReservation() && next.isTerminal()) {
            releaseReservation(orderId);
        }

        // Every transition announces itself once, from the one place that
        // performs them. Observers pick this up after the commit, so a failing
        // notification cannot roll back the state change that caused it.
        events.publishEvent(new OrderStateChangedEvent(
                order.getId(), order.getOrderNo(),
                order.getBuyerId(), order.getSellerId(),
                current.name(), next.name(), order.getTotalAmount()));

        return orderMapper.selectById(orderId);
    }

    private void requireCaller(Order order, OrderStatus.Actor actor, Long callerId) {
        Long expected = switch (actor) {
            case BUYER -> order.getBuyerId();
            case SELLER -> order.getSellerId();
            case SYSTEM -> null;
        };
        if (expected != null && !expected.equals(callerId)) {
            throw new IllegalArgumentException(
                    "Only the " + actor.name().toLowerCase() + " can do this");
        }
    }

    private void releaseReservation(Long orderId) {
        for (OrderItem item : orderItemMapper.selectByOrderId(orderId)) {
            productMapper.releaseStock(item.getProductId(), item.getQuantity());
        }
    }

    private String describe(OrderStatus status) {
        return status.name().toLowerCase().replace('_', ' ');
    }
}
