package sg.edu.nus.iss.c2csectrade.mapper;

import sg.edu.nus.iss.c2csectrade.entity.ProductMedia;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ProductMediaMapper {
    ProductMedia selectById(@Param("id") Long id);
    List<ProductMedia> selectByProductId(@Param("productId") Long productId);
    /** Media of several listings in one query; callers group by product and never pass an empty list. */
    List<ProductMedia> selectByProductIds(@Param("productIds") java.util.Collection<Long> productIds);
    int insert(ProductMedia media);
    int update(ProductMedia media);
    int deleteById(@Param("id") Long id);
    /** Media is replaced wholesale on edit so the seller's ordering is authoritative. */
    int deleteByProductId(@Param("productId") Long productId);
}

