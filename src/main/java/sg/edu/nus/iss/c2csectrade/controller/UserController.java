package sg.edu.nus.iss.c2csectrade.controller;

import sg.edu.nus.iss.c2csectrade.entity.User;
import sg.edu.nus.iss.c2csectrade.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserMapper userMapper;

    /**
     * 根据用户名获取用户信息（仍返回完整信息，如需可改为 DTO）
     */
    @GetMapping("/{username}")
    public ResponseEntity<User> getUserByUsername(@PathVariable String username) {
        User user = userMapper.selectByUsername(username);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(user);
    }


    /**
     * 修改用户显示名称
     */
    @PutMapping("/display-name")
    public ResponseEntity<?> updateDisplayName(@RequestBody Map<String, String> request, Authentication authentication) {
        try {
            String username = authentication.getName();
            String newDisplayName = request.get("displayName");

            if (newDisplayName == null || newDisplayName.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "显示名称不能为空"));
            }

            newDisplayName = newDisplayName.trim();

            // 验证长度
            if (newDisplayName.length() < 2 || newDisplayName.length() > 20) {
                return ResponseEntity.badRequest().body(Map.of("message", "用户名长度必须在2-20个字符之间"));
            }

            // 敏感词检查
            if (containsSensitiveWords(newDisplayName)) {
                return ResponseEntity.badRequest().body(Map.of("message", "用户名包含敏感词汇，请重新输入"));
            }

            // 检查是否与其他用户重复
            if (isDisplayNameExists(newDisplayName, username)) {
                return ResponseEntity.badRequest().body(Map.of("message", "该用户名已被使用，请选择其他名称"));
            }

            User user = userMapper.selectByUsername(username);
            if (user == null) {
                return ResponseEntity.status(404).body(Map.of("message", "用户不存在"));
            }
            user.setUpdatedAt(java.time.Instant.now());

            // 更新用户显示名称
            user.setDisplayName(newDisplayName);
            userMapper.update(user);

            Map<String, String> response = new HashMap<>();
            response.put("message", "用户名修改成功");
            response.put("displayName", newDisplayName);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("message", "修改失败: " + e.getMessage()));
        }
    }

    /**
     * 检查敏感词
     */
    private boolean containsSensitiveWords(String name) {
        String[] sensitiveWords = {
            "管理员", "admin", "系统", "system", "root", "超级", "客服",
            "官方", "垃圾", "傻逼", "操你", "妈的", "死", "杀", "色情",
            "政治"
        };

        String lowerName = name.toLowerCase();
        for (String word : sensitiveWords) {
            if (lowerName.contains(word.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查显示名称是否已存在（排除当前用户）
     */
    private boolean isDisplayNameExists(String displayName, String currentUsername) {
        List<User> allUsers = userMapper.selectAll();
        return allUsers.stream()
                .filter(user -> !user.getUsername().equals(currentUsername))
                .anyMatch(user -> displayName.equals(user.getDisplayName()));
    }

    /**
     * 获取当前用户信息（包括余额）
     */
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(Authentication authentication) {
        try {
            String username = authentication.getName();
            User user = userMapper.selectByUsername(username);

            if (user == null) {
                return ResponseEntity.status(404).body(Map.of("message", "用户不存在"));
            }

            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("id", user.getId());
            userInfo.put("username", user.getUsername());
            userInfo.put("displayName", user.getDisplayName());
            userInfo.put("email", user.getEmail());
            userInfo.put("avatarUrl", user.getAvatarUrl());
            userInfo.put("balance", user.getBalance() != null ? user.getBalance() : java.math.BigDecimal.ZERO);

            return ResponseEntity.ok(userInfo);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("message", "获取用户信息失败: " + e.getMessage()));
        }
    }

}
