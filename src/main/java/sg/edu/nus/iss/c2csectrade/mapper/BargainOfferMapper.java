package sg.edu.nus.iss.c2csectrade.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import sg.edu.nus.iss.c2csectrade.entity.BargainOffer;

import java.util.List;

@Mapper
public interface BargainOfferMapper {
    int insert(BargainOffer offer);

    BargainOffer selectById(@Param("id") Long id);

    /** One thread: every entry one buyer has made on one product, oldest first. */
    List<BargainOffer> selectThread(@Param("productId") Long productId, @Param("buyerId") Long buyerId);

    /** Every thread on the listing, oldest first - the seller's view. */
    List<BargainOffer> selectByProduct(@Param("productId") Long productId);

    /** Moves an entry to a terminal state; orderId is only set on acceptance. */
    int updateStatus(@Param("id") Long id, @Param("status") String status, @Param("orderId") Long orderId);
}
