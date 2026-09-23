package sg.edu.nus.iss.c2csectrade.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import sg.edu.nus.iss.c2csectrade.entity.OrderItem;

import java.util.List;

@Mapper
public interface OrderItemMapper {
    List<OrderItem> selectByOrderId(@Param("orderId") Long orderId);

    int insert(OrderItem item);

    int batchInsert(@Param("items") List<OrderItem> items);
}
