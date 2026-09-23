package sg.edu.nus.iss.c2csectrade.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import sg.edu.nus.iss.c2csectrade.entity.CartItem;

import java.util.List;

@Mapper
public interface CartItemMapper {
    /** Cart lines with the listing joined in, newest first. */
    List<CartItem> selectByUserId(@Param("userId") Long userId);

    CartItem selectByUserAndProduct(@Param("userId") Long userId, @Param("productId") Long productId);

    CartItem selectById(@Param("id") Long id);

    List<CartItem> selectByIds(@Param("ids") List<Long> ids);

    int insert(CartItem item);

    int updateQuantity(@Param("id") Long id, @Param("quantity") Integer quantity);

    int deleteById(@Param("id") Long id);

    int deleteByIds(@Param("ids") List<Long> ids);

    int deleteByUserId(@Param("userId") Long userId);
}
