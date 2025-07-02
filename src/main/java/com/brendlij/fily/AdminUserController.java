package com.brendlij.fily;

import com.brendlij.fily.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/users")
@CrossOrigin
public class AdminUserController {

    private static final Logger logger = LoggerFactory.getLogger(AdminUserController.class);

    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    // User erstellen
    @PostMapping
    public ResponseEntity<?> createUser(@RequestBody Map<String, Object> body) {
        String username = (String) body.get("username");
        String password = (String) body.get("password");
        boolean isAdmin = body.get("isAdmin") != null && (Boolean) body.get("isAdmin");

        if (username == null || password == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Fehlende Daten!"));
        }

        boolean created = userService.createUser(username, password, isAdmin);
        if (created) return ResponseEntity.ok(Map.of("message", "User erstellt!"));
        else return ResponseEntity.status(400).body(Map.of("message", "User existiert bereits!"));
    }

    // Alle User holen (ohne Passwörter)
    @GetMapping
    public List<Map<String, Object>> listUsers() {
        logger.info("AdminUserController: listUsers called");
        return userService.findAllUsers().stream()
                .map(user -> Map.<String, Object>of(
                        "id", user.getId(),
                        "username", user.getUsername(),
                        "isAdmin", user.isAdmin()
                ))
                .toList();
    }

    // User löschen
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        logger.info("AdminUserController: deleteUser called for id {}", id);
        boolean deleted = userService.deleteUserById(id);
        if (deleted) {
            logger.info("User with id {} deleted", id);
            return ResponseEntity.ok(Map.of("message", "User gelöscht"));
        } else {
            logger.warn("User with id {} not found for deletion", id);
            return ResponseEntity.status(404).body(Map.of("message", "User nicht gefunden"));
        }
    }

    // Passwort ändern
    @PutMapping("/{id}/password")
    public ResponseEntity<?> changePassword(@PathVariable Long id, @RequestBody Map<String, String> body) {
        logger.info("AdminUserController: changePassword called for id {}", id);
        String newPassword = body.get("password");
        if (newPassword == null || newPassword.isEmpty()) {
            logger.warn("Password change failed: no password provided");
            return ResponseEntity.badRequest().body(Map.of("message", "Passwort fehlt"));
        }
        boolean changed = userService.changePassword(id, newPassword);
        if (changed) {
            logger.info("Password changed for user with id {}", id);
            return ResponseEntity.ok(Map.of("message", "Passwort geändert"));
        } else {
            logger.warn("User with id {} not found for password change", id);
            return ResponseEntity.status(404).body(Map.of("message", "User nicht gefunden"));
        }
    }

    @PutMapping("/{id}/role")
    public ResponseEntity<?> updateUserRole(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        Boolean isAdmin = body.get("isAdmin");
        if (isAdmin == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "isAdmin fehlt"));
        }
        boolean updated = userService.updateUserAdminRole(id, isAdmin);
        if (updated) {
            return ResponseEntity.ok(Map.of("message", "User-Rolle aktualisiert"));
        } else {
            return ResponseEntity.status(404).body(Map.of("message", "User nicht gefunden"));
        }
    }

}
