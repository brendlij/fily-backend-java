package com.brendlij.fily;

import com.brendlij.fily.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/public")
@CrossOrigin
public class PublicController {

    private static final Logger logger = LoggerFactory.getLogger(PublicController.class);

    private final UserService userService;

    public PublicController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/users-exist")
    public boolean usersExist() {
        boolean exist = !userService.findAllUsers().isEmpty();
        logger.info("usersExist called, result: {}", exist);
        return exist;
    }
}
