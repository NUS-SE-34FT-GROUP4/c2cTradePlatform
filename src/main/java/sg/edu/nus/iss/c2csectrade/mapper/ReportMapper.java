package sg.edu.nus.iss.c2csectrade.mapper;

import org.apache.ibatis.annotations.*;
import sg.edu.nus.iss.c2csectrade.entity.Report;
import java.util.List;

@Mapper
public interface ReportMapper {
    @Insert("INSERT INTO report (reporter_id, target_type, target_id, reason, detail, status) " +
            "VALUES (#{reporterId}, #{targetType}, #{targetId}, #{reason}, #{detail}, 'OPEN')")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Report report);

    @Select("SELECT * FROM report WHERE id=#{id}")
    Report selectById(Long id);

    /** Locks the report so two administrators cannot decide it at the same time. */
    @Select("SELECT * FROM report WHERE id=#{id} FOR UPDATE")
    Report selectByIdForUpdate(Long id);

    @Select("SELECT COUNT(*) FROM report WHERE reporter_id=#{reporterId} AND target_type=#{targetType} " +
            "AND target_id=#{targetId} AND status='OPEN'")
    int countOpenByReporterAndTarget(@Param("reporterId") Long reporterId, @Param("targetType") String targetType,
                                     @Param("targetId") Long targetId);

    @Select("SELECT * FROM report WHERE reporter_id=#{reporterId} ORDER BY created_at DESC, id DESC " +
            "LIMIT #{limit} OFFSET #{offset}")
    List<Report> selectByReporter(@Param("reporterId") Long reporterId, @Param("limit") int limit,
                                  @Param("offset") long offset);

    @Select("SELECT COUNT(*) FROM report WHERE reporter_id=#{reporterId}")
    long countByReporter(Long reporterId);

    /** OPEN is a queue, so oldest first; decided reports read newest first. */
    @Select("<script>SELECT * FROM report WHERE status=#{status}" +
            "<if test='targetType != null'> AND target_type=#{targetType}</if>" +
            "<choose><when test=\"status == 'OPEN'\"> ORDER BY created_at, id</when>" +
            "<otherwise> ORDER BY decided_at DESC, id DESC</otherwise></choose>" +
            " LIMIT #{limit} OFFSET #{offset}</script>")
    List<Report> selectForQueue(@Param("status") String status, @Param("targetType") String targetType,
                                @Param("limit") int limit, @Param("offset") long offset);

    @Select("<script>SELECT COUNT(*) FROM report WHERE status=#{status}" +
            "<if test='targetType != null'> AND target_type=#{targetType}</if></script>")
    long countForQueue(@Param("status") String status, @Param("targetType") String targetType);

    @Select("SELECT COUNT(*) FROM report WHERE target_type='USER' AND target_id=#{userId} AND status='OPEN'")
    int countOpenAgainstUser(Long userId);

    @Update("UPDATE report SET status=#{status}, decided_by=#{decidedBy}, decision_note=#{note}, decided_at=NOW() " +
            "WHERE id=#{id} AND status='OPEN'")
    int decide(@Param("id") Long id, @Param("status") String status, @Param("decidedBy") Long decidedBy,
               @Param("note") String note);

    /** Closes the other open reports on a target that has just been dealt with. */
    @Update("UPDATE report SET status=#{status}, decided_by=#{decidedBy}, decision_note=#{note}, decided_at=NOW() " +
            "WHERE target_type=#{targetType} AND target_id=#{targetId} AND status='OPEN'")
    int decideAllOpenOnTarget(@Param("targetType") String targetType, @Param("targetId") Long targetId,
                              @Param("status") String status, @Param("decidedBy") Long decidedBy,
                              @Param("note") String note);
}
