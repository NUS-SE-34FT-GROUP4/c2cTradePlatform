package sg.edu.nus.iss.c2csectrade.mapper;

import org.apache.ibatis.annotations.*;
import sg.edu.nus.iss.c2csectrade.dto.CreditHistory;
import java.util.Collection;
import java.util.List;

@Mapper
public interface CreditMapper {
    // One grouped query for a whole results page; order lines cannot multiply the counts.
    @Select("""
        <script>
        SELECT seller_id, COUNT(*) AS completed_orders,
               COUNT(seller_rating) AS review_count, COALESCE(SUM(seller_rating), 0) AS rating_sum
        FROM (
            SELECT o.id, o.seller_id, r.seller_rating
            FROM oms_order o LEFT JOIN review r ON r.order_id = o.id AND r.seller_id = o.seller_id
            WHERE o.status = 'COMPLETED' AND o.seller_id IN
            <foreach collection="sellerIds" item="id" open="(" separator="," close=")">#{id}</foreach>
        ) history GROUP BY seller_id
        </script>
        """)
    List<CreditHistory> selectHistory(@Param("sellerIds") Collection<Long> sellerIds);
}
