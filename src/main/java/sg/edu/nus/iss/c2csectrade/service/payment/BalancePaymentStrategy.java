package sg.edu.nus.iss.c2csectrade.service.payment;

import org.springframework.stereotype.Component;
import sg.edu.nus.iss.c2csectrade.entity.Order;
import sg.edu.nus.iss.c2csectrade.entity.PaymentMethod;
import sg.edu.nus.iss.c2csectrade.service.WalletService;

/**
 * The only method settled inside this system: the buyer's balance is debited
 * and the seller's credited in the same transaction, so the money never exists
 * in neither account.
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
        walletService.credit(order.getSellerId(), order.getTotalAmount());
    }
}
