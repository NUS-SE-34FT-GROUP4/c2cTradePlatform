package sg.edu.nus.iss.c2csectrade.event;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import sg.edu.nus.iss.c2csectrade.service.NotificationService;

/**
 * The Observer of an order state change.
 *
 * It runs after the transaction that moved the order has committed, so a slow
 * or failing notification can never roll the state change back. Delivery is
 * per recipient: one party failing leaves the other untouched.
 */
@Component
public class OrderNotificationListener {

    private final NotificationService notifications;

    public OrderNotificationListener(NotificationService notifications) {
        this.notifications = notifications;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(OrderStateChangedEvent event) {
        String text = "Order " + event.orderNo()
                + " (" + event.amount().toPlainString() + ") is now " + event.to() + ".";
        notifications.deliver(event.buyerId(), event.orderId(), text);
        notifications.deliver(event.sellerId(), event.orderId(), text);
    }
}
