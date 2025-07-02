package com.brendlij.fily.service;

import com.brendlij.fily.model.User;
import com.brendlij.fily.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Value("${fileserver.basedir}")
    private String baseDir;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public boolean createUser(String username, String password, boolean isAdmin) {
        if (userRepository.findByUsername(username).isPresent()) {
            logger.info("Create user failed: User '{}' already exists", username);
            return false; // User already exists
        }
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setAdmin(isAdmin);
        userRepository.save(user);
        logger.info("User '{}' created with admin={}", username, isAdmin);

        // Create user folder
        File userFolder = new File(baseDir, username);
        if (!userFolder.exists()) {
            if (userFolder.mkdirs()) {
                logger.info("User folder created at {}", userFolder.getAbsolutePath());
            } else {
                logger.warn("Failed to create user folder at {}", userFolder.getAbsolutePath());
            }
        }
        return true;
    }

    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public boolean authenticate(String username, String password) {
        return userRepository.findByUsername(username)
                .map(user -> passwordEncoder.matches(password, user.getPasswordHash()))
                .orElse(false);
    }

    public List<User> findAllUsers() {
        return userRepository.findAll();
    }

    public boolean deleteUserById(Long id) {
        if (userRepository.existsById(id)) {
            userRepository.deleteById(id);
            logger.info("User with id {} deleted", id);
            return true;
        }
        logger.warn("Delete user failed: User with id {} does not exist", id);
        return false;
    }

    public boolean changePassword(Long id, String newPassword) {
        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            user.setPasswordHash(passwordEncoder.encode(newPassword));
            userRepository.save(user);
            logger.info("Password changed for user with id {}", id);
            return true;
        }
        logger.warn("Change password failed: User with id {} not found", id);
        return false;
    }

    public boolean updateUserAdminRole(Long id, boolean isAdmin) {
        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            user.setAdmin(isAdmin);
            userRepository.save(user);
            return true;
        }
        return false;
    }
}
