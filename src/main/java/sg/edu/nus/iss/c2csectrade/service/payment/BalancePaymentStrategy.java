package sg.edu.nus.iss.c2csectrade.service.payment;

import org.springframework.stereotype.Component;
import sg.edu.nus.iss.c2csectrade.entity.Order;
import sg.edu.nus.iss.c2csectrade.entity.PaymentMethod;
import sg.edu.nus.iss.c2csectrade.service.WalletService;

import java.math.BigDecimal;

/**
 * The only method settled inside this system: the buyer's balance is debited
 * at payment and the seller's is credited when the buyer confirms receipt.
 *
 * Between those two points the money is in neither account. It is accounted
 * for by the order's own ledger rows, which is what lets the seller refund a
 * paid order without needing the balance to still be there.
 */
@Component
public class BalancePaymentStrategy implements PaymentStrategy {

    private final WalletService walletService;

    public BalancePaymentStrategy(WalletService walletService) {
        this.walletService = walletService;
    }

    @Override
    public PaymentMethod method() {
        return PaymentMethod.BALANCE;
    }

    @Override
    public void settle(Order order, Long buyerId, String paymentPassword) {
        walletService.pay(buyerId, paymentPassword, order.getTotalAmount());
    }

    @Override
    public void releaseToSeller(Order order, BigDecimal amount) {
        walletService.credit(order.getSellerId(), amount);
    }

    /** The held money goes back where it came from; the seller never had it. */
    @Override
    public void refund(Order order, BigDecimal amount) {
        walletService.credit(order.getBuyerId(), amount);
    }
}
