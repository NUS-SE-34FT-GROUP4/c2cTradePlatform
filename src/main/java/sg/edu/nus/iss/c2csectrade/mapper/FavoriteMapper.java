package sg.edu.nus.iss.c2csectrade.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import sg.edu.nus.iss.c2csectrade.entity.Favorite;

import java.util.List;

@Mapper
public interface FavoriteMapper {
    List<Favorite> selectByUserId(@Param("userId") Long userId);
    Favorite selectByUserAndProduct(@Param("userId") Long userId, @Param("productId") Long productId);
    int countByUserId(@Param("userId") Long userId);
    int insert(Favorite favorite);
    int delete(@Param("userId") Long userId, @Param("productId") Long productId);
}
