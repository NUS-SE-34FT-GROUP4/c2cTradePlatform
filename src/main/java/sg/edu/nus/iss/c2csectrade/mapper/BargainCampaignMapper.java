package sg.edu.nus.iss.c2csectrade.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import sg.edu.nus.iss.c2csectrade.entity.BargainCampaign;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface BargainCampaignMapper {
    int insert(BargainCampaign campaign);

    BargainCampaign selectById(@Param("id") Long id);

    /** The buyer's live campaign on a listing, if any - enforces one per buyer per item. */
    BargainCampaign selectActiveByBuyerAndProduct(@Param("buyerId") Long buyerId,
                                                  @Param("productId") Long productId);

    List<BargainCampaign> selectByProductId(@Param("productId") Long productId);

    int updateCurrentPrice(@Param("id") Long id, @Param("currentPrice") BigDecimal currentPrice);

    /** Closes every ACTIVE campaign past its deadline; idempotent, so it is safe to sweep repeatedly. */
    int expireDue(@Param("now") LocalDateTime now);

    /** Retires the buyer's ACTIVE campaign on a listing when a negotiated deal turns into an order. */
    int markConverted(@Param("buyerId") Long buyerId, @Param("productId") Long productId);
}
