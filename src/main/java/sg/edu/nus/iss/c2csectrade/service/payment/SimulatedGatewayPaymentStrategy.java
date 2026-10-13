package sg.edu.nus.iss.c2csectrade.service.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sg.edu.nus.iss.c2csectrade.entity.Order;
import sg.edu.nus.iss.c2csectrade.entity.PaymentMethod;
import sg.edu.nus.iss.c2csectrade.service.WalletService;

import java.math.BigDecimal;

/**
 * Shared behaviour for the three external methods. The proposal calls for them
 * to be simulated (NFR 6.4), so no network call is made: the payment password
 * is still verified and the buyer's balance is left alone, because the money
 * came from outside.
 *
 * The timing matches balance payment: the seller is credited when the buyer
 * confirms receipt, not at payment, so the order's state means the same thing
 * whichever method was used.
 */
abstract class SimulatedGatewayPaymentStrategy implements PaymentStrategy {

    private static final Logger log = LoggerFactory.getLogger(SimulatedGatewayPaymentStrategy.class);

    private final WalletService walletService;

    protected SimulatedGatewayPaymentStrategy(WalletService walletService) {
        this.walletService = walletService;
    }

    @Override
    public void settle(Order order, Long buyerId, String paymentPassword) {
        walletService.verifyPaymentPassword(buyerId, paymentPassword);
        log.info("Simulated {} settlement of {} for order {}; held until receipt is confirmed",
                method().code(), order.getTotalAmount(), order.getOrderNo());
    }

    @Override
    public void releaseToSeller(Order order, BigDecimal amount) {
        walletService.credit(order.getSellerId(), amount);
        log.info("Simulated {} payout of {} for order {}",
                method().code(), amount, order.getOrderNo());
    }

    /**
     * The buyer is reimbursed through the same external channel the money
     * arrived on, which is outside this system (NFR 6.4). So nothing moves on
     * the platform here, exactly as settle moved nothing on it.
     */
    @Override
    public void refund(Order order, BigDecimal amount) {
        log.info("Simulated {} refund of {} for order {}; buyer reimbursed off-platform",
                method().code(), amount, order.getOrderNo());
    }
}
