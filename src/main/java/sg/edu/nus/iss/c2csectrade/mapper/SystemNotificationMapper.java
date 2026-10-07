package sg.edu.nus.iss.c2csectrade.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import sg.edu.nus.iss.c2csectrade.entity.SystemNotification;

import java.util.List;

@Mapper
public interface SystemNotificationMapper {
    int insert(SystemNotification notification);

    /** Newest first, capped so the list stays small. */
    List<SystemNotification> selectForUser(@Param("userId") Long userId, @Param("limit") int limit);

    int markRead(@Param("userId") Long userId, @Param("ids") List<Long> ids);
}
