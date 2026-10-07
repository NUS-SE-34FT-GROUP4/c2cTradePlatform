package sg.edu.nus.iss.c2csectrade.event;

import java.math.BigDecimal;

/**
 * Raised after an order has moved to a new state. Observers subscribe to this
 * instead of the order service calling into each of them, so adding a
 * notification, a credit-score update or anything else later touches nothing
 * that already exists.
 *
 * @param orderId  id of the order that changed
 * @param orderNo  human-readable order number, used in message bodies
 * @param buyerId  buyer party of the order
 * @param sellerId seller party of the order
 * @param from     state before the change
 * @param to       state after the change
 * @param amount   order total, for use in message bodies
 */
public record OrderStateChangedEvent(Long orderId,
                                     String orderNo,
                                     Long buyerId,
                                     Long sellerId,
                                     String from,
                                     String to,
                                     BigDecimal amount) {
}
