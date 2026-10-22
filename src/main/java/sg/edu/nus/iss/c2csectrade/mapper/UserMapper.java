package sg.edu.nus.iss.c2csectrade.mapper;

import sg.edu.nus.iss.c2csectrade.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Mapper
public interface UserMapper {
    User selectById(@Param("id") Long id);
    User selectByUsername(@Param("username") String username);
    // 加载包含角色的用户
    User selectByUsernameWithRoles(@Param("username") String username);
    // 加载所有用户（包含角色）
    List<User> selectAllWithRoles();
    /** Several users in one query, for list views; callers never pass an empty list. */
    List<User> selectByIds(@Param("ids") java.util.Collection<Long> ids);
    List<User> selectAll();
    int insert(User user);
    int update(User user);
    int updatePaymentPasswordHash(@Param("id") Long id, @Param("hash") String hash);
    /** Locks the row until the surrounding transaction ends; only call inside one. */
    User selectByIdForUpdate(@Param("id") Long id);
    int updateBalance(@Param("id") Long id, @Param("balance") BigDecimal balance);
    int deleteById(@Param("id") Long id);

    int updateBan(@Param("id") Long id, @Param("status") String status,
                  @Param("banReason") String banReason, @Param("bannedAt") Instant bannedAt);

    List<User> searchForAdmin(@Param("status") String status, @Param("q") String q,
                              @Param("limit") int limit, @Param("offset") long offset);

    long countForAdmin(@Param("status") String status, @Param("q") String q);
}
