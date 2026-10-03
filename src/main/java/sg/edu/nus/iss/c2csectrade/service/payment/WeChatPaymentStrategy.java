package sg.edu.nus.iss.c2csectrade.service.payment;

import org.springframework.stereotype.Component;
import sg.edu.nus.iss.c2csectrade.entity.PaymentMethod;
import sg.edu.nus.iss.c2csectrade.service.WalletService;

@Component
public class WeChatPaymentStrategy extends SimulatedGatewayPaymentStrategy {

    public WeChatPaymentStrategy(WalletService walletService) {
        super(walletService);
    }

    @Override
    public PaymentMethod method() {
        return PaymentMethod.WECHAT;
    }
}
