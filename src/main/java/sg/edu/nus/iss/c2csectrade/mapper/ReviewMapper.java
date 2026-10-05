package sg.edu.nus.iss.c2csectrade.mapper;

import org.apache.ibatis.annotations.*;
import sg.edu.nus.iss.c2csectrade.entity.Review;
import java.util.List;

@Mapper
public interface ReviewMapper {
    @Insert("INSERT INTO review (order_id, product_id, buyer_id, seller_id, product_rating, seller_rating, comment, review_images, is_anonymous) " +
            "VALUES (#{orderId}, #{productId}, #{buyerId}, #{sellerId}, #{productRating}, #{sellerRating}, #{comment}, #{reviewImages}, #{isAnonymous})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Review review);

    @Select("SELECT * FROM review WHERE order_id=#{orderId}")
    Review selectByOrderId(Long orderId);

    @Select("SELECT * FROM review WHERE product_id=#{productId} ORDER BY created_at DESC, id DESC")
    List<Review> selectByProductId(Long productId);
}
