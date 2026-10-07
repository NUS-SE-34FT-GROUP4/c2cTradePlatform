package sg.edu.nus.iss.c2csectrade.service.payment;

import sg.edu.nus.iss.c2csectrade.entity.Order;
import sg.edu.nus.iss.c2csectrade.entity.PaymentMethod;

/**
 * How money moves for one payment method.
 *
 * The surrounding flow — verify the password, settle, record the transaction,
 * advance the order, convert the stock reservation — is identical for all four
 * methods and lives in PaymentService. Only the settlement differs, so that is
 * the one thing behind this interface. Adding a fifth method adds a class
 * rather than another branch in the middle of the order flow.
 */
public interface PaymentStrategy {

    PaymentMethod method();

    /**
     * Move the money. Throws when settlement fails; returning normally means
     * the buyer has been charged and the seller credited.
     *
     * @param paymentPassword already format-checked by the caller; a strategy
     *                        that settles inside this system verifies it
     */
    void settle(Order order, Long buyerId, String paymentPassword);
}
