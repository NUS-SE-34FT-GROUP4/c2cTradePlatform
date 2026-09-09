package sg.edu.nus.iss.c2csectrade.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;
import java.time.Instant;
import java.util.Set;

@Data
public class User implements Serializable {
    private Long id;
    private String username;
    private String displayName;
    private String email;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String passwordHash;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String paymentPasswordHash;
    private String avatarUrl;
    private java.math.BigDecimal balance; // 用户余额
    private Instant createdAt;
    private Instant updatedAt;
    private Set<Role> roles;

    // 获取头像URL，如果为空则返回默认头像
    public String getAvatarUrl() {
        if (avatarUrl == null || avatarUrl.trim().isEmpty()) {
            String nameForAvatar = (displayName != null && !displayName.trim().isEmpty()) ? displayName : username;
            return "https://ui-avatars.com/api/?name=" + nameForAvatar + "&background=007bff&color=fff&size=100";
        }
        return avatarUrl;
    }

    // 获取显示名称，如果为空则返回用户名
    public String getDisplayName() {
        return (displayName != null && !displayName.trim().isEmpty()) ? displayName : username;
    }
}
