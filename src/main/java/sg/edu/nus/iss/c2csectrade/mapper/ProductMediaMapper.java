package sg.edu.nus.iss.c2csectrade.mapper;

import sg.edu.nus.iss.c2csectrade.entity.ProductMedia;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ProductMediaMapper {
    ProductMedia selectById(@Param("id") Long id);
    List<ProductMedia> selectByProductId(@Param("productId") Long productId);
    int insert(ProductMedia media);
    int update(ProductMedia media);
    int deleteById(@Param("id") Long id);
}

