package com.brendlij.fily.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.security.Key;

@Component
public class JwtUtil {

    private static final Logger logger = LoggerFactory.getLogger(JwtUtil.class);

    private final Key key = Keys.secretKeyFor(SignatureAlgorithm.HS256);
    private final long expirationMillis = 86400000; // 24h

    public String generateToken(String username, boolean isAdmin) {
        logger.debug("Generating token for user '{}', isAdmin={}", username, isAdmin);
        String token = Jwts.builder()
                .setSubject(username)
                .claim("isAdmin", isAdmin)
                .setExpiration(new Date(System.currentTimeMillis() + expirationMillis))
                .signWith(key)
                .compact();
        logger.debug("Generated token: {}", token);
        return token;
    }

    public Boolean getIsAdmin(String token) {
        try {
            Boolean isAdmin = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody()
                    .get("isAdmin", Boolean.class);
            logger.debug("Extracted isAdmin={} from token", isAdmin);
            return isAdmin;
        } catch (JwtException e) {
            logger.warn("Failed to parse isAdmin from token: {}", e.getMessage());
            return false;
        }
    }

    public String validateTokenAndGetUsername(String token) {
        try {
            String username = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody()
                    .getSubject();
            logger.debug("Validated token for user '{}'", username);
            return username;
        } catch (JwtException e) {
            logger.warn("Token validation failed: {}", e.getMessage());
            return null; // Invalid
        }
    }
}
