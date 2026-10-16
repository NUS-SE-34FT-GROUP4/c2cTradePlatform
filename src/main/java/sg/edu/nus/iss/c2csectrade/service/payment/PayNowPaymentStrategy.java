package sg.edu.nus.iss.c2csectrade.service.payment;

import org.springframework.stereotype.Component;
import sg.edu.nus.iss.c2csectrade.entity.Order;
import sg.edu.nus.iss.c2csectrade.entity.PaymentMethod;
import sg.edu.nus.iss.c2csectrade.service.WalletService;

import java.math.BigDecimal;

/**
 * PayNow: a bank transfer the buyer makes outside this system, simulated like
 * the other external methods (NFR 6.4).
 *
 * It shares their settlement and their payout timing, and differs in exactly
 * one answer. A PayNow transfer cannot be pulled back by whoever received it,
 * so a refund cannot be sent the way the payment arrived; the held amount is
 * handed to the buyer as account balance instead, which is a movement this
 * system can actually make and can show on the ledger.
 *
 * Nothing outside this class knows about that difference. The order flow, the
 * payment service and the ledger see the same three operations they see for
 * every other method.
 */
@Component
public class PayNowPaymentStrategy extends SimulatedGatewayPaymentStrategy {

    private final WalletService walletService;

    public PayNowPaymentStrategy(WalletService walletService) {
        super(walletService);
        this.walletService = walletService;
    }

    @Override
    public PaymentMethod method() {
        return PaymentMethod.PAYNOW;
    }

    /**
     * The transfer is irreversible, so the money goes back as account balance.
     * Still nothing is taken from the seller, who was never paid.
     */
    @Override
    public void refund(Order order, BigDecimal amount) {
        walletService.credit(order.getBuyerId(), amount);
    }
}
