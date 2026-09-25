package sg.edu.nus.iss.c2csectrade.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ViewHistoryMapper {
    int record(@Param("userId") Long userId, @Param("productId") Long productId);

    /** Categories the user looked at most recently, most viewed first. */
    List<String> recentCategories(@Param("userId") Long userId, @Param("limit") int limit);

    List<Long> recentProductIds(@Param("userId") Long userId, @Param("limit") int limit);
}
