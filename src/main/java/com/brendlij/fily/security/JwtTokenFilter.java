package com.brendlij.fily.security;

import com.brendlij.fily.model.User;
import com.brendlij.fily.service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class JwtTokenFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtTokenFilter.class);

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserService userService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        logger.debug("Authorization header: {}", header);

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            logger.debug("Extracted JWT token: {}", token);

            String username = jwtUtil.validateTokenAndGetUsername(token);
            if (username != null) {
                logger.debug("Token valid for user: {}", username);
                User user = userService.findByUsername(username).orElse(null);
                if (user != null) {
                    Boolean isAdmin = jwtUtil.getIsAdmin(token);
                    logger.debug("User found: {} with isAdmin={}", username, isAdmin);
                    user.setAdmin(isAdmin); // falls nicht persistent nötig

                    List<GrantedAuthority> authorities = new ArrayList<>();
                    if (isAdmin != null && isAdmin) {
                        authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
                        logger.debug("Granted ROLE_ADMIN authority to user {}", username);
                    }

                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(user, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    request.setAttribute("user", user);
                } else {
                    logger.warn("User not found in database: {}", username);
                }
            } else {
                logger.warn("Invalid JWT token");
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid JWT token");
                return;
            }
        } else {
            logger.debug("No Bearer token found in Authorization header");
        }

        filterChain.doFilter(request, response);
    }
}
