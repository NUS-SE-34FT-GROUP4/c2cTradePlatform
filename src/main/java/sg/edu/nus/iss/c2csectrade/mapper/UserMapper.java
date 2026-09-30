package sg.edu.nus.iss.c2csectrade.mapper;

import sg.edu.nus.iss.c2csectrade.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface UserMapper {
    User selectById(@Param("id") Long id);
    User selectByUsername(@Param("username") String username);
    // 加载包含角色的用户
    User selectByUsernameWithRoles(@Param("username") String username);
    // 加载所有用户（包含角色）
    List<User> selectAllWithRoles();
    List<User> selectAll();
    int insert(User user);
    int update(User user);
    int updatePaymentPasswordHash(@Param("id") Long id, @Param("hash") String hash);
    /** @return 0 when the balance is lower than amount; nothing is deducted then */
    int debitBalance(@Param("id") Long id, @Param("amount") BigDecimal amount);
    int creditBalance(@Param("id") Long id, @Param("amount") BigDecimal amount);
    int deleteById(@Param("id") Long id);
}
