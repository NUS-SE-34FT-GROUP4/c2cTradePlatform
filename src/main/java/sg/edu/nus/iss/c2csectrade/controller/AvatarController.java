package sg.edu.nus.iss.c2csectrade.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import sg.edu.nus.iss.c2csectrade.entity.User;
import sg.edu.nus.iss.c2csectrade.mapper.UserMapper;
import sg.edu.nus.iss.c2csectrade.service.FileStorageService;

import java.util.Map;

/**
 * Avatar management. Every endpoint acts on the caller's own account only —
 * the user id comes from the authentication, never from the request body.
 */
@RestController
@RequestMapping("/api/users/avatar")
public class AvatarController {

    private static final String DEFAULT_AVATAR = "https://i.pravatar.cc/150?u=";

    private final FileStorageService fileStorageService;
    private final UserMapper userMapper;

    public AvatarController(FileStorageService fileStorageService, UserMapper userMapper) {
        this.fileStorageService = fileStorageService;
        this.userMapper = userMapper;
    }

    @PostMapping("/upload")
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file, Authentication authentication) {
        User user = currentUser(authentication);
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("message", "Not authenticated"));
        }
        try {
            String url = fileStorageService.upload(file, "avatar");
            return ResponseEntity.ok(save(user, url));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("message", "Upload failed: " + e.getMessage()));
        }
    }

    /** For picking an avatar that already lives somewhere, instead of uploading bytes. */
    @PostMapping("/url")
    public ResponseEntity<?> setUrl(@RequestBody Map<String, String> body, Authentication authentication) {
        User user = currentUser(authentication);
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("message", "Not authenticated"));
        }
        String url = body.get("avatarUrl");
        if (url == null || url.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "avatarUrl is required"));
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return ResponseEntity.badRequest().body(Map.of("message", "avatarUrl must be an http or https URL"));
        }
        return ResponseEntity.ok(save(user, url));
    }

    @PostMapping("/reset")
    public ResponseEntity<?> reset(Authentication authentication) {
        User user = currentUser(authentication);
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("message", "Not authenticated"));
        }
        return ResponseEntity.ok(save(user, DEFAULT_AVATAR + user.getUsername()));
    }

    private Map<String, String> save(User user, String url) {
        user.setAvatarUrl(url);
        userMapper.update(user);
        return Map.of("avatarUrl", url);
    }

    private User currentUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        return userMapper.selectByUsername(authentication.getName());
    }
}
