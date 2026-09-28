package sg.edu.nus.iss.c2csectrade.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import sg.edu.nus.iss.c2csectrade.entity.Transaction;

import java.util.List;

@Mapper
public interface TransactionMapper {
    int insert(Transaction transaction);

    List<Transaction> selectByOrderId(@Param("orderId") Long orderId);

    /** The successful payment for an order, if it has one. Used to size a refund. */
    Transaction selectSuccessfulPayment(@Param("orderId") Long orderId);
}
