package sg.edu.nus.iss.c2csectrade.mapper;

import org.apache.ibatis.annotations.*;
import sg.edu.nus.iss.c2csectrade.entity.AdminAuditLog;
import java.util.List;

@Mapper
public interface AdminAuditLogMapper {
    @Insert("INSERT INTO admin_audit_log (admin_id, action, target_type, target_id, report_id, note) " +
            "VALUES (#{adminId}, #{action}, #{targetType}, #{targetId}, #{reportId}, #{note})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AdminAuditLog entry);

    @Select("<script>SELECT * FROM admin_audit_log<if test='action != null'> WHERE action=#{action}</if>" +
            " ORDER BY created_at DESC, id DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<AdminAuditLog> select(@Param("action") String action, @Param("limit") int limit, @Param("offset") long offset);

    @Select("<script>SELECT COUNT(*) FROM admin_audit_log<if test='action != null'> WHERE action=#{action}</if></script>")
    long count(@Param("action") String action);
}
