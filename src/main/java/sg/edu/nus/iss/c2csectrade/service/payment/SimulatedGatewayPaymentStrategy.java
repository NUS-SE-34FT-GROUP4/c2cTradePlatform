package sg.edu.nus.iss.c2csectrade.service.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sg.edu.nus.iss.c2csectrade.entity.Order;
import sg.edu.nus.iss.c2csectrade.entity.PaymentMethod;
import sg.edu.nus.iss.c2csectrade.service.WalletService;

/**
 * Shared behaviour for the three external methods. The proposal calls for them
 * to be simulated (NFR 6.4), so no network call is made: the payment password
 * is still verified, the seller is still credited, and the buyer's balance is
 * left alone because the money came from outside.
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
        walletService.credit(order.getSellerId(), order.getTotalAmount());
        log.info("Simulated {} settlement of {} for order {}",
                method().code(), order.getTotalAmount(), order.getOrderNo());
    }
}
