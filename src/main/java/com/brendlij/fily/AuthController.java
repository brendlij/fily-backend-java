package com.brendlij.fily;

import com.brendlij.fily.security.JwtUtil;
import com.brendlij.fily.service.UserService;
import com.brendlij.fily.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    private final UserService userService;
    private final JwtUtil jwtUtil;

    public AuthController(UserService userService, JwtUtil jwtUtil) {
        this.userService = userService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, Object> body) {
        String username = (String) body.get("username");
        String password = (String) body.get("password");
        boolean isAdmin = body.get("isAdmin") != null && (Boolean) body.get("isAdmin");

        logger.info("Register attempt for username: {}", username);

        if (username == null || password == null) {
            logger.warn("Registration failed: missing data");
            return ResponseEntity.badRequest().body(Map.of("message", "Fehlende Daten!"));
        }

        boolean created = userService.createUser(username, password, isAdmin);
        if (created) {
            logger.info("User '{}' created successfully, isAdmin={}", username, isAdmin);
            return ResponseEntity.ok(Map.of("message", "User erstellt!"));
        } else {
            logger.warn("Registration failed: user '{}' already exists", username);
            return ResponseEntity.status(400).body(Map.of("message", "User existiert bereits!"));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");

        logger.info("Login attempt for username: {}", username);

        var userOpt = userService.findByUsername(username);
        if (userOpt.isPresent() && userService.authenticate(username, password)) {
            User user = userOpt.get();
            String token = jwtUtil.generateToken(user.getUsername(), user.isAdmin());
            Map<String, Object> resp = new HashMap<>();
            resp.put("token", token);
            resp.put("isAdmin", user.isAdmin());

            logger.info("User '{}' logged in successfully", username);
            return ResponseEntity.ok(resp);
        }

        logger.warn("Login failed for username: {}", username);
        return ResponseEntity.status(401).body(Map.of("message", "Login fehlgeschlagen!"));
    }

}
