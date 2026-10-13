package sg.edu.nus.iss.c2csectrade.service.payment;

import sg.edu.nus.iss.c2csectrade.entity.Order;
import sg.edu.nus.iss.c2csectrade.entity.PaymentMethod;

import java.math.BigDecimal;

/**
 * How money moves for one payment method.
 *
 * The surrounding flow — verify the password, settle, record the transaction,
 * advance the order, convert the stock reservation — is identical for all four
 * methods and lives in PaymentService. Only the settlement differs, so that is
 * the one thing behind this interface. Adding a fifth method adds a class
 * rather than another branch in the middle of the order flow.
 *
 * Money reaches the seller in two steps, not one: {@link #settle} takes it
 * from the buyer, and it is held against the order until the buyer confirms
 * receipt ({@link #releaseToSeller}) or the seller hands it back
 * ({@link #refund}). A method that settles outside this system still follows
 * that timing, so the order's state means the same thing whichever method
 * was used.
 */
public interface PaymentStrategy {

    PaymentMethod method();

    /**
     * Take the money from the buyer. Throws when settlement fails; returning
     * normally means the buyer has been charged and the amount is held against
     * the order. The seller is not paid here.
     *
     * @param paymentPassword already format-checked by the caller; a strategy
     *                        that settles inside this system verifies it
     */
    void settle(Order order, Long buyerId, String paymentPassword);

    /**
     * Hand the held amount to the seller, because the buyer confirmed the item
     * arrived. This is the only point at which the money becomes the seller's.
     *
     * @param amount what was actually paid, read from the payment's ledger row
     *               rather than recomputed from the order
     */
    void releaseToSeller(Order order, BigDecimal amount);

    /**
     * Return the held amount to the buyer instead. Nothing is taken from the
     * seller, who was never paid, so a refund cannot fail because the seller
     * has spent the money.
     *
     * @param amount what was actually paid, read from the payment's ledger row
     *               rather than recomputed from the order
     */
    void refund(Order order, BigDecimal amount);
}
