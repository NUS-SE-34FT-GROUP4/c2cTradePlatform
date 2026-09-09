package sg.edu.nus.iss.c2csectrade.mapper;

import sg.edu.nus.iss.c2csectrade.entity.Role;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface RoleMapper {
    Role selectById(@Param("id") Integer id);
    Role selectByName(@Param("name") String name);
    List<Role> selectAll();
    int insert(Role role);
    int update(Role role);
    int deleteById(@Param("id") Integer id);
}

