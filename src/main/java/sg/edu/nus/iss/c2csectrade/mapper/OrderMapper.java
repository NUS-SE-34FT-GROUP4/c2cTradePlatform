package sg.edu.nus.iss.c2csectrade.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import sg.edu.nus.iss.c2csectrade.entity.Order;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface OrderMapper {
    Order selectById(@Param("id") Long id);

    List<Order> selectByBuyerId(@Param("buyerId") Long buyerId);

    List<Order> selectBySellerId(@Param("sellerId") Long sellerId);

    /** Unpaid orders whose expiry has passed; the scheduled release works off this. */
    List<Order> selectExpiredPending(@Param("now") LocalDateTime now);

    int insert(Order order);

    int updateStatus(@Param("id") Long id, @Param("status") String status);

    /**
     * Move an order from one state to the next, stamping the matching column.
     * Returns 0 when the order is no longer in {@code fromStatus}, which is how
     * a concurrent transition is rejected rather than silently overwritten.
     */
    int transition(@Param("id") Long id,
                   @Param("fromStatus") String fromStatus,
                   @Param("toStatus") String toStatus,
                   @Param("paymentMethod") String paymentMethod);
}
